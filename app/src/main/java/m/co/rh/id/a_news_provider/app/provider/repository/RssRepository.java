package m.co.rh.id.a_news_provider.app.provider.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

/**
 * Repository for handling RSS feed persistence logic.
 * Moved from RssRequest to separate network layer from app layer.
 * Preserves read and favorite item state across feed syncs and retains items
 * that fall out of a feed's current list instead of deleting them (issue #37).
 */
public class RssRepository {
    private final RssDao mRssDao;
    private final UrlNormalizer mUrlNormalizer;

    public RssRepository(Provider provider) {
        mRssDao = provider.get(RssDao.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
    }

    /**
     * Persists a parsed RSS feed model to the database, handling merge logic for existing channels.
     * Existing channels are upserted item-by-item inside a single DAO transaction: items are
     * matched by link (or by a title/pub-date tuple for linkless items), matched rows keep their
     * id and read/favorite state while their feed fields are refreshed, and items that are no
     * longer part of the parsed feed are retained (never deleted). The DAO transaction is the
     * single source of truth for the state merge; see
     * {@link RssDao#upsertRssItemsForChannel(RssChannel, List)} for the full retention semantics.
     * This method must be called on a background thread.
     *
     * @param parsed the parsed RSS feed model from the network response
     * @return the persisted model with database ID populated, containing the merged item list
     * (persisted parsed items followed by the retained items the feed no longer carries)
     */
    public RssModel persist(RssModel parsed) {
        RssChannel responseRssChannel = parsed.getRssChannel();
        String originalUrl = responseRssChannel.url;
        String normalizedUrl = mUrlNormalizer.normalizeUrl(originalUrl);
        // Match both the raw and normalized URL variants so legacy un-normalized rows are found
        RssChannel rssChannel = mRssDao.findRssChannelByUrlVariants(originalUrl, normalizedUrl);
        responseRssChannel.url = normalizedUrl;
        if (rssChannel == null) {
            // New channel - insert directly
            mRssDao.insertRssChannel(parsed.getRssChannel(), parsed.getRssItems().toArray(new RssItem[0]));
            return parsed;
        } else {
            // Existing channel - preserve channel fields and upsert items atomically
            copyPreservedFields(rssChannel, responseRssChannel);
            List<RssItem> persistedItems =
                    mRssDao.upsertRssItemsForChannel(responseRssChannel, parsed.getRssItems());
            return new RssModel(responseRssChannel, new ArrayList<>(persistedItems));
        }
    }

    /**
     * Copies preserved fields from existing DB channel to parsed channel.
     * Preserves: id, feedName, createdDateTime, updatedDateTime
     */
    private void copyPreservedFields(RssChannel fromDb, RssChannel toUpdate) {
        toUpdate.id = fromDb.id;
        toUpdate.feedName = fromDb.feedName;
        toUpdate.createdDateTime = fromDb.createdDateTime;
        toUpdate.updatedDateTime = fromDb.updatedDateTime;
    }

    /**
     * Updates the isRead status of an RSS item in the database.
     * This method must be called on a background thread.
     *
     * @param rssItem the RSS item to update
     */
    public void updateRssItemIsRead(RssItem rssItem) {
        mRssDao.updateRssItemsIsReadByLink(rssItem.isRead, rssItem.link);
    }

    /**
     * Marks unread RSS items as read when their link matches one of the given links,
     * keeping cross-channel duplicates (same link) consistent.
     * This method must be called on a background thread.
     *
     * @param links the links of the RSS items to mark as read
     * @return the number of rows marked as read
     */
    public int markItemsReadByLinks(List<String> links) {
        return mRssDao.markItemsReadByLinks(links);
    }

    /**
     * Auto mark-read (unread retention window): marks unread, non-favorite RSS items
     * older than the given cutoff as read, keeping cross-channel duplicates
     * (same link) consistent. Favorites are never touched.
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are affected
     * @return the number of rows marked as read
     */
    public int markOldItemsRead(long cutoffMillis) {
        return mRssDao.markOldItemsRead(cutoffMillis);
    }

    /**
     * Updates the isFavorite status of an RSS item in the database.
     * This method must be called on a background thread.
     *
     * @param rssItem the RSS item to update
     */
    public void updateRssItemIsFavorite(RssItem rssItem) {
        mRssDao.updateRssItemsIsFavoriteByLink(rssItem.isFavorite, rssItem.link);
    }

    /**
     * Finds all RSS items matching the given link, regardless of channel.
     * This method must be called on a background thread.
     *
     * @param link the link of the RSS items to find
     * @return list of RSS items matching the given link
     */
    public List<RssItem> findRssItemsByLink(String link) {
        return mRssDao.findRssItemsByLink(link);
    }

    /**
     * Finds the RSS channel with the given id. Backs the duplicate-story sources
     * expansion (issue #45), which maps the channel ids of an item's cross-feed
     * copies to displayable feed names.
     * This method must be called on a background thread.
     *
     * @param id the channel id to find
     * @return the channel, or null when no channel with this id exists
     */
    public RssChannel findRssChannelById(long id) {
        return mRssDao.findRssChannelById(id);
    }

    /**
     * Deletes an RSS channel from the database.
     * This method must be called on a background thread.
     *
     * @param rssChannel the RSS channel to delete
     */
    public void deleteRssChannel(RssChannel rssChannel) {
        mRssDao.deleteRssChannel(rssChannel);
    }

    /**
     * Retrieves a map of RSS channels to their unread item counts.
     * This method must be called on a background thread.
     *
     * @return a LinkedHashMap preserving channel order, with zero-filled counts for channels with no unread items
     */
    public Map<RssChannel, Integer> getChannelUnreadCountMap() {
        return buildUnreadCountMap(mRssDao.loadAllRssChannel(), mRssDao.countUnReadRssItemsByChannel());
    }

    /**
     * Package-visible helper for testing - builds a map of channels to their unread counts.
     * Merges the channel list with unread count data, preserving order and zero-filling missing counts.
     *
     * @param channels the list of RSS channels
     * @param unreadCounts the list of unread count data
     * @return a LinkedHashMap mapping channels to their unread counts
     */
    static Map<RssChannel, Integer> buildUnreadCountMap(List<RssChannel> channels, List<ChannelUnreadCount> unreadCounts) {
        Map<RssChannel, Integer> mapResult = new LinkedHashMap<>();
        
        if (channels == null || channels.isEmpty()) {
            return mapResult;
        }

        // Build a map of channel_id to count for quick lookup
        HashMap<Long, Integer> countMap = new HashMap<>();
        if (unreadCounts != null) {
            for (ChannelUnreadCount cuc : unreadCounts) {
                countMap.put(cuc.channel_id, cuc.cnt);
            }
        }

        // Merge channels with counts, preserving order
        for (RssChannel rssChannel : channels) {
            Integer count = countMap.get(rssChannel.id);
            mapResult.put(rssChannel, count != null ? count : 0);
        }

        return mapResult;
    }
}
