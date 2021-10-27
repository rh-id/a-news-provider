package m.co.rh.id.a_news_provider.app.provider;

import android.content.Context;

import androidx.work.WorkManager;

import m.co.rh.id.a_news_provider.app.network.RssRequestFactory;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.NetworkProviderModule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new NetworkProviderModule());

        providerRegistry.registerAsync(DeviceStatusNotifier.class,
                () -> {
                    DeviceStatusNotifier deviceStatusNotifier = new DeviceStatusNotifier(provider, context);
                    BaseApplication.of(context).registerActivityLifecycleCallbacks(deviceStatusNotifier);
                    return deviceStatusNotifier;
                });
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider, context));
        providerRegistry.registerFactory(RxDisposer.class, () -> new RxDisposer());
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));
        // for rss
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerLazy(RssRequestFactory.class, () -> new RssRequestFactory(provider, context));
        providerRegistry.registerAsync(RssChangeNotifier.class, () -> new RssChangeNotifier(provider, context));
        providerRegistry.registerFactory(PagedRssItemsCmd.class, () -> new PagedRssItemsCmd(provider));
        providerRegistry.registerFactory(NewRssChannelCmd.class, () -> new NewRssChannelCmd(provider, context));
        providerRegistry.registerFactory(RenameRssFeedCmd.class, () -> new RenameRssFeedCmd(provider, context));
        providerRegistry.registerFactory(SyncRssCmd.class, () -> new SyncRssCmd(provider, context));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // Leave blank
    }
}
