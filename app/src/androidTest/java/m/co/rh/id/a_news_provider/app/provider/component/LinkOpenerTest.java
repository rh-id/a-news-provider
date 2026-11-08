package m.co.rh.id.a_news_provider.app.provider.component;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.page.HomePage;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.util.UrlSanitizer;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link LinkOpener} (issues #35/#76) against a REAL
 * activity, so {@code CustomTabsClient.getPackageName} and the preference
 * facade run for real (no Mockito on device - maintainer decision). The
 * launch boundary is replaced WITHOUT a mocking framework: the package-private
 * {@link TestLinkOpener} test subclass overrides LinkOpener's package-private
 * {@code startActivity} seam and RECORDS the intent instead of launching it
 * (same precedent as the {@code fetchArticleBytes} seam in
 * {@code FetchArticleContentCmdTest}). The strip-tracking and open-links-mode
 * preferences are read at CLICK time by openLink - the tests flip the toggle
 * between two calls and assert the very next tap honors the new value.
 * <p>
 * The link provider is a small hand-written module registering only the real
 * {@link AppSharedPreferences} (direct executor for synchronous persistence)
 * and the real {@link UrlSanitizer}, like {@code AppSharedPreferencesTest}'s
 * PrefsTestProviderModule. MainActivity itself resolves its dependencies from
 * the APPLICATION provider, so the launch follows the {@code AppPageTest}
 * homePage_displayed pattern: a full IntegrationTestAppProviderModule-backed
 * provider plus a Navigator registered as INavigator before the launch.
 */
@RunWith(AndroidJUnit4.class)
public class LinkOpenerTest {

    private static final String SHARED_PREFERENCES_NAME = "RssSharedPreferences";
    private static final String TEST_DB_NAME = "linkOpenerTest";

    /**
     * URL carrying two utm_* trackers, one global click id (fbclid) and one
     * non-tracking parameter that must survive the sanitize step.
     */
    private static final String TRACKING_URL =
            "https://example.com/article?keep=1&utm_source=news&utm_medium=app&fbclid=abc123";
    private static final String STRIPPED_URL = "https://example.com/article?keep=1";

    private Context mContext;
    private TestApplication mTestApplication;
    private Provider mAppProvider;
    private Provider mProvider;
    private Navigator mNavigator;
    private ActivityScenario<MainActivity> mScenario;
    private TestLinkOpener mLinkOpener;
    private AppSharedPreferences mAppSharedPreferences;
    private final AtomicReference<Activity> mActivityRef = new AtomicReference<>();

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mTestApplication = (TestApplication) mContext.getApplicationContext();
        // defensive clear: with the direct executor this test's own writes are
        // synchronous, so the wipe only guards against a stale write from a
        // previously-run foreign test class
        mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
        mProvider = Provider.createProvider(mContext, new LinkOpenerTestProviderModule());
        mLinkOpener = new TestLinkOpener(mProvider);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        // MainActivity resolves playback singletons / AppSharedPreferences /
        // INavigator from the APPLICATION provider: give it a full test
        // provider + navigator (homePage_displayed pattern) or the launch
        // crashes before the tests can use the activity
        mAppProvider = Provider.createProvider(mTestApplication,
                new IntegrationTestAppProviderModule(mTestApplication, TEST_DB_NAME));
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(Routes.HOME_PAGE, (args, activity) -> new HomePage());
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(Routes.HOME_PAGE, navMap);
        navBuilder.setRequiredComponent(mAppProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        mNavigator = new Navigator(MainActivity.class, navConfiguration);
        mTestApplication.registerActivityLifecycleCallbacks(mNavigator);
        mTestApplication.registerComponentCallbacks(mNavigator);
        mTestApplication.setProvider(mAppProvider);
        mAppProvider.get(ProviderRegistry.class).register(INavigator.class, () -> mNavigator);
        mScenario = ActivityScenario.launch(MainActivity.class);
        mScenario.onActivity(mActivityRef::set);
    }

    @After
    public void tearDown() {
        if (mScenario != null) {
            mScenario.close();
            mScenario = null;
        }
        if (mProvider != null) {
            mProvider.dispose();
            mProvider = null;
        }
        if (mAppProvider != null) {
            mAppProvider.dispose();
            mAppProvider = null;
        }
        if (mNavigator != null) {
            mTestApplication.unregisterActivityLifecycleCallbacks(mNavigator);
            mTestApplication.unregisterComponentCallbacks(mNavigator);
            mNavigator = null;
        }
        mTestApplication.deleteDatabase(TEST_DB_NAME);
        // this test's own commits are synchronous now, so the wipe simply prevents
        // them from leaking into the next test
        mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void openLink_withNullActivityOrNullOrEmptyUrl_failsGracefully() {
        assertFalse(mLinkOpener.openLink(null, TRACKING_URL));
        assertFalse(mLinkOpener.openLink(mActivityRef.get(), null));
        assertFalse(mLinkOpener.openLink(mActivityRef.get(), ""));
        assertTrue("the guards must fire before any launch attempt",
                mLinkOpener.getRecordedIntents().isEmpty());
    }

    @Test
    public void openLink_whenStripEnabled_stripsTrackingParamsFromLocalCopyOnly() {
        mAppSharedPreferences.setStripTrackingParamsEnabled(true);
        // local variable plays the caller holding RssItem.link: openLink must
        // never write the sanitized value back into it
        String callerLink = TRACKING_URL;

        assertTrue(mLinkOpener.openLink(mActivityRef.get(), callerLink));

        List<Intent> recordedIntents = mLinkOpener.getRecordedIntents();
        assertEquals(1, recordedIntents.size());
        Uri launchedUri = recordedIntents.get(0).getData();
        assertEquals(STRIPPED_URL, launchedUri.toString());
        assertEquals("the non-tracking parameter must be preserved",
                "1", launchedUri.getQueryParameter("keep"));
        assertNull(launchedUri.getQueryParameter("utm_source"));
        assertNull(launchedUri.getQueryParameter("fbclid"));
        assertEquals("the caller's url string must never be mutated",
                TRACKING_URL, callerLink);
    }

    @Test
    public void openLink_whenStripDisabled_launchesRawUrl() {
        mAppSharedPreferences.setStripTrackingParamsEnabled(false);

        assertTrue(mLinkOpener.openLink(mActivityRef.get(), TRACKING_URL));

        List<Intent> recordedIntents = mLinkOpener.getRecordedIntents();
        assertEquals(1, recordedIntents.size());
        assertEquals("the raw url must be launched byte-for-byte when the toggle is off",
                TRACKING_URL, recordedIntents.get(0).getData().toString());
    }

    @Test
    public void openLink_inDefaultBrowserMode_recordsActionViewWithoutPackage() {
        mAppSharedPreferences.setOpenLinksMode(AppSharedPreferences.MODE_DEFAULT_BROWSER);

        assertTrue(mLinkOpener.openLink(mActivityRef.get(), TRACKING_URL));

        List<Intent> recordedIntents = mLinkOpener.getRecordedIntents();
        assertEquals(1, recordedIntents.size());
        Intent intent = recordedIntents.get(0);
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertNull("the default-browser path must not pin a browser package",
                intent.getPackage());
    }

    @Test
    public void openLink_inCustomTabsMode_fallsBackToActionViewOnEmulator() {
        // the emulator image ships no browser declaring Custom Tabs support:
        // OpenLinkPolicy must fall back to the plain ACTION_VIEW path instead
        // of failing the tap
        mAppSharedPreferences.setOpenLinksMode(AppSharedPreferences.MODE_CUSTOM_TABS);

        assertTrue(mLinkOpener.openLink(mActivityRef.get(), TRACKING_URL));

        List<Intent> recordedIntents = mLinkOpener.getRecordedIntents();
        assertEquals(1, recordedIntents.size());
        assertEquals("no custom-tabs browser on the emulator: must fall back to ACTION_VIEW",
                Intent.ACTION_VIEW, recordedIntents.get(0).getAction());
    }

    @Test
    public void openLink_realPath_withUnhandledScheme_swallowsActivityNotFoundException() {
        // the PLAIN LinkOpener (no seam override): "unknownscheme://" has no
        // handler on the emulator, so the real startActivity throws
        // ActivityNotFoundException and openLink must swallow it (launches
        // nothing)
        LinkOpener plainLinkOpener = new LinkOpener(mProvider);
        AtomicBoolean opened = new AtomicBoolean(false);
        mScenario.onActivity(activity ->
                opened.set(plainLinkOpener.openLink(activity, "unknownscheme://whatever")));
        assertFalse(opened.get());
    }

    @Test
    public void openLink_readsPrefsAtClickTime_flipAppliesToVeryNextTap() {
        mAppSharedPreferences.setStripTrackingParamsEnabled(true);
        Activity activity = mActivityRef.get();

        mLinkOpener.openLink(activity, TRACKING_URL);

        assertEquals("first tap with the toggle on: sanitized",
                STRIPPED_URL, mLinkOpener.getRecordedIntents().get(0).getData().toString());

        mAppSharedPreferences.setStripTrackingParamsEnabled(false);
        mLinkOpener.openLink(activity, TRACKING_URL);

        assertEquals("the flip must apply to the very next tap: raw url",
                TRACKING_URL, mLinkOpener.getRecordedIntents().get(1).getData().toString());
    }

    @Test
    public void routeUrlSpans_swapsEveryUrlSpanForCustomTabsUrlSpanKeepingBounds() {
        final int firstStart = 0;
        final int firstEnd = 5;
        final int secondStart = 6;
        final int secondEnd = 12;
        final int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        final SpannableString spannable = new SpannableString("first second");
        spannable.setSpan(new URLSpan("https://first.example.com"),
                firstStart, firstEnd, flags);
        spannable.setSpan(new URLSpan("https://second.example.com"),
                secondStart, secondEnd, flags);
        // a plain TextView with the default NORMAL buffer type COPIES the
        // setText input (TextUtils.stringOrSpannedString), so the spans must be
        // asserted on the TextView's STORED text - the SpannableStringBuilder
        // routeUrlSpans installs via BufferType.SPANNABLE - never on the
        // original local SpannableString, which stays untouched
        final AtomicReference<Spannable> storedTextRef = new AtomicReference<>();

        // views must be built on the main thread
        mScenario.onActivity(activity -> {
            TextView textView = new TextView(activity);
            textView.setText(spannable);
            mLinkOpener.routeUrlSpans(textView);
            storedTextRef.set((Spannable) textView.getText());
        });

        Spannable stored = storedTextRef.get();
        URLSpan[] urlSpans = stored.getSpans(0, stored.length(), URLSpan.class);
        assertEquals("span count must be unchanged", 2, urlSpans.length);
        URLSpan firstSpan = findSpanByUrl(urlSpans, "https://first.example.com");
        URLSpan secondSpan = findSpanByUrl(urlSpans, "https://second.example.com");
        assertNotNull(firstSpan);
        assertNotNull(secondSpan);
        assertTrue("the spans must be CustomTabsUrlSpan instances",
                firstSpan instanceof CustomTabsUrlSpan
                        && secondSpan instanceof CustomTabsUrlSpan);
        assertEquals(firstStart, stored.getSpanStart(firstSpan));
        assertEquals(firstEnd, stored.getSpanEnd(firstSpan));
        assertEquals(flags, stored.getSpanFlags(firstSpan));
        assertEquals(secondStart, stored.getSpanStart(secondSpan));
        assertEquals(secondEnd, stored.getSpanEnd(secondSpan));
        assertEquals(flags, stored.getSpanFlags(secondSpan));
    }

    private static URLSpan findSpanByUrl(URLSpan[] urlSpans, String url) {
        for (URLSpan urlSpan : urlSpans) {
            if (url.equals(urlSpan.getURL())) {
                return urlSpan;
            }
        }
        return null;
    }

    /**
     * Package-private test subclass replacing the launch boundary without a
     * mocking framework: {@code startActivity} is LinkOpener's seam, and the
     * override records the intent instead of launching it, so the tests can
     * assert exactly what a tap WOULD launch (including the click-time
     * sanitization and the open-links-mode decision).
     */
    private static class TestLinkOpener extends LinkOpener {

        private final List<Intent> mRecordedIntents = new ArrayList<>();

        TestLinkOpener(Provider provider) {
            super(provider);
        }

        List<Intent> getRecordedIntents() {
            return mRecordedIntents;
        }

        @Override
        void startActivity(Activity activity, Intent intent) {
            mRecordedIntents.add(intent);
        }
    }

    /**
     * Small module registering exactly what {@link LinkOpener} resolves:
     * the real {@link AppSharedPreferences} backed by the direct executor
     * (pref persistence runs inline on the calling thread, keeping writes
     * synchronous and deterministic) and the real pure-JVM {@link UrlSanitizer}.
     */
    private static class LinkOpenerTestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.registerAsync(AppSharedPreferences.class,
                    () -> new AppSharedPreferences(provider));
            providerRegistry.registerLazy(UrlSanitizer.class, UrlSanitizer::new);
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
