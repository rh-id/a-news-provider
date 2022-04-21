package m.co.rh.id.a_news_provider.base.provider;

import androidx.room.Room;

import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.room.DbMigration;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for database configuration
 */
public class DatabaseProviderModule implements ProviderModule {

    private String mDbName;

    public DatabaseProviderModule(String dbName) {
        mDbName = dbName;
    }

    public DatabaseProviderModule() {
        mDbName = "a-news-provider.db";
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerAsync(AppDatabase.class, () ->
                Room.databaseBuilder(provider.getContext(),
                        AppDatabase.class, mDbName)
                        .addMigrations(DbMigration.getAll())
                        .build());
        // register Dao separately to decouple from AppDatabase
        providerRegistry.registerAsync(RssDao.class, () ->
                provider.get(AppDatabase.class).rssDao());
        providerRegistry.registerAsync(AndroidNotificationDao.class, () ->
                provider.get(AppDatabase.class).androidNotificationDao());
    }

    @Override
    public void dispose(Provider provider) {
        mDbName = null;
    }
}
