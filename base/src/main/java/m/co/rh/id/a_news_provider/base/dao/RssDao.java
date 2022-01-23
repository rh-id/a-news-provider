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

    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId AND is_read = :isRead ORDER BY pub_date DESC,created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsByChannelIdAndIsReadWithLimit(long channelId, int isRead, int limit);

    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId ORDER BY pub_date DESC,created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsByChannelIdWithLimit(long channelId, int limit);

    @Query("SELECT * FROM rss_item ORDER BY pub_date DESC,created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> loadRssItemsWithLimit(int limit);

    @Query("SELECT * FROM rss_item WHERE is_read = :isRead ORDER BY pub_date DESC,created_date_time DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsByIsReadWithLimit(int isRead, int limit);

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
}
