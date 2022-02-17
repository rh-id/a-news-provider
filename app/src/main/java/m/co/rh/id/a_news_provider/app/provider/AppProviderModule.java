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
import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.ui.page.SettingsPage;
import m.co.rh.id.a_news_provider.app.ui.page.SplashPage;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.component.network.provider.NetworkProviderModule;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class AppProviderModule implements ProviderModule {

    private Application mApplication;
    private Navigator mNavigator;
    private DeviceStatusNotifier mDeviceStatusNotifier;

    public AppProviderModule(Application application) {
        mApplication = application;
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new BaseProviderModule());
        providerRegistry.registerModule(new DatabaseProviderModule());
        providerRegistry.registerModule(new NetworkProviderModule());
        providerRegistry.registerModule(new CommandProviderModule(provider));

        providerRegistry.register(DeviceStatusNotifier.class, getDeviceStatusNotifier(context, provider));
        providerRegistry.registerLazy(AppNotificationHandler.class, () -> new AppNotificationHandler(provider, context));
        providerRegistry.registerAsync(WorkManager.class, () -> WorkManager.getInstance(context));
        // for rss
        providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider, context));
        providerRegistry.registerAsync(RssChangeNotifier.class, () -> new RssChangeNotifier(provider, context));
        providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider, context));

        providerRegistry.registerPool(StatefulViewProvider.class, () -> new StatefulViewProvider(provider));

        // it is safer to register navigator last in case it needs dependency from all above, provider can be passed here
        providerRegistry.register(INavigator.class, getNavigator(provider));
    }

    @NonNull
    private DeviceStatusNotifier getDeviceStatusNotifier(Context context, Provider provider) {
        DeviceStatusNotifier deviceStatusNotifier = new DeviceStatusNotifier(provider, context);
        mApplication.registerActivityLifecycleCallbacks(deviceStatusNotifier);
        mDeviceStatusNotifier = deviceStatusNotifier;
        return deviceStatusNotifier;
    }

    private Navigator getNavigator(Provider provider) {
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
        navBuilder.setSaveStateFile(new File(mApplication.getCacheDir(),
                "anavigator/Navigator.state"));
        navBuilder.setRequiredComponent(provider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mNavigator = navigator;
        mApplication.registerActivityLifecycleCallbacks(navigator);
        mApplication.registerComponentCallbacks(navigator);
        return navigator;
    }

    @Override
    public void dispose(Context context, Provider provider) {
        mApplication.unregisterActivityLifecycleCallbacks(mDeviceStatusNotifier);
        mApplication.unregisterActivityLifecycleCallbacks(mNavigator);
        mApplication.unregisterComponentCallbacks(mNavigator);
        mNavigator = null;
        mApplication = null;
    }
}
