package m.co.rh.id.a_news_provider.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.Date;
import java.util.List;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;

@Dao
public abstract class RssDao {

    /**
     * Maximum number of links bound per UPDATE statement. SQLite older than 3.32
     * (Android 11 and below) allows at most 999 host parameters per query, and the
     * caller's page size starts at 1000 (see BaseRssItemsCmd.mLimit), so 500 keeps
     * every chunk safely within the limit while halving the number of statements.
     */
    private static final int MARK_READ_BY_LINKS_CHUNK_SIZE = 500;

    @Query("SELECT * FROM rss_channel ORDER BY feed_name")
    public abstract List<RssChannel> loadAllRssChannel();

    @Query("SELECT * FROM rss_channel WHERE id = :id")
    public abstract RssChannel findRssChannelById(long id);

    /**
     * Finds a channel matching either the raw or the normalized URL variant.
     * Ordered by id ASC so that, while duplicate channels from before URL
     * normalization still exist, the oldest row (lowest id) deterministically wins.
     * This method must be called on a background thread.
     *
     * @param url           the raw URL to match
     * @param normalizedUrl the normalized URL variant to match
     * @return the first matching channel, or null when none exists
     */
    @Query("SELECT * FROM rss_channel WHERE url = :url OR url = :normalizedUrl ORDER BY id ASC LIMIT 1")
    public abstract RssChannel findRssChannelByUrlVariants(String url, String normalizedUrl);

    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId")
    public abstract List<RssItem> findRssItemsByChannelId(long channelId);

    @Query("SELECT COUNT(id) FROM rss_item")
    public abstract int countRssItem();

    @Query("SELECT COUNT(id) FROM rss_item WHERE is_read = 0 AND channel_id = :channelId")
    public abstract int countUnReadRssItems(long channelId);

    @Transaction
    public void insertRssChannel(RssChannel rssChannel, RssItem... rssItems) {
        if (rssChannel.createdDateTime == null) {
            Date date = new Date();
            rssChannel.createdDateTime = date;
            rssChannel.updatedDateTime = date;
        }
        long channelId = insert(rssChannel);
        rssChannel.id = channelId;
        if (rssItems != null && rssItems.length > 0) {
            for (RssItem rssItem : rssItems) {
                rssItem.channelId = channelId;
            }
            insertRssItem(rssItems);
        }
    }

    @Transaction
    public void insertRssItem(RssItem... rssItems) {
        for (RssItem rssItem : rssItems) {
            if (rssItem.createdDateTime == null) {
                Date date = new Date();
                rssItem.createdDateTime = date;
                rssItem.updatedDateTime = date;
            }
            rssItem.id = insert(rssItem);
        }
    }

    @Transaction
    public void updateRssItem(RssItem rssItem) {
        rssItem.updatedDateTime = new Date();
        update(rssItem);
    }

    @Transaction
    public void updateRssChannel(RssChannel rssChannel, RssItem... rssItems) {
        rssChannel.updatedDateTime = new Date();
        update(rssChannel);
        if (rssItems != null) {
            // delete previous items
            deleteRssItemsByChannelId(rssChannel.id);
            for (RssItem rssItem : rssItems) {
                rssItem.channelId = rssChannel.id;
            }
            insertRssItem(rssItems);
        }
    }

    @Insert
    protected abstract long insert(RssChannel rssChannel);

    @Insert
    protected abstract long insert(RssItem rssItem);

    @Update
    public abstract void update(RssChannel rssChannel);

    @Update
    protected abstract void update(RssItem rssItem);

    @Delete
    protected abstract void delete(RssChannel rssChannel);

    @Query("DELETE FROM rss_item WHERE channel_id = :rssChannelId")
    public abstract void deleteRssItemsByChannelId(long rssChannelId);

    @Transaction
    public void deleteRssChannel(RssChannel rssChannel) {
        delete(rssChannel);
        deleteRssItemsByChannelId(rssChannel.id);
    }

    @Query("SELECT * FROM rss_item WHERE id = :rssItemId")
    public abstract RssItem findRssItemById(long rssItemId);

    @Query("UPDATE rss_item SET is_read = :isRead WHERE link = :link")
    public abstract void updateRssItemsIsReadByLink(boolean isRead, String link);

