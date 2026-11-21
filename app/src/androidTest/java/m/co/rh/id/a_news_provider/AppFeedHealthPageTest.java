package m.co.rh.id.a_news_provider;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.work.WorkManager;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.page.FeedHealthPage;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.FakeWorkManager;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.util.ProviderDbRule;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * On-device Espresso smoke suite for the feed-health dashboard (issue #42),
 * replacing the manual live-UI matrix: it proves the page end-to-end against
 * the real {@link FeedHealthPage} + FeedHealthCmd + RssDao stack.
 * <p>
 * Coverage: the page opens and renders its RecyclerView (empty state without
 * channels), the channel-driven zero-fill merge (a channel with ZERO item rows
 * must still be listed, next to a channel with items), the counts string and
 * the never-synced timeline of a feed that was never attempted, the pause flow
 * (badge flips to paused, sync-now button disappears - the worker would
 * silently skip a paused feed), the delete flow (confirm dialog, then row
 * removal + empty state + DB row gone) and the ERROR state (badge + the
 * composed HTTP-status/error line of a synthetically failed channel).
 * <p>
 * Harness notes:
 * <ul>
 * <li>The dashboard is the navigator's INITIAL route (the same harness shape as
 * AppPageTest's settings/search page tests): the page needs no route args, and
 * the home drawer's menu button is not exercised because that would drag the
 * whole home page (paged list, sync-on-open hooks) into a smoke suite about the
 * dashboard.</li>
 * <li>Channels and items are seeded directly via {@link RssDao} BEFORE launch so
 * the page's first refresh sees them; read-state flips use the DAO exactly like
 * RssDaoTest, and the synthetic failure state of the ERROR test is written with
 * the real health writer {@code markRssChannelSyncFailure} (issue #50) instead
 * of raw SQL.</li>
 * <li>A {@link FakeWorkManager} is registered BEFORE the wrapped module (the
 * NewRssChannelCmdTest mechanism): the page wires its rows' delete command,
 * whose constructor resolves WorkManager - the real WorkManager singleton would
 * be initialized with THIS test provider's executor and then outlive it in the
 * process. The double keeps the suite hermetic; no worker ever runs, so NO real
 * network happens in any test.</li>
 * <li>Dashboard rows are async (command refresh on the app executor, emission on
 * the main thread), so DB-driven assertions poll with the await helpers instead
 * of asserting immediately; row-visible state that Espresso cannot see (a GONE
 * button) is polled by direct view inspection, which also keeps the Espresso
 * accessibility gate away from invisible views.</li>
 * <li>Touch targets and text/background pairs of the dashboard row are kept
 * above the ERROR-level thresholds the gate enforces (48dp buttons,
 * colorError/colorOnError pairs); the Material dialog's framework buttons are
 * library-rendered noise, suppressed in TestApplication like the spinner and
 * progress-bar noise.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class AppFeedHealthPageTest {

    private static final long AWAIT_TIMEOUT_MILLIS = 20000;
    private static final long AWAIT_POLL_MILLIS = 100;

    private static final String FEED_ALPHA = "Alpha Feed";
    private static final String FEED_BETA_EMPTY = "Beta Empty";
    private static final String FEED_COUNT = "Count Feed";
    private static final String FEED_PAUSE = "Pause Feed";
    private static final String FEED_DELETE = "Delete Feed";
    private static final String FEED_ERROR = "Error Feed";

    @Rule
    public final ProviderDbRule mDbRule = new ProviderDbRule();

    private String mDbName;
    private Context mTargetContext;
    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private FakeWorkManager mFakeWorkManager;
    private Navigator mNavigator;
    private ActivityScenario<MainActivity> mMainActivityScenario;
    private RssDao mRssDao;

    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDbName = "appFeedHealthPageTest_" + System.nanoTime();
        mTestApplication = (TestApplication) mTargetContext.getApplicationContext();
        mFakeWorkManager = new FakeWorkManager();
        mTestProvider = mDbRule.create(mTestApplication,
                new WorkManagerOverrideProviderModule(mTestApplication, mDbName,
                        mFakeWorkManager),
                mDbName);
        mRssDao = mTestProvider.get(RssDao.class);
    }

    @After
    public void tearDown() {
        if (mMainActivityScenario != null) {
            mMainActivityScenario.close();
            mMainActivityScenario = null;
        }
        if (mNavigator != null) {
            mTestApplication.unregisterActivityLifecycleCallbacks(mNavigator);
            mTestApplication.unregisterComponentCallbacks(mNavigator);
            mNavigator = null;
        }
        // ProviderDbRule (runs after @After) closes the Room instance, disposes
        // the provider and deletes the per-test database
    }

    // ========================================================================
    // a. page opens, RecyclerView rendered
    // ========================================================================

    @Test
    public void feedHealthPage_displayed() {
        launchFeedHealthPage();

        onView(withText(R.string.feed_health))
                .check(matches(withParent(withId(R.id.toolbar))));
        awaitAssertion("recycler view", () ->
                onView(withId(R.id.recycler_view)).check(matches(isDisplayed())));
        // no channels seeded: the adapter's empty-state row renders inside the
        // RecyclerView
        awaitAssertion("empty state without channels", () ->
                onView(withText(R.string.no_record)).check(matches(isDisplayed())));
    }

    // ========================================================================
    // b. zero-fill merge: the zero-item channel must be listed
    // ========================================================================

    @Test
    public void feedHealthPage_listsItemsAndEmptyFeeds() {
        RssChannel alphaChannel = createRssChannel(FEED_ALPHA);
        RssItem alphaRead = createRssItem("alpha read", "https://alpha.test.com/read");
        RssItem alphaUnread = createRssItem("alpha unread", "https://alpha.test.com/unread");
        mRssDao.insertRssChannel(alphaChannel, alphaRead, alphaUnread);
        alphaRead.isRead = true;
        mRssDao.updateRssItem(alphaRead);
        // NO items at all: the count query omits this channel, the command's
        // zero-fill must keep it visible on the dashboard
        mRssDao.insertRssChannel(createRssChannel(FEED_BETA_EMPTY));

        launchFeedHealthPage();

        awaitAssertion("both channels listed", () -> {
            onView(withText(FEED_ALPHA)).check(matches(isDisplayed()));
            onView(withText(FEED_BETA_EMPTY)).check(matches(isDisplayed()));
        });
        awaitAssertion("zero-item channel renders zero-filled counts", () ->
                onView(allOf(withId(R.id.text_counts), withText(expectedCounts(0, 0))))
                        .check(matches(isDisplayed())));
        awaitAssertion("item channel renders its counts", () ->
                onView(allOf(withId(R.id.text_counts), withText(expectedCounts(1, 2))))
                        .check(matches(isDisplayed())));
    }

    // ========================================================================
    // c. counts + never-synced timeline
    // ========================================================================

    @Test
    public void feedHealthPage_rendersCountsAndNeverSyncedTimeline() {
        RssChannel countChannel = createRssChannel(FEED_COUNT);
        RssItem countRead = createRssItem("count read", "https://count.test.com/read");
        RssItem countUnread = createRssItem("count unread", "https://count.test.com/unread");
        mRssDao.insertRssChannel(countChannel, countRead, countUnread);
        countRead.isRead = true;
        mRssDao.updateRssItem(countRead);

        launchFeedHealthPage();

        awaitAssertion("counts string renders", () ->
                onView(allOf(withId(R.id.text_counts), withText(expectedCounts(1, 2))))
                        .check(matches(isDisplayed())));
        // the channel was never attempted: the last-attempt line shows the
        // never-synced string instead of a date
        awaitAssertion("never-synced timeline", () ->
                onView(withText(R.string.feed_health_never_synced))
                        .check(matches(isDisplayed())));
        // and the last-success line stays hidden for such a channel: its
        // updated_date_time is the add time, never a success (issue #42)
        awaitViewCondition("last-success line hidden for a never-synced feed",
                R.id.text_last_success, view -> view.getVisibility() == View.GONE);
    }

    // ========================================================================
    // d. pause: badge flips, sync-now disappears
    // ========================================================================

    @Test
    public void feedHealthPage_pauseHidesSyncNowAndShowsPausedBadge() {
        RssChannel pauseChannel = createRssChannel(FEED_PAUSE);
        mRssDao.insertRssChannel(pauseChannel,
                createRssItem("pause item", "https://pause.test.com/item"));

        launchFeedHealthPage();

        awaitAssertion("row bound before interacting", () ->
                onView(withText(FEED_PAUSE)).check(matches(isDisplayed())));
        onView(withId(R.id.button_pause)).perform(click());

        awaitAssertion("paused badge", () ->
                onView(allOf(withId(R.id.text_status), withText(R.string.paused)))
                        .check(matches(isDisplayed())));
        awaitAssertion("pause button label flips to unpause", () ->
                onView(allOf(withId(R.id.button_pause), withText(R.string.unpause)))
                        .check(matches(isDisplayed())));
        // polled by direct view inspection: the button is GONE, and Espresso
        // assertions on invisible views are exactly what the a11y gate should
        // not be fed
        awaitViewCondition("sync-now hidden on the paused row",
                R.id.button_sync_now, view -> view.getVisibility() == View.GONE);
    }

    // ========================================================================
    // e. delete: confirm dialog, then row removed + empty state
    // ========================================================================

    @Test
    public void feedHealthPage_deleteShowsConfirmDialogThenRemovesRow() {
        RssChannel deleteChannel = createRssChannel(FEED_DELETE);
        mRssDao.insertRssChannel(deleteChannel,
                createRssItem("delete item", "https://delete.test.com/item"));

        launchFeedHealthPage();

        awaitAssertion("row bound before interacting", () ->
                onView(withText(FEED_DELETE)).check(matches(isDisplayed())));
        onView(withId(R.id.button_delete)).perform(click());

        // deliberate new UX on this dashboard (issue #42): delete asks first
        awaitAssertion("confirm dialog shown", () ->
                onView(withText(R.string.feed_health_delete_confirm_title))
                        .check(matches(isDisplayed())));
        onView(withText(R.string.delete)).inRoot(isDialog()).perform(click());

        awaitAssertion("row removed, empty state shown", () ->
                onView(withText(R.string.no_record)).check(matches(isDisplayed())));
        assertTrue("The confirmed channel must be gone from the database",
                mRssDao.loadAllRssChannel().isEmpty());
    }

    // ========================================================================
    // f. ERROR state: badge + composed error line
    // ========================================================================

    @Test
    public void feedHealthPage_errorChannelShowsErrorBadgeAndErrorText() {
        RssChannel errorChannel = createRssChannel(FEED_ERROR);
        mRssDao.insertRssChannel(errorChannel,
                createRssItem("error item", "https://error.test.com/item"));
        // synthetic failure through the REAL health writer (issue #50): HTTP
        // 500 + message, one consecutive failure, attempt stamp = now - which
        // is exactly the state markRssChannelSyncFailure persists on a failed
        // fetch, so the dashboard renders it like production would
        mRssDao.markRssChannelSyncFailure(errorChannel.id, 500, "test error", new Date());

        launchFeedHealthPage();

        awaitAssertion("ERROR badge", () ->
                onView(allOf(withId(R.id.text_status), withText(R.string.status_error)))
                        .check(matches(isDisplayed())));
        awaitAssertion("HTTP status + error message line", () ->
                onView(allOf(withId(R.id.text_error), withText(
                        mTargetContext.getString(R.string.feed_health_http_status, 500)
                                + " - " + "test error")))
                        .check(matches(isDisplayed())));
    }

    // ========================================================================
    // harness + helpers
    // ========================================================================

    /**
     * Launches {@link MainActivity} with a test navigator whose ONLY route is
     * the feed-health dashboard (the AppPageTest settings/search harness shape):
     * the page needs no route args, so it can be the initial route and no home
     * page is dragged into the suite.
     */
    private void launchFeedHealthPage() {
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.FEED_HEALTH_PAGE, (args, activity) -> new FeedHealthPage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.FEED_HEALTH_PAGE, navMap);
        navBuilder.setRequiredComponent(mTestProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        mNavigator = new Navigator(MainActivity.class, navConfiguration);
        mTestApplication.registerActivityLifecycleCallbacks(mNavigator);
        mTestApplication.registerComponentCallbacks(mNavigator);
        mTestApplication.setProvider(mTestProvider);
        mTestProvider.get(ProviderRegistry.class).register(INavigator.class, () -> mNavigator);

        mMainActivityScenario = ActivityScenario.launch(MainActivity.class);
    }

    private RssChannel createRssChannel(String feedName) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = "https://feed-health.test.com/" + feedName.replace(' ', '-');
        rssChannel.feedName = feedName;
        return rssChannel;
    }

    private RssItem createRssItem(String title, String link) {
        RssItem rssItem = new RssItem();
        rssItem.title = title;
        rssItem.link = link;
        return rssItem;
    }

    /**
     * The exact dashboard counts string the row renders for the given numbers.
     *
     * @param unreadItems the unread subset of the channel
     * @param totalItems  the total item count of the channel
     */
    private String expectedCounts(int unreadItems, int totalItems) {
        return mTargetContext.getString(R.string.feed_health_counts, unreadItems, totalItems);
    }

    /**
     * Blocks until the Espresso assertion passes, retrying at the poll interval:
     * the dashboard loads asynchronously (command refresh on the app executor,
     * emission on the main thread) and none of that is an Espresso idling
     * resource. A real failure repeats every retry and surfaces as the last
     * error when the deadline passes.
     *
     * @param description what is being awaited, for the failure message
     * @param assertion   the Espresso assertion to retry
     */
    private static void awaitAssertion(String description, Runnable assertion) {
        long deadline = SystemClock.elapsedRealtime() + AWAIT_TIMEOUT_MILLIS;
        Throwable lastError = null;
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                assertion.run();
                return;
            } catch (Throwable throwable) {
                lastError = throwable;
                SystemClock.sleep(AWAIT_POLL_MILLIS);
            }
        }
        fail(description + " never passed. last error: " + lastError);
    }

    /**
     * Blocks until the view with the given id satisfies the predicate, polled by
     * direct view inspection on the main thread - used for state Espresso cannot
     * express on visible views only (a GONE button must not be fed to the a11y
     * gate as an invisible assertion target).
     *
     * @param description what is being awaited, for the failure message
     * @param viewId      the id of the view to inspect
     * @param viewState   the state predicate to satisfy
     */
    private void awaitViewCondition(String description, int viewId,
                                    Predicate<View> viewState) {
        long deadline = SystemClock.elapsedRealtime() + AWAIT_TIMEOUT_MILLIS;
        while (SystemClock.elapsedRealtime() < deadline) {
            AtomicBoolean satisfied = new AtomicBoolean(false);
            mMainActivityScenario.onActivity(activity -> {
                View view = activity.findViewById(viewId);
                satisfied.set(view != null && viewState.test(view));
            });
            if (satisfied.get()) {
                return;
            }
            SystemClock.sleep(AWAIT_POLL_MILLIS);
        }
        fail(description);
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers the
     * do-nothing {@link FakeWorkManager} FIRST: DefaultProvider throws on
     * duplicate registrations and with setSkipSameType(true) it keeps the FIRST
     * registration, so the double wins over the wrapped module's real
     * WorkManager registration (same mechanism as NewRssChannelCmdTest). The
     * real WorkManager singleton would otherwise be initialized with THIS test
     * provider's executor and then outlive it in the process - every later
     * test's WorkManager resolution would hit a dead executor. The executor
     * itself is deliberately NOT overridden: the dashboard's async refresh and
     * its main-thread emissions must run exactly like production.
     */
    private static class WorkManagerOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final WorkManager mWorkManager;

        WorkManagerOverrideProviderModule(Application application, String dbName,
                                          WorkManager workManager) {
            mApplication = application;
            mDbName = dbName;
            mWorkManager = workManager;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerLazy(WorkManager.class, () -> mWorkManager);
            providerRegistry.setSkipSameType(true);
            providerRegistry.registerModule(new IntegrationTestAppProviderModule(
                    mApplication, mDbName));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
