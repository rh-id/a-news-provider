package m.co.rh.id.a_news_provider.app.provider.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

/**
 * Repository for handling RSS feed persistence logic.
 * Moved from RssRequest to separate network layer from app layer.
 */
public class RssRepository {
    private final RssDao mRssDao;

    public RssRepository(Provider provider) {
        mRssDao = provider.get(RssDao.class);
    }

    /**
     * Persists a parsed RSS feed model to the database, handling merge logic for existing channels.
     * This method must be called on a background thread.
     *
     * @param parsed the parsed RSS feed model from the network response
     * @return the persisted model with database ID populated
     */
    public RssModel persist(RssModel parsed) {
        RssChannel rssChannel = mRssDao.findRssChannelByUrl(parsed.getRssChannel().url);
        if (rssChannel == null) {
            // New channel - insert directly
            mRssDao.insertRssChannel(parsed.getRssChannel(), parsed.getRssItems().toArray(new RssItem[0]));
            return parsed;
        } else {
            // Existing channel - preserve certain fields and merge read state
            RssChannel responseRssChannel = parsed.getRssChannel();
            copyPreservedFields(rssChannel, responseRssChannel);

            ArrayList<RssItem> rssItemsFromModel = parsed.getRssItems();
            List<RssItem> rssItemList = mRssDao.findRssItemsByChannelId(rssChannel.id);

            // Apply read state from DB to new items based on link matching
            applyReadState(rssItemList, rssItemsFromModel);

            RssModel result = new RssModel(responseRssChannel, rssItemsFromModel);
            mRssDao.updateRssChannel(responseRssChannel, result.getRssItems().toArray(new RssItem[0]));
            return result;
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
     * Package-visible helper for testing - applies read state from DB items to parsed items
     * by matching links. If an item with the same link exists in the DB, its isRead value
     * is carried over to the parsed item.
     *
     * @param dbItems items from the database with read state
     * @param parsedItems newly parsed items to update with read state
     */
    static void applyReadState(List<RssItem> dbItems, ArrayList<RssItem> parsedItems) {
        if (dbItems == null || dbItems.isEmpty() || parsedItems == null || parsedItems.isEmpty()) {
            return;
        }

        HashMap<String, Boolean> linkReadMap = new HashMap<>();
        for (RssItem rssItem : dbItems) {
            if (rssItem.link != null && !rssItem.link.isEmpty()) {
                linkReadMap.put(rssItem.link, rssItem.isRead);
            }
        }

        for (RssItem rssItemFromModel : parsedItems) {
            if (rssItemFromModel.link != null && !rssItemFromModel.link.isEmpty()) {
                Boolean isRead = linkReadMap.get(rssItemFromModel.link);
                if (isRead != null) {
                    rssItemFromModel.isRead = isRead;
                }
            }
        }
    }
}
