package m.co.rh.id.a_news_provider.base.provider.component;

import androidx.room.Database;
import androidx.room.RoomDatabase;

import m.co.rh.id.a_news_provider.base.provider.component.dao.AndroidNotificationDao;
import m.co.rh.id.a_news_provider.base.provider.component.dao.ArticleContentDao;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.AndroidNotification;
import m.co.rh.id.a_news_provider.base.entity.ArticleContent;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

@Database(entities = {RssChannel.class, RssItem.class,
        AndroidNotification.class, ArticleContent.class},
        version = 15)
public abstract class AppDatabase extends RoomDatabase {
    public abstract RssDao rssDao();

    public abstract AndroidNotificationDao androidNotificationDao();

    public abstract ArticleContentDao articleContentDao();
}
