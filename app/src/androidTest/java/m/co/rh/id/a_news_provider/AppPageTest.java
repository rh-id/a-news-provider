package m.co.rh.id.a_news_provider;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.page.HomePage;
import m.co.rh.id.a_news_provider.app.ui.page.SettingsPage;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented test, to test pages
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class AppPageTest {

    @Test
    public void useTestApplication() {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        assertTrue(TestApplication.class.isInstance(appContext));
    }

    // simple test to just ensure home page didn't crash
    @Test
    public void homePage_displayed() {
        String dbName = "homePage_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> new HomePage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withText(R.string.home))
                .check(matches(withParent(withId(R.id.toolbar))));
        mainActivityScenario.close();
        testProvider.dispose();
        testApplication.unregisterActivityLifecycleCallbacks(navigator);
        testApplication.unregisterComponentCallbacks(navigator);
        testApplication.deleteDatabase(dbName);
    }

    // simple test to just ensure settings page didn't crash
    @Test
    public void settingsPage_displayed() {
        String dbName = "settingsPage_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.SETTINGS_PAGE, (args, activity) -> new SettingsPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.SETTINGS_PAGE, navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withText(R.string.settings))
                .check(matches(withParent(withId(R.id.toolbar))));
        mainActivityScenario.close();
        testProvider.dispose();
        testApplication.unregisterActivityLifecycleCallbacks(navigator);
        testApplication.unregisterComponentCallbacks(navigator);
        testApplication.deleteDatabase(dbName);
    }
}