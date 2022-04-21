package m.co.rh.id.a_news_provider.provider;

import android.app.Application;

import androidx.work.WorkManager;

import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.provider.CommandProviderModule;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.event.AppSharedPreferencesEventHandler;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.component.network.provider.NetworkProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * ProviderModule that is the exact same as AppProviderModule except Navigator,
 * and some components are set to registerLazy
 */
public class IntegrationTestAppProviderModule implements ProviderModule {

    private Application mApplication;
    private String mDbName;

    public IntegrationTestAppProviderModule(Application application, String dbName) {
        mApplication = application;
        mDbName = dbName;
    }

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule(
                mDbName));
        providerRegistry.registerModule(new NetworkProviderModule());
        providerRegistry.registerModule(new CommandProviderModule());

        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider));
        providerRegistry.registerLazy(WorkManager.class, () -> WorkManager.getInstance(provider.getContext()));
        providerRegistry.registerLazy(AppSharedPreferencesEventHandler.class, () -> new AppSharedPreferencesEventHandler(provider));
        // for rss
        providerRegistry.registerLazy(RssChangeNotifier.class, () -> new RssChangeNotifier(provider));
        providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider));

        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));
    }

    @Override
    public void dispose(Provider provider) {
        mApplication = null;
    }
}
