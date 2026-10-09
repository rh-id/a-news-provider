package m.co.rh.id.a_news_provider.base;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the AppSharedPreferences notification-permission state
 * (issue #51) and the auto mark-read retention window (issue #47): asserts the
 * fresh-install defaults, the set/get round-trip of both new fields and that state
 * persists through a fresh provider instance (re-read from the shared preferences
 * file, mirroring a process restart).
 */
@RunWith(AndroidJUnit4.class)
public class AppSharedPreferencesTest {

    private static final String SHARED_PREFERENCES_NAME = "RssSharedPreferences";

    private Context mContext;
    private Provider mProvider;
    private AppSharedPreferences mAppSharedPreferences;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // defensive clear: with the direct executor this test's own writes are
        // synchronous, so the wipe only guards against a stale write from a
        // previously-run foreign test class
        SharedPreferences prefs = mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
    }

    @After
    public void tearDown() {
        if (mProvider != null) {
            mProvider.dispose();
        }
        // this test's own commits are synchronous now, so the wipe simply prevents
        // them from leaking into the next test
        mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    @Test
    public void testFreshInstallDefaults() {
        assertEquals(0, mAppSharedPreferences.getNotificationPermissionRequestCount());
        assertFalse(mAppSharedPreferences.isNotificationPermissionDeniedBefore());
    }

    @Test
    public void testRequestCountRoundTrip() {
        mAppSharedPreferences.setNotificationPermissionRequestCount(1);
        assertEquals(1, mAppSharedPreferences.getNotificationPermissionRequestCount());
        mAppSharedPreferences.setNotificationPermissionRequestCount(2);
        assertEquals(2, mAppSharedPreferences.getNotificationPermissionRequestCount());
    }

    @Test
    public void testDeniedBeforeRoundTrip() {
        mAppSharedPreferences.setNotificationPermissionDeniedBefore(true);
        assertTrue(mAppSharedPreferences.isNotificationPermissionDeniedBefore());
        mAppSharedPreferences.setNotificationPermissionDeniedBefore(false);
        assertFalse(mAppSharedPreferences.isNotificationPermissionDeniedBefore());
    }

    @Test
    public void testStateSurvivesNewProviderInstance() {
        mAppSharedPreferences.setNotificationPermissionRequestCount(2);
        mAppSharedPreferences.setNotificationPermissionDeniedBefore(true);
        // the setter persists synchronously via the direct executor, so the write
        // has landed before dispose() runs
        mProvider.dispose();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        AppSharedPreferences reloaded = mProvider.get(AppSharedPreferences.class);
        assertEquals(2, reloaded.getNotificationPermissionRequestCount());
        assertTrue(reloaded.isNotificationPermissionDeniedBefore());
    }

    @Test
    public void testAutoMarkReadDaysFreshInstallDefault() {
        assertEquals("Auto mark-read must default to off (0)",
                Integer.valueOf(0), mAppSharedPreferences.getAutoMarkReadDays());
    }

    @Test
    public void testAutoMarkReadDaysRoundTrip() {
        mAppSharedPreferences.setAutoMarkReadDays(7);
        assertEquals(Integer.valueOf(7), mAppSharedPreferences.getAutoMarkReadDays());
        mAppSharedPreferences.setAutoMarkReadDays(90);
        assertEquals(Integer.valueOf(90), mAppSharedPreferences.getAutoMarkReadDays());
        mAppSharedPreferences.setAutoMarkReadDays(0);
        assertEquals("Setting 0 must turn the feature back off",
                Integer.valueOf(0), mAppSharedPreferences.getAutoMarkReadDays());
    }

    @Test
    public void testAutoMarkReadDaysFlowEmitsUpdates() {
        TestSubscriber<Integer> testSubscriber =
                mAppSharedPreferences.getAutoMarkReadDaysFlow().test();

        // SerialBehaviorSubject replays the current value on subscribe
        assertEquals(Integer.valueOf(0), testSubscriber.values().get(0));

        mAppSharedPreferences.setAutoMarkReadDays(14);

        testSubscriber.assertValueCount(2);
        assertEquals(Integer.valueOf(14), testSubscriber.values().get(1));
    }

    @Test
    public void testAutoMarkReadDaysSurvivesNewProviderInstance() {
        mAppSharedPreferences.setAutoMarkReadDays(30);
        // the setter persists synchronously via the direct executor, so the write
        // has landed before dispose() runs
        mProvider.dispose();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        AppSharedPreferences reloaded = mProvider.get(AppSharedPreferences.class);
        assertEquals(Integer.valueOf(30), reloaded.getAutoMarkReadDays());
    }

    private static class PrefsTestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // direct executor: pref persistence runs inline on the calling thread,
            // keeping writes synchronous and deterministic in tests (still mirrors
            // the production ExecutorService registration in BaseProviderModule)
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
