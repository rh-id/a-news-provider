package m.co.rh.id.a_news_provider;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.app.Activity;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LogMenuSV;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.TestPage;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented test, to test SV components individually
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class AppSvTest {

    // simple test to just ensure LogMenuSV didn't crash
    @Test
    public void logMenuSv_displayed() {
        String dbName = "logMenuSv_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put("/log", (args, activity) -> new TestPage(new LogMenuSV()));
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>("/log", navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        onView(withText(R.string.log_file))
                .check(matches(withId(R.id.menu_log_file)));
        mainActivityScenario.close();
        testProvider.dispose();
        testApplication.unregisterActivityLifecycleCallbacks(navigator);
        testApplication.unregisterComponentCallbacks(navigator);
        testApplication.deleteDatabase(dbName);
    }

    // simple test to just ensure LicenseMenuSV didn't crash
    @Test
    public void licenseMenuSv_displayed() {
        String dbName = "licenseMenuSv_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put("/license", (args, activity) -> new TestPage(new LicensesMenuSV()));
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>("/license", navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        onView(withText(R.string.licenses))
                .check(matches(withId(R.id.menu_licenses)));
        mainActivityScenario.close();
        testProvider.dispose();
        testApplication.unregisterActivityLifecycleCallbacks(navigator);
        testApplication.unregisterComponentCallbacks(navigator);
        testApplication.deleteDatabase(dbName);
    }

}