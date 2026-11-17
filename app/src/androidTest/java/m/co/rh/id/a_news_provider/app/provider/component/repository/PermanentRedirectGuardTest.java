package m.co.rh.id.a_news_provider.app.provider.component.repository;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.base.provider.component.AppDatabase;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the shared permanent-redirect duplicate guard (issue #50)
 * consumed by BOTH persist call sites (RssSyncWorker and NewRssWorker), against a
 * real in-memory Room database (no mocking framework - Mockito is unreliable on ART).
 * Formerly a JVM test (src/test) that mocked RssDao/ILogger with the injected test
 * constructor; it moved here and was re-wired because the guard now keeps ONLY its
 * production (Provider) constructor - every scenario resolves the guard through a
 * test ProviderModule whose RssDao is the real in-memory database and whose
 * {@link UrlNormalizer} is real.
 * <p>
 * Covers: the no-outcome and temporary-outcome no-ops, the surviving outcome with
 * its normalized url, the same-channel allowance on the sync path, the
 * any-holder-is-a-collision rule on the add path, and production constructor
 * wiring. Conversion notes (behavioral guarantees preserved):
 * <ul>
 *     <li>mocked RssDao replaced by REAL seeded rows: scenarios that mocked a
 *     lookup result now INSERT the holder channel under its NORMALIZED url
 *     spelling (as persist would have written it), so the mock-level raw/normalized
 *     param-order verification is preserved behaviorally - the lookup only finds
 *     the row when the guard sends the variant pair the production code sends.</li>
 *     <li>mocked ILogger replaced by a hand-rolled recording logger ({@code
 *     RecordingLogger}), so the collision test's exact debug-literal assertion is
 *     KEPT, not downgraded.</li>
 *     <li>the two no-op tests' mock-only "never queries DAO" verification is
 *     DOWNGRADED: a real DAO cannot be query-spied here. The observable no-op
 *     contract (decision false, model fields untouched) is asserted fully.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class PermanentRedirectGuardTest {
    // UrlNormalizer lowercases the host and strips the trailing slash
    private static final String RAW_FINAL_URL = "https://Moved.example.com/feed/";
    private static final String NORMALIZED_FINAL_URL = "https://moved.example.com/feed";

    private AppDatabase mAppDatabase;
    private RssDao mRssDao;
    private Provider mProvider;
    private PermanentRedirectGuard mGuard;
    private RecordingLogger mRecordingLogger;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mAppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        mRssDao = mAppDatabase.rssDao();
        mRecordingLogger = new RecordingLogger();
        mProvider = Provider.createProvider(context,
                new GuardTestProviderModule(mAppDatabase, mRssDao, mRecordingLogger));
        mGuard = mProvider.get(PermanentRedirectGuard.class);
    }

    @After
    public void tearDown() {
        if (mProvider != null) {
            mProvider.dispose();
        }
        if (mAppDatabase != null) {
            mAppDatabase.close();
        }
    }

    private static RssModel modelWithOutcome(String finalUrl, boolean permanent) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = "https://original.example.com/rss";
        rssChannel.feedName = "Original";
        RssModel rssModel = new RssModel(rssChannel, new ArrayList<>());
        rssModel.setFinalUrl(finalUrl);
        rssModel.setPermanentRedirect(permanent);
        return rssModel;
    }

    /**
     * Seeds a channel row under the given url and returns the STORED row (with its
     * Room-generated id) - the "holder" the guard's by-url lookup finds.
     */
    private RssChannel insertHolderChannel(String url) {
        RssChannel holder = new RssChannel();
        holder.url = url;
        holder.feedName = "Holder feed";
        mRssDao.insertRssChannel(holder);
        assertNotNull("Seeded holder row must carry its generated id", holder.id);
        return holder;
    }

    @Test
    public void noOutcomeReturnsFalseAndNeverQueriesDao() {
        RssModel rssModel = modelWithOutcome(null, false);

        boolean applied = mGuard.applyGuard(new RssChannel(), rssModel);

        assertFalse("no outcome must be a no-op", applied);
        assertNull(rssModel.getFinalUrl());
        assertFalse(rssModel.isPermanentRedirect());
        // the former JVM test verified the mock DAO was never queried; a real DAO
        // cannot be query-spied, so the observable no-op contract above stands in
    }

    @Test
    public void temporaryOutcomeReturnsFalseAndNeverQueriesDao() {
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, false);

        boolean applied = mGuard.applyGuard(new RssChannel(), rssModel);

        assertFalse("a temporary last hop must never rewrite", applied);
        assertEquals("the outcome fields stay untouched on the temporary path "
                        + "(no normalization, no lookup)",
                RAW_FINAL_URL, rssModel.getFinalUrl());
        assertFalse(rssModel.isPermanentRedirect());
        // same query-spy downgrade as noOutcomeReturnsFalseAndNeverQueriesDao
    }

    @Test
    public void permanentOutcomeSurvivesWhenTargetNotAdded() {
        // empty database - the redirect target is not added
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);
        RssChannel syncedChannel = new RssChannel();
        syncedChannel.id = 1L;

        boolean applied = mGuard.applyGuard(syncedChannel, rssModel);

        assertTrue("a free redirect target must survive the guard", applied);
        assertEquals("the model must carry the NORMALIZED final url for persist",
                NORMALIZED_FINAL_URL, rssModel.getFinalUrl());
        assertTrue(rssModel.isPermanentRedirect());
    }

    @Test
    public void holderEqualToFetchedChannelIsNotACollision() {
        // the synced channel IS the row found under the redirect target url:
        // the lookup finds it because the holder row is seeded under the
        // NORMALIZED spelling the guard passes as its second variant
        RssChannel holder = insertHolderChannel(NORMALIZED_FINAL_URL);
        RssChannel syncedChannel = new RssChannel();
        syncedChannel.id = holder.id;
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);

        boolean applied = mGuard.applyGuard(syncedChannel, rssModel);

        assertTrue("a holder equal to the synced channel (normalization-only "
                + "difference) must not count as a collision", applied);
        assertEquals(NORMALIZED_FINAL_URL, rssModel.getFinalUrl());
    }

    @Test
    public void collisionWithDifferentChannelClearsOutcomeAndLogs() {
        // the redirect target is owned by a DIFFERENT channel row
        RssChannel holder = insertHolderChannel(NORMALIZED_FINAL_URL);
        RssChannel otherChannel = insertHolderChannel("https://other.example.com/rss");
        RssChannel syncedChannel = new RssChannel();
        syncedChannel.id = otherChannel.id;
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);

        boolean applied = mGuard.applyGuard(syncedChannel, rssModel);

        assertFalse("a redirect onto another feed must clear the outcome", applied);
        assertNull("persist must not see a final url after a collision",
                rssModel.getFinalUrl());
        assertFalse(rssModel.isPermanentRedirect());
        // the debug log carries WHICH feed collided: a plain literal (a .d()
        // never toasts, so no string resource) naming the feed in question -
        // the exact-literal assertion from the former JVM test is KEPT via the
        // recording logger
        assertEquals(1, mRecordingLogger.debugMessages.size());
        assertEquals("Permanent redirect outcome skipped for feed \"Original\": "
                        + "redirect target is already another feed's url",
                mRecordingLogger.debugMessages.get(0));
        assertEquals(PermanentRedirectGuard.class.getName(),
                mRecordingLogger.debugTags.get(0));
    }

    @Test
    public void addPathAnyHolderIsACollision() {
        // the add path passes NO existing channel: every holder is someone
        // else's feed, so the outcome must be cleared
        insertHolderChannel(NORMALIZED_FINAL_URL);
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);

        boolean applied = mGuard.applyGuard(null, rssModel);

        assertFalse("the add path must not adopt an owned url", applied);
        assertNull(rssModel.getFinalUrl());
        assertFalse(rssModel.isPermanentRedirect());
        assertEquals("the collision must be logged", 1,
                mRecordingLogger.debugMessages.size());
    }

    @Test
    public void addPathWithoutHolderKeepsOutcome() {
        // empty database - the redirect target is free
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);

        boolean applied = mGuard.applyGuard(null, rssModel);

        assertTrue("a free target on the add path must keep the outcome", applied);
        assertEquals(NORMALIZED_FINAL_URL, rssModel.getFinalUrl());
    }

    @Test
    public void productionConstructorResolvesDependenciesFromProvider() {
        // every scenario here already wires the guard through the production
        // (Provider) constructor via the test module; this pins the constructor
        // DIRECTLY (bypassing the registry) so a wiring regression in the ctor's
        // provider resolutions (RssDao/UrlNormalizer/ILogger) turns red
        PermanentRedirectGuard directGuard = new PermanentRedirectGuard(mProvider);
        RssModel rssModel = modelWithOutcome(RAW_FINAL_URL, true);

        boolean applied = directGuard.applyGuard(null, rssModel);

        assertTrue(applied);
        assertEquals(NORMALIZED_FINAL_URL, rssModel.getFinalUrl());
    }

    /**
     * Hand-rolled recording {@link ILogger} double for instrumented tests
     * (Mockito is unreliable on ART, so fakes are implemented directly - same
     * pattern as {@code NoOpLogger}, plus debug-message recording so the guard's
     * developer-facing collision log stays assertable without a mocking
     * framework).
     */
    private static class RecordingLogger implements ILogger {
        private final List<String> debugTags =
                Collections.synchronizedList(new ArrayList<>());
        private final List<String> debugMessages =
                Collections.synchronizedList(new ArrayList<>());

        @Override
        public void setLogLevel(int level) {
            // no-op
        }

        @Override
        public void v(String tag, String message) {
            // no-op
        }

        @Override
        public void v(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void d(String tag, String message) {
            debugTags.add(tag);
            debugMessages.add(message);
        }

        @Override
        public void d(String tag, String message, Throwable throwable) {
            debugTags.add(tag);
            debugMessages.add(message);
        }

        @Override
        public void i(String tag, String message) {
            // no-op
        }

        @Override
        public void i(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void w(String tag, String message) {
            // no-op
        }

        @Override
        public void w(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void e(String tag, String message) {
            // no-op
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
            // no-op
        }
    }

    /**
     * Test module in the {@code RssRepositoryTest} style: registers the REAL
     * in-memory Room database and its DAO, the recording logger, a real
     * {@link UrlNormalizer} and the guard through its production (Provider)
     * constructor - no test-only constructor exists anymore.
     */
    private static class GuardTestProviderModule implements ProviderModule {
        private final AppDatabase mAppDatabase;
        private final RssDao mRssDao;
        private final RecordingLogger mRecordingLogger;

        GuardTestProviderModule(AppDatabase appDatabase, RssDao rssDao,
                                RecordingLogger recordingLogger) {
            mAppDatabase = appDatabase;
            mRssDao = rssDao;
            mRecordingLogger = recordingLogger;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerAsync(AppDatabase.class, () -> mAppDatabase);
            providerRegistry.registerAsync(RssDao.class, () -> mRssDao);
            providerRegistry.registerAsync(ILogger.class, () -> mRecordingLogger);
            providerRegistry.registerLazy(UrlNormalizer.class, UrlNormalizer::new);
            providerRegistry.registerLazy(PermanentRedirectGuard.class,
                    () -> new PermanentRedirectGuard(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
