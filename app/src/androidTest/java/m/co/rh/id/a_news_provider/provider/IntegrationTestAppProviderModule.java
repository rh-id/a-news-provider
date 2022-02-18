package m.co.rh.id.a_news_provider.provider;

import android.app.Application;
import android.content.Context;

import androidx.work.WorkManager;

import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.provider.CommandProviderModule;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
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
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule(
                mDbName));
        providerRegistry.registerModule(new NetworkProviderModule());
        providerRegistry.registerModule(new CommandProviderModule(provider));

        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider, context));
        providerRegistry.registerLazy(WorkManager.class, () -> WorkManager.getInstance(context));
        // for rss
        providerRegistry.registerLazy(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerLazy(RssChangeNotifier.class, () -> new RssChangeNotifier(provider, context));
        providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider, context));

        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        mApplication = null;
    }
}