    @Query("SELECT channel_id, COUNT(id) as cnt FROM rss_item WHERE is_read = 0 GROUP BY channel_id")
    public abstract List<ChannelUnreadCount> countUnReadRssItemsByChannel();

    /**
     * Finds rss items filtered by optional channel, read and favorite state,
     * ordered by newest first and limited to the given count.
     * This method must be called on a background thread.
     *
     * @param channelId   optional channel id filter, null to include all channels
     * @param isRead      optional read-state filter, null to include read and unread items
     * @param isFavorite  optional favorite filter, null to include favorite and non-favorite items
     * @param limit       maximum number of items to return
     * @return list of rss items matching the filters, newest first
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) ORDER BY COALESCE(pub_date, created_date_time) DESC, created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsWithLimit(Long channelId, Integer isRead, Integer isFavorite, int limit);

    /**
     * Finds rss items filtered by optional channel, read and favorite state,
     * ordered by oldest first and limited to the given count.
     * This method must be called on a background thread.
     *
     * @param channelId   optional channel id filter, null to include all channels
     * @param isRead      optional read-state filter, null to include read and unread items
     * @param isFavorite  optional favorite filter, null to include favorite and non-favorite items
     * @param limit       maximum number of items to return
     * @return list of rss items matching the filters, oldest first
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) ORDER BY COALESCE(pub_date, created_date_time) ASC, created_date_time ASC LIMIT :limit")
    public abstract List<RssItem> findRssItemsWithLimitAsc(Long channelId, Integer isRead, Integer isFavorite, int limit);

    /**
     * Searches rss items matching the given query against title and description,
     * filtered by optional channel, read and favorite state,
     * ordered by newest first and limited to the given count.
     * The query must be pre-escaped for LIKE wildcards by the caller.
     * This method must be called on a background thread.
     *
     * @param query       pre-escaped search query matched against title and description
     * @param channelId   optional channel id filter, null to include all channels
     * @param isRead      optional read-state filter, null to include read and unread items
     * @param isFavorite  optional favorite filter, null to include favorite and non-favorite items
     * @param limit       maximum number of items to return
     * @return list of rss items matching the query, newest first
     */
    @Query("SELECT * FROM rss_item WHERE (title LIKE '%' || :query || '%' ESCAPE '\\' OR description LIKE '%' || :query || '%' ESCAPE '\\') AND (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) ORDER BY COALESCE(pub_date, created_date_time) DESC, created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> searchRssItemsWithLimit(String query, Long channelId, Integer isRead, Integer isFavorite, int limit);

    /**
     * Searches rss items matching the given query against title and description,
     * filtered by optional channel, read and favorite state,
     * ordered by oldest first and limited to the given count.
     * The query must be pre-escaped for LIKE wildcards by the caller.
     * This method must be called on a background thread.
     *
     * @param query       pre-escaped search query matched against title and description
     * @param channelId   optional channel id filter, null to include all channels
     * @param isRead      optional read-state filter, null to include read and unread items
     * @param isFavorite  optional favorite filter, null to include favorite and non-favorite items
     * @param limit       maximum number of items to return
     * @return list of rss items matching the query, oldest first
     */
    @Query("SELECT * FROM rss_item WHERE (title LIKE '%' || :query || '%' ESCAPE '\\' OR description LIKE '%' || :query || '%' ESCAPE '\\') AND (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) ORDER BY COALESCE(pub_date, created_date_time) ASC, created_date_time ASC LIMIT :limit")
    public abstract List<RssItem> searchRssItemsWithLimitAsc(String query, Long channelId, Integer isRead, Integer isFavorite, int limit);

    /**
     * Marks all rss items as read.
     * This method must be called on a background thread.
     */
    @Query("UPDATE rss_item SET is_read = 1 WHERE is_read = 0")
    public abstract void markAllRssItemsRead();

    /**
     * Marks all rss items of the given channel as read.
     * This method must be called on a background thread.
     *
     * @param channelId the channel id of the items to mark as read
     */
    @Query("UPDATE rss_item SET is_read = 1 WHERE channel_id = :channelId AND is_read = 0")
    public abstract void markRssItemsReadByChannelId(long channelId);

    /**
     * Marks unread rss items as read when their link matches one of the given links.
     * Matching is done by link (not id) so cross-channel duplicates stay consistent,
     * same as {@link #updateRssItemsIsReadByLink(boolean, String)} and
     * {@link #markOldItemsRead(long)}. Rows with a null or empty link can never match
     * and are explicitly excluded by the query.
     * This method must be called on a background thread.
     *
     * @param links the links of the rss items to mark as read
     * @return the number of rows marked as read for this chunk
     */
    @Query("UPDATE rss_item SET is_read = 1 " +
            "WHERE is_read = 0 AND link IS NOT NULL AND link != '' " +
            "AND link IN (:links)")
    protected abstract int markItemsReadByLinksChunk(List<String> links);

    /**
     * Marks unread rss items as read when their link matches one of the given links,
     * splitting the links into chunks of {@link #MARK_READ_BY_LINKS_CHUNK_SIZE}.
     * Chunking is required because SQLite older than 3.32 (Android 11 and below)
     * allows at most 999 host parameters per query while the caller's page size
     * starts at 1000 and doubles (BaseRssItemsCmd.mLimit). Since link is unindexed,
     * each UPDATE is a full table scan, so chunking also bounds the work per
     * statement. The {@code is_read = 0} guard makes repeated calls idempotent.
     * This method must be called on a background thread.
     *
     * @param links the links of the rss items to mark as read
     * @return the total number of rows marked as read
     */
    @Transaction
    public int markItemsReadByLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return 0;
        }
        int totalRows = 0;
        for (int i = 0; i < links.size(); i += MARK_READ_BY_LINKS_CHUNK_SIZE) {
            int end = Math.min(i + MARK_READ_BY_LINKS_CHUNK_SIZE, links.size());
            totalRows += markItemsReadByLinksChunk(links.subList(i, end));
        }
        return totalRows;
    }

    /**
     * Auto mark-read (unread retention window): marks unread, non-favorite rss items
     * as read when they are old. An item is old when
     * COALESCE(pub_date, created_date_time) is strictly before the given cutoff.
     * The update is link-consistent with {@link #updateRssItemsIsReadByLink(boolean, String)}:
     * every row whose link belongs to an old-dated row is marked too, so cross-channel
     * duplicates stay in sync. Rows with a null or empty link are explicitly excluded -
     * a null link can never match the IN subquery and all empty-link rows would
     * wrongly couple together.
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are affected
     * @return the number of rows marked as read
     */
    @Query("UPDATE rss_item SET is_read = 1 " +
            "WHERE is_read = 0 AND is_favorite = 0 " +
            "AND link IS NOT NULL AND link != '' " +
            "AND link IN (" +
            "SELECT link FROM rss_item " +
            "WHERE COALESCE(pub_date, created_date_time) < :cutoffMillis " +
            "AND link IS NOT NULL AND link != '')")
    public abstract int markOldItemsRead(long cutoffMillis);

    /**
     * Updates the favorite state of rss items matching the given link.
     * This method must be called on a background thread.
     *
     * @param isFavorite the favorite state to set
     * @param link       the link of the rss items to update
     */
    @Query("UPDATE rss_item SET is_favorite = :isFavorite WHERE link = :link")
    public abstract void updateRssItemsIsFavoriteByLink(boolean isFavorite, String link);

    /**
     * Updates the paused state of the given channel. A paused channel keeps its
     * history but is skipped by the sync worker (no sync, no notifications).
     * Its unread counts can still decrease via the optional auto mark-read
     * retention window, see {@link #markOldItemsRead(long)}.
     * This method must be called on a background thread.
     *
     * @param channelId the channel id to update
     * @param isPaused  the paused state to set
     */
    @Query("UPDATE rss_channel SET is_paused = :isPaused WHERE id = :channelId")
    public abstract void updateRssChannelIsPaused(long channelId, boolean isPaused);

    /**
     * Finds all rss items matching the given link, regardless of channel.
     * This method must be called on a background thread.
     *
     * @param link the link of the rss items to find
     * @return list of rss items matching the given link
     */
    @Query("SELECT * FROM rss_item WHERE link = :link")
    public abstract List<RssItem> findRssItemsByLink(String link);
}
