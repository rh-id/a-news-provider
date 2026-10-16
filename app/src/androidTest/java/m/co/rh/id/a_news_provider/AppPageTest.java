package m.co.rh.id.a_news_provider;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.page.HomePage;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.app.ui.page.SearchRssPage;
import m.co.rh.id.a_news_provider.app.ui.page.SettingsPage;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
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

    /**
     * Route of the empty host page the rss item detail test pushes onto. Not a
     * Routes constant: it exists only inside this test's navigator configuration.
     */
    private static final String DETAIL_HOST_PAGE = "/detail_host";

    private static final long AWAIT_TIMEOUT_MILLIS = 20000;
    private static final long AWAIT_POLL_MILLIS = 100;

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
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

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
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

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

    // simple test to just ensure search page didn't crash
    @Test
    public void searchPage_displayed() {
        String dbName = "searchPage_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.SEARCH_RSS_PAGE, (args, activity) -> new SearchRssPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.SEARCH_RSS_PAGE, navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        onView(withId(R.id.toolbar)).check(matches(isDisplayed()));
        onView(withText(R.string.menu_search))
                .check(matches(withParent(withId(R.id.toolbar))));
        mainActivityScenario.close();
        testProvider.dispose();
        testApplication.unregisterActivityLifecycleCallbacks(navigator);
        testApplication.unregisterComponentCallbacks(navigator);
        testApplication.deleteDatabase(dbName);
    }

    /**
     * First launch of {@link RssItemDetailPage} in any test: the article title and
     * body render. This is also the path the reading-typography appliers run on
     * (issue #29), so it proves the appliers survive the real binding path on the
     * page that consumes them - a crash in either would fail this test. It asserts
     * only that the title and body are displayed; the size, family, spacing and
     * justification they actually receive are not checked here, and it does not
     * exercise the settings screens that write those preferences.
     * <p>
     * The detail page deliberately cannot be the navigator's <em>initial</em>
     * route: {@code NavConfiguration} only exposes
     * {@code getInitialRouteName()}, with no initial-route args, so
     * {@code Args.of(navRoute)} resolves to null, {@code mRssItem} stays null and
     * {@code bindRssItem()} dereferences it. The page therefore has to be pushed
     * with its arguments, which is what the real app does from
     * {@code RssItemSV} and what {@code openNeighbor()} does - so this test hosts
     * an empty page and pushes the real detail route onto it.
     */
    @Test
    public void rssItemDetailPage_displayed() {
        String dbName = "rssItemDetailPage_displayed";
        TestApplication testApplication = (TestApplication) InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        Provider testProvider = Provider.createProvider(testApplication, new IntegrationTestAppProviderModule(testApplication, dbName));
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = "https://detail.test.com/feed";
        rssChannel.title = "Detail Test Feed";
        rssChannel.feedName = "Detail Test Feed";
        RssItem rssItem = new RssItem();
        rssItem.title = "Detail Test Article";
        rssItem.link = "https://detail.test.com/article";
        rssItem.description = "<p>Article body rendered by the detail page.</p>";
        testProvider.get(RssDao.class).insertRssChannel(rssChannel, rssItem);

        CountDownLatch hostPageCreated = new CountDownLatch(1);
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(DETAIL_HOST_PAGE, (args, activity) -> {
            hostPageCreated.countDown();
            return new DetailHostPage();
        });
        navMap.put(Routes.RSS_ITEM_DETAIL_PAGE, (args, activity) -> new RssItemDetailPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(DETAIL_HOST_PAGE, navMap);
        navBuilder.setRequiredComponent(testProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        Navigator navigator = new Navigator(MainActivity.class, navConfiguration);
        testApplication.registerActivityLifecycleCallbacks(navigator);
        testApplication.registerComponentCallbacks(navigator);
        testApplication.setProvider(testProvider);
        testProvider.get(ProviderRegistry.class).register(INavigator.class, () -> navigator);

        ActivityScenario<MainActivity> mainActivityScenario = ActivityScenario.launch(MainActivity.class);
        try {
            // the Navigator creates routes on the main thread after a background
            // load, so a cross-thread latch is the deterministic wait for the host
            // page to exist before pushing onto it
            if (!hostPageCreated.await(20, TimeUnit.SECONDS)) {
                fail("Host page was not created");
            }
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    navigator.push(Routes.RSS_ITEM_DETAIL_PAGE,
                            RssItemDetailPage.Args.withRss(rssItem, rssChannel), null,
                            RssItemDetailPage.detailPageRouteOptions()));

            // the push runs a Material fade+slide transition that Espresso does not
            // register as an idling resource, so wait for the article to actually
            // come on screen before asserting on it
            awaitDisplayed(mainActivityScenario, R.id.text_title, "text_title");
            onView(withId(R.id.text_title)).check(matches(isDisplayed()));
            onView(withId(R.id.text_content)).check(matches(isDisplayed()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for the host page");
        } finally {
            mainActivityScenario.close();
            testProvider.dispose();
            testApplication.unregisterActivityLifecycleCallbacks(navigator);
            testApplication.unregisterComponentCallbacks(navigator);
            testApplication.deleteDatabase(dbName);
        }
    }

    /**
     * Blocks until the view with the given id is attached, laid out and on screen,
     * failing with the whole ancestor chain when it never gets there. The push
     * transition is not an Espresso idling resource, so a plain assertion can run
     * while the incoming page is still sliding in.
     *
     * @param scenario  the launched activity, used to resolve the view
     * @param viewId    the id of the view to wait for
     * @param viewResId the resource name, for the failure message
     */
    private static void awaitDisplayed(ActivityScenario<MainActivity> scenario, int viewId,
                                       String viewResId) {
        long deadline = SystemClock.elapsedRealtime() + AWAIT_TIMEOUT_MILLIS;
        Rect rect = new Rect();
        String chain = "not found";
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean displayed = new AtomicBoolean(false);
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                if (view != null) {
                    displayed.set(view.getVisibility() == View.VISIBLE
                            && view.getWidth() > 0 && view.getHeight() > 0
                            && view.getGlobalVisibleRect(rect) && !rect.isEmpty());
                }
            });
            if (displayed.get()) {
                return;
            }
            AtomicReference<String> chainRef = new AtomicReference<>("not found");
            scenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                StringBuilder builder = new StringBuilder();
                while (view != null) {
                    if (builder.length() > 0) {
                        builder.append(" <- ");
                    }
                    builder.append(view.getClass().getSimpleName())
                            .append("(vis=").append(view.getVisibility())
                            .append(",size=").append(view.getWidth()).append('x').append(view.getHeight())
                            .append(",shown=").append(view.isShown())
                            .append(",attached=").append(view.isAttachedToWindow())
                            .append(')');
                    view = view.getParent() instanceof View ? (View) view.getParent() : null;
                }
                chainRef.set(builder.toString());
            });
            chain = chainRef.get();
            SystemClock.sleep(AWAIT_POLL_MILLIS);
        }
        fail(viewResId + " never became displayed. ancestor chain: " + chain);
    }

    /**
     * Empty page the detail route is pushed onto. It exists only so the pushed
     * route has a page below it; it renders nothing and is never asserted on.
     */
    private static class DetailHostPage extends StatefulView<Activity> {

        @Override
        protected View createView(Activity activity, ViewGroup container) {
            FrameLayout frameLayout = new FrameLayout(activity);
            frameLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            return frameLayout;
        }
    }
}