package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;

import androidx.room.Room;

import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = context.getApplicationContext();
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(appContext,
                        AppDatabase.class, "a-news-provider.db")
                        .build());
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(RssDao.class, () ->
                provider.get(AppDatabase.class).rssDao());
        providerRegistry.registerAsync(AndroidNotificationDao.class, () ->
                provider.get(AppDatabase.class).androidNotificationDao());
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}
