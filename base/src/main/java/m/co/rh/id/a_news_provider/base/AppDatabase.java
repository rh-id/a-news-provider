package m.co.rh.id.a_news_provider.base;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_news_provider.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.AndroidNotification;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

@Database(entities = {RssChannel.class, RssItem.class,
        AndroidNotification.class},
        version = 6)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RssDao rssDao();

    public abstract AndroidNotificationDao androidNotificationDao();
}
