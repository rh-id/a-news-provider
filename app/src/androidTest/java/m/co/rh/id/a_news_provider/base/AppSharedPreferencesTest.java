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
 * (issue #51), the auto mark-read retention window (issue #47) and the four
 * reading-typography preferences (issue #29): asserts the fresh-install
 * defaults, the set/get round-trip of each new field, that every Flowable
 * replays its current value and emits on change, and that state persists through
 * a fresh provider instance (re-read from the shared preferences file, mirroring
 * a process restart).
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

    // ========================================================================
    // reading typography (issue #29)
    //
    // All four store an INDEX into the arrays on app/util/ReadingTypography
    // ({14,16,18,20,22,24}sp, three families and five spacing multipliers) rather
    // than a raw value, so the defaults asserted here must track
    // DEFAULT_FONT_SIZE_INDEX / DEFAULT_LINE_SPACING_INDEX there. :base cannot
    // reference :app, hence the literal 2 / 0 / 1 below.
    // ========================================================================

    @Test
    public void testReadingTypographyFreshInstallDefaults() {
        assertEquals("Font size must default to index 2 (the 18sp the layouts ship with)",
                Integer.valueOf(2), mAppSharedPreferences.getReadingFontSize());
        assertEquals("Font family must default to index 0 (the platform sans)",
                Integer.valueOf(0), mAppSharedPreferences.getReadingFontFamily());
        assertEquals("Line spacing must default to index 1 (a neutral 1.0 multiplier)",
                Integer.valueOf(1), mAppSharedPreferences.getReadingLineSpacing());
        assertFalse("Justification is opt-in and must default to off",
                Boolean.TRUE.equals(mAppSharedPreferences.isReadingJustification()));
    }

    @Test
    public void testReadingFontSizeRoundTripAcrossEveryIndex() {
        for (int index = 0; index < 6; index++) {
            mAppSharedPreferences.setReadingFontSize(index);
            assertEquals("index " + index + " must survive the round-trip",
                    Integer.valueOf(index), mAppSharedPreferences.getReadingFontSize());
        }
        // and back to the default, which is also the index the dialog pre-checks
        mAppSharedPreferences.setReadingFontSize(2);
        assertEquals(Integer.valueOf(2), mAppSharedPreferences.getReadingFontSize());
    }

    @Test
    public void testReadingFontFamilyRoundTripAcrossEveryIndex() {
        for (int index = 0; index < 3; index++) {
            mAppSharedPreferences.setReadingFontFamily(index);
            assertEquals("index " + index + " must survive the round-trip",
                    Integer.valueOf(index), mAppSharedPreferences.getReadingFontFamily());
        }
        mAppSharedPreferences.setReadingFontFamily(0);
        assertEquals(Integer.valueOf(0), mAppSharedPreferences.getReadingFontFamily());
    }

    @Test
    public void testReadingLineSpacingRoundTripAcrossEveryIndex() {
        for (int index = 0; index < 5; index++) {
            mAppSharedPreferences.setReadingLineSpacing(index);
            assertEquals("index " + index + " must survive the round-trip",
                    Integer.valueOf(index), mAppSharedPreferences.getReadingLineSpacing());
        }
        mAppSharedPreferences.setReadingLineSpacing(1);
        assertEquals(Integer.valueOf(1), mAppSharedPreferences.getReadingLineSpacing());
    }

    @Test
    public void testReadingJustificationRoundTrip() {
        mAppSharedPreferences.setReadingJustification(true);
        assertTrue(Boolean.TRUE.equals(mAppSharedPreferences.isReadingJustification()));
        mAppSharedPreferences.setReadingJustification(false);
        assertFalse(Boolean.TRUE.equals(mAppSharedPreferences.isReadingJustification()));
    }

    @Test
    public void testReadingTypographyFlowsEmitUpdates() {
        TestSubscriber<Integer> fontSize =
                mAppSharedPreferences.getReadingFontSizeFlow().test();
        TestSubscriber<Integer> fontFamily =
                mAppSharedPreferences.getReadingFontFamilyFlow().test();
        TestSubscriber<Integer> lineSpacing =
                mAppSharedPreferences.getReadingLineSpacingFlow().test();
        TestSubscriber<Boolean> justification =
                mAppSharedPreferences.getIsReadingJustificationFlow().test();

        // SerialBehaviorSubject replays the current value on subscribe
        assertEquals(Integer.valueOf(2), fontSize.values().get(0));
        assertEquals(Integer.valueOf(0), fontFamily.values().get(0));
        assertEquals(Integer.valueOf(1), lineSpacing.values().get(0));
        assertEquals(Boolean.FALSE, justification.values().get(0));

        mAppSharedPreferences.setReadingFontSize(5);
        mAppSharedPreferences.setReadingFontFamily(2);
        mAppSharedPreferences.setReadingLineSpacing(4);
        mAppSharedPreferences.setReadingJustification(true);

        fontSize.assertValueCount(2);
        assertEquals(Integer.valueOf(5), fontSize.values().get(1));
        fontFamily.assertValueCount(2);
        assertEquals(Integer.valueOf(2), fontFamily.values().get(1));
        lineSpacing.assertValueCount(2);
        assertEquals(Integer.valueOf(4), lineSpacing.values().get(1));
        justification.assertValueCount(2);
        assertEquals(Boolean.TRUE, justification.values().get(1));
    }

    @Test
    public void testReadingTypographySurvivesNewProviderInstance() {
        mAppSharedPreferences.setReadingFontSize(5);
        mAppSharedPreferences.setReadingFontFamily(1);
        mAppSharedPreferences.setReadingLineSpacing(4);
        mAppSharedPreferences.setReadingJustification(true);
        // the setters persist synchronously via the direct executor, so the writes
        // have landed before dispose() runs
        mProvider.dispose();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        AppSharedPreferences reloaded = mProvider.get(AppSharedPreferences.class);
        assertEquals(Integer.valueOf(5), reloaded.getReadingFontSize());
        assertEquals(Integer.valueOf(1), reloaded.getReadingFontFamily());
        assertEquals(Integer.valueOf(4), reloaded.getReadingLineSpacing());
        assertTrue(Boolean.TRUE.equals(reloaded.isReadingJustification()));
    }

    @Test
    public void testReadingTypographyFlowsReplayAfterNewProviderInstance() {
        mAppSharedPreferences.setReadingFontSize(3);
        mAppSharedPreferences.setReadingJustification(true);
        mProvider.dispose();
        mProvider = Provider.createProvider(mContext, new PrefsTestProviderModule());
        AppSharedPreferences reloaded = mProvider.get(AppSharedPreferences.class);
        // the whole point of initValue() emitting eagerly: a late subscriber must
        // get the stored value, never a null
        TestSubscriber<Integer> fontSize = reloaded.getReadingFontSizeFlow().test();
        TestSubscriber<Boolean> justification =
                reloaded.getIsReadingJustificationFlow().test();
        assertEquals(Integer.valueOf(3), fontSize.values().get(0));
        assertEquals(Boolean.TRUE, justification.values().get(0));
    }

    @Test
    public void testReadingTypographySettingsAreIndependent() {
        mAppSharedPreferences.setReadingFontSize(0);
        mAppSharedPreferences.setReadingLineSpacing(4);
        assertEquals("a font-size change must not disturb the line spacing",
                Integer.valueOf(4), mAppSharedPreferences.getReadingLineSpacing());
        mAppSharedPreferences.setReadingFontFamily(1);
        assertEquals("a family change must not disturb the font size",
                Integer.valueOf(0), mAppSharedPreferences.getReadingFontSize());
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
