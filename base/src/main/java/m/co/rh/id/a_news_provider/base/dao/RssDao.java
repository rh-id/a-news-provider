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

    @Query("SELECT * FROM rss_channel ORDER BY feed_name")
    public abstract List<RssChannel> loadAllRssChannel();

    @Query("SELECT * FROM rss_channel WHERE id = :id")
    public abstract RssChannel findRssChannelById(long id);

    @Query("SELECT * FROM rss_channel WHERE url = :url")
    public abstract RssChannel findRssChannelByUrl(String url);

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
     * Updates the favorite state of rss items matching the given link.
     * This method must be called on a background thread.
     *
     * @param isFavorite the favorite state to set
     * @param link       the link of the rss items to update
     */
    @Query("UPDATE rss_item SET is_favorite = :isFavorite WHERE link = :link")
    public abstract void updateRssItemsIsFavoriteByLink(boolean isFavorite, String link);

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
