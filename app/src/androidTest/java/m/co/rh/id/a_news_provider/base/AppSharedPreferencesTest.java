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
import java.util.concurrent.Executors;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the AppSharedPreferences notification-permission state
 * (issue #51): asserts the fresh-install defaults, the set/get round-trip of both
 * new fields and that state persists through a fresh provider instance (re-read
 * from the shared preferences file, mirroring a process restart).
 */
@RunWith(AndroidJUnit4.class)
public class AppSharedPreferencesTest {

    private static final String SHARED_PREFERENCES_NAME = "RssSharedPreferences";

    private Context mContext;
    private Provider mProvider;
    private AppSharedPreferences mAppSharedPreferences;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // isolate: a previous test's executor may still hold a pending async commit
        // (setters persist asynchronously); wait for it to land, then clear again so
        // the stale write is wiped instead of leaking into this test's state
        SharedPreferences prefs = mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        Thread.sleep(300);
        prefs.edit().clear().commit();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
    }

    @After
    public void tearDown() throws Exception {
        if (mProvider != null) {
            mProvider.dispose();
        }
        // let this test's own async commits land before wiping the file, otherwise
        // they can leak into the next test's fresh provider
        Thread.sleep(300);
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
    public void testStateSurvivesNewProviderInstance() throws Exception {
        mAppSharedPreferences.setNotificationPermissionRequestCount(2);
        mAppSharedPreferences.setNotificationPermissionDeniedBefore(true);
        // the setter persists asynchronously on the executor thread, give it a moment
        // (generous for slow emulators)
        Thread.sleep(1000);
        mProvider.dispose();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        AppSharedPreferences reloaded = mProvider.get(AppSharedPreferences.class);
        assertEquals(2, reloaded.getNotificationPermissionRequestCount());
        assertTrue(reloaded.isNotificationPermissionDeniedBefore());
    }

    private static class PrefsTestProviderModule implements ProviderModule {
        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, Executors::newSingleThreadExecutor);
            // mirror the production registration in BaseProviderModule
            providerRegistry.registerAsync(AppSharedPreferences.class, () -> new AppSharedPreferences(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
