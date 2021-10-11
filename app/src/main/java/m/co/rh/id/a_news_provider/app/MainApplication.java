package m.co.rh.id.a_news_provider.app;

import android.app.Activity;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.provider.AppProviderModule;
import m.co.rh.id.a_news_provider.app.ui.page.SettingsPage;
import m.co.rh.id.a_news_provider.app.ui.page.SplashPage;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;

public class MainApplication extends BaseApplication {

    private Provider mProvider;
    private INavigator mMainNavigator;

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void onCreate() {
        super.onCreate();
        mProvider = Provider.createProvider(this, new AppProviderModule());

        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> {
            if (args instanceof StatefulView) {
                return (StatefulView) args;
            }
            return new SplashPage();
        });
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder = new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setSaveStateFile(new File(getCacheDir(), "Navigator.state"));
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        mMainNavigator = navigator;
        registerActivityLifecycleCallbacks(navigator);
        registerComponentCallbacks(navigator);
    }

    @Override
    public Provider getProvider() {
        return mProvider;
    }

    public INavigator getNavigator(Activity activity) {
        if (activity instanceof MainActivity) {
            return mMainNavigator;
        }
        return null;
    }
}
