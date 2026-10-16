package m.co.rh.id.a_news_provider;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;

import android.app.Activity;
import android.os.Build;

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
import m.co.rh.id.a_news_provider.app.ui.component.settings.ReadingFontFamilyMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ReadingFontSizeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ReadingJustificationMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ReadingLineSpacingMenuSV;
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
public class AppSVTest {

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

    // ========================================================================
    // reading typography settings rows (issue #29)
    // ========================================================================

    @Test
    public void readingFontSizeMenuSv_displayed() {
        runMenuSvTest("readingFontSizeMenuSv_displayed", new ReadingFontSizeMenuSV(),
                () -> assertTitleAndSubtitleDisplayed(R.string.menu_reading_font_size));
    }

    @Test
    public void readingFontFamilyMenuSv_displayed() {
        runMenuSvTest("readingFontFamilyMenuSv_displayed", new ReadingFontFamilyMenuSV(),
                () -> assertTitleAndSubtitleDisplayed(R.string.menu_reading_font_family));
    }

    @Test
    public void readingLineSpacingMenuSv_displayed() {
        runMenuSvTest("readingLineSpacingMenuSv_displayed", new ReadingLineSpacingMenuSV(),
                () -> assertTitleAndSubtitleDisplayed(R.string.menu_reading_line_spacing));
    }

    /**
     * The justification row is the odd one out: justification only exists from
     * API 26, so below that the row is deliberately hidden rather than shown and
     * silently ignored. Assert whichever behaviour matches the running API.
     */
    @Test
    public void readingJustificationMenuSv_displayed() {
        boolean justificationSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        runMenuSvTest("readingJustificationMenuSv_displayed",
                new ReadingJustificationMenuSV(), () -> {
                    if (justificationSupported) {
                        assertTitleAndSubtitleDisplayed(R.string.menu_reading_justification);
                    } else {
                        onView(withText(R.string.menu_reading_justification))
                                .check(matches(not(isDisplayed())));
                    }
                });
    }

    /**
     * Asserts the row shows its own title and a non-empty subtitle.
     *
     * @param titleStringRes the row title the settings page labels it with
     */
    private static void assertTitleAndSubtitleDisplayed(int titleStringRes) {
        onView(withText(titleStringRes)).check(matches(isDisplayed()));
        // every reading row names its current value in the subtitle, so an empty
        // one means the row failed to read the preference, not that it is unset
        onView(withId(R.id.text_subtitle)).check(matches(not(withText(""))));
    }

    /**
     * Hosts one settings row in a TestPage - the same way SettingsPage builds it -
     * runs the assertions against it and tears everything down again.
     *
     * @param dbName      unique database name for this test's provider
     * @param menuSv      the row under test
     * @param assertions  the Espresso assertions to run against the rendered row
     */
    private void runMenuSvTest(String dbName, StatefulView<Activity> menuSv,
                               Runnable assertions) {
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put("/menu", (args, activity) -> new TestPage(menuSv));
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>("/menu", navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        try {
            assertions.run();
        } finally {
            mainActivityScenario.close();
            testProvider.dispose();
            testApplication.unregisterActivityLifecycleCallbacks(navigator);
            testApplication.unregisterComponentCallbacks(navigator);
            testApplication.deleteDatabase(dbName);
        }
    }

}