package m.co.rh.id.a_news_provider.app.provider;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.WorkManager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.network.RssRequestFactory;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.page.SettingsPage;
import m.co.rh.id.a_news_provider.app.ui.page.SplashPage;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.NetworkProviderModule;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application application;

    public AppProviderModule(Application application) {
        this.application = application;
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new NetworkProviderModule());
        providerRegistry.register(DeviceStatusNotifier.class, getDeviceStatusNotifier(context, provider));
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider, context));
        providerRegistry.registerFactory(RxDisposer.class, RxDisposer::new);
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));
        // for rss
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerLazy(RssRequestFactory.class, () -> new RssRequestFactory(provider, context));
        providerRegistry.registerAsync(RssChangeNotifier.class, () -> new RssChangeNotifier(provider, context));
        providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider, context));
        providerRegistry.registerFactory(PagedRssItemsCmd.class, () -> new PagedRssItemsCmd(provider));
        providerRegistry.registerFactory(NewRssChannelCmd.class, () -> new NewRssChannelCmd(provider, context));
        providerRegistry.registerFactory(RenameRssFeedCmd.class, () -> new RenameRssFeedCmd(provider, context));
        providerRegistry.registerFactory(SyncRssCmd.class, () -> new SyncRssCmd(provider, context));

        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(INavigator.class, getNavigator());
    }

    @NonNull
    private DeviceStatusNotifier getDeviceStatusNotifier(Context context, Provider provider) {
        DeviceStatusNotifier deviceStatusNotifier = new DeviceStatusNotifier(provider, context);
        application.registerActivityLifecycleCallbacks(deviceStatusNotifier);
        return deviceStatusNotifier;
    }

    private Navigator getNavigator() {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setSaveStateFile(new File(application.getCacheDir(),
                "anavigator/Navigator.state"));
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        application.registerActivityLifecycleCallbacks(navigator);
        application.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // Leave blank
    }
}
