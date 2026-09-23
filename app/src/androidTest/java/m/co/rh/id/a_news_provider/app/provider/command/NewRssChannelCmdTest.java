package m.co.rh.id.a_news_provider.app.provider.command;

import android.app.Application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.FakeWorkManager;
import m.co.rh.id.a_news_provider.test.NoOpLogger;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link NewRssChannelCmd} duplicate detection before
 * enqueueing the fetch worker, against a real Room database (no mocking framework -
 * Mockito is unreliable on ART). Absorbs the former JVM NewRssChannelCmdTest and the
 * former NewRssChannelCmdDuplicateTest / NewRssChannelCmdLegacyDuplicateTest.
 * <p>
 * Conversion notes (behavioral guarantees preserved):
 * <ul>
 *     <li>spy(validUrl) replaced by REAL validation - android.util.Patterns.WEB_URL
 *     works on-device. The validation subject only receives the empty "" success
 *     value on the added path, so duplicate scenarios assert "subject contains the
 *     duplicate message" instead of an exact single-value count.</li>
 *     <li>mock WorkManager replaced by {@link FakeWorkManager} registered BEFORE the
 *     wrapped test module, so the duplicate-skip keeps the first registration.</li>
 *     <li>mock RssDao replaced by the real per-test Room database (unique dbName).</li>
 *     <li>the redirect-duplicate probe runs inside execute() for genuinely new URLs -
 *     a {@link RedirectDuplicateChecker} with a RECORDING FAKE resolver (never touches
 *     the network) is registered BEFORE the wrapped test module, so it wins over the
 *     wrapped module's real (network-resolving) registration via setSkipSameType(true).</li>
 *     <li>DAO-error fail-closed: the duplicate-check failure is simulated by giving
 *     that test's provider a database name containing a path separator, which
 *     Android (API 26+) deterministically rejects when the database is OPENED -
 *     SQLiteOpenHelper throws the "File ... contains a path separator"
 *     IllegalArgumentException at open time, so the first DAO query (the duplicate
 *     check) fails. The tearDown deleteDatabase call does not throw for such a name
 *     (the suite stays green with it). execute() fails closed: the Single errors
 *     with the feed-add error message (also carried by the validation subject) and
 *     no worker is enqueued.</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class NewRssChannelCmdTest {
    private static final String LEGACY_RAW_URL = "https://CoolFeed.com/rss/";
    private static final String CANONICAL_URL = "https://coolfeed.com/rss";
    private static final String LEGACY_FEED_NAME = "Cool Feed";

    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private FakeWorkManager mFakeWorkManager;
    private RecordingRedirectResolver mRecordingResolver;
    private String mDbName;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    private void createProvider(String dbName) {
        mDbName = dbName;
        mFakeWorkManager = new FakeWorkManager();
        mRecordingResolver = new RecordingRedirectResolver();
        mTestProvider = Provider.createProvider(mTestApplication,
                new WorkManagerOverrideProviderModule(mTestApplication, dbName,
                        mFakeWorkManager, mRecordingResolver));
    }

    @After
    public void tearDown() {
        if (mTestProvider != null) {
            try {
                // close the Room instance before deleting its file so the delete
                // cannot race an open database handle
                mTestProvider.get(AppDatabase.class).close();
            } catch (Throwable ignored) {
                // database may never have been opened (e.g. the brokenDb test)
            }
            mTestProvider.dispose();
        }
        if (mDbName != null) {
            mTestApplication.deleteDatabase(mDbName);
        }
    }

    @Test
    public void testExecuteDuplicateFoundErrorsWithMessageAndDoesNotEnqueue() {
        createProvider("cmdDuplicateEndToEnd");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);

        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String expectedMessage = mTestApplication.getString(
                R.string.feed_already_added_as, "Example feed");
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute("https://example.com/feed")
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(throwable -> expectedMessage.equals(throwable.getMessage()));
        assertTrue("Validation subject must carry the duplicate message",
                validationSubscriber.values().contains(expectedMessage));
        assertEquals("Last validation value must be the duplicate message",
                expectedMessage, cmd.getValidationError());
        assertEquals("Channel count must stay at one", 1, rssDao.loadAllRssChannel().size());
        assertTrue("Duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
        assertTrue("Duplicate path must not run the redirect probe",
                mRecordingResolver.getCalls().isEmpty());
    }

    @Test
    public void testExecuteDifferentlySpelledCanonicalRowErrorsAsDuplicateAndDoesNotEnqueue() {
        // a canonical row found through the NORMALIZED lookup param of a differently
        // spelled input (host case, trailing slash) must hit the duplicate path
        createProvider("cmdNormalizedVariantDuplicate");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);

        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String expectedMessage = mTestApplication.getString(
                R.string.feed_already_added_as, "Example feed");
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute("https://EXAMPLE.com/feed/")
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(throwable -> expectedMessage.equals(throwable.getMessage()));
        assertTrue("Validation subject must carry the duplicate message",
                validationSubscriber.values().contains(expectedMessage));
        assertEquals("Normalized-variant duplicate must not add a row",
                1, rssDao.loadAllRssChannel().size());
        assertTrue("Duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testExecuteRedirectToExistingChannelErrorsAndDoesNotEnqueue() {
        // the requested URL is genuinely new (first variants lookup misses), but the
        // faked redirect chain ends at an already added channel - the probe must
        // report it and execute() must error inline without enqueueing
        createProvider("cmdRedirectDuplicate");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);
        mRecordingResolver.setResolution(url -> "https://example.com/feed");

        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String expectedMessage = mTestApplication.getString(
                R.string.error_feed_duplicate_redirect);
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute("https://old.example.com/feed")
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(throwable -> expectedMessage.equals(throwable.getMessage()));
        assertTrue("Validation subject must carry the redirect-duplicate message",
                validationSubscriber.values().contains(expectedMessage));
        assertEquals("Last validation value must be the redirect-duplicate message",
                expectedMessage, cmd.getValidationError());
        assertTrue("Redirect probe must have run for the genuinely new URL",
                mRecordingResolver.getCalls().contains("https://old.example.com/feed"));
        assertTrue("Redirect-duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
        assertEquals("Redirect-duplicate path must not add a row",
                1, rssDao.loadAllRssChannel().size());
    }

    @Test
    public void testExecuteNewUrlEmitsRequestUrlAndEnqueuesWorkerOnce() {
        createProvider("cmdNewUrlEnqueuesNormalized");
        // empty database - the feed is genuinely new
        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);

        TestObserver<String> observer = cmd.execute("example.com/newfeed/")
                .test().awaitDone(10, TimeUnit.SECONDS)
                .assertComplete();

        observer.assertValue("https://example.com/newfeed");
        assertEquals("Add path must enqueue exactly one fetch worker",
                1, mFakeWorkManager.getEnqueuedWorkRequests().size());
        assertTrue("Genuinely new URL must be probed for redirect duplicates",
                mRecordingResolver.getCalls().contains("https://example.com/newfeed"));
        // the Single's emitted request URL AND the enqueued worker's input data are
        // both asserted to be the NORMALIZED url, pinning the end-to-end spelling
        assertEquals("Enqueued worker must receive the NORMALIZED url as input",
                "https://example.com/newfeed",
                mFakeWorkManager.getEnqueuedWorkRequests().get(0).getWorkSpec().input
                        .getString(ConstantsKey.KEY_STRING_URL));
    }

    @Test
    public void testExecuteDaoCheckFailureErrorsAndDoesNotEnqueue() {
        // database name contains a path separator: on API 26+ Android deterministically
        // rejects such names when opening the database ("IllegalArgumentException: File
        // ... contains a path separator"), so the first DAO query (the duplicate check)
        // fails. execute() must fail closed: the Single errors with the feed-add error
        // message and the worker is NOT enqueued
        createProvider("cmdDaoError/brokenDb");
        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String expectedMessage = mTestApplication.getString(R.string.error_feed_add);
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute("https://example.com/feed")
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(throwable -> expectedMessage.equals(throwable.getMessage())
                && throwable.getCause() != null);
        assertTrue("Validation subject must carry the feed-add error message",
                validationSubscriber.values().contains(expectedMessage));
        assertEquals("Last validation value must be the feed-add error message",
                expectedMessage, cmd.getValidationError());
        assertTrue("DAO check failure must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
        // guards against the test passing vacuously: if a future platform silently
        // opened such a database name, the failure path would not have run and this file
        // check turns the test red. getDatabasePath(mDbName) itself throws the same
        // separator rejection on API 26+, so the file handle is built manually from
        // a valid probe name's parent directory.
        File brokenDbFile = new File(
                mTestApplication.getDatabasePath("probe").getParentFile(), mDbName);
        assertFalse("The rejected database name must never materialize as a file",
                brokenDbFile.exists());
    }

    @Test
    public void testExecuteInvalidUrlErrorsAndDoesNotEnqueue() {
        createProvider("cmdInvalidUrl");
        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String invalidUrlMessage = mTestApplication.getString(R.string.invalid_url);

        TestObserver<String> observer = cmd.execute("invalid url")
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(RuntimeException.class);
        assertEquals("Validation error must carry the invalid-url message",
                invalidUrlMessage, cmd.getValidationError());
        assertTrue("Invalid URL must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testExecuteLegacyRowIdenticalSpellingErrorsAndDoesNotEnqueue() {
        // legacy rows were stored with the scheme-prepended but un-normalized spelling;
        // re-adding the IDENTICAL spelling must hit the duplicate path via the raw param
        createProvider("legacyRowIdenticalSpelling");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        insertLegacyChannel(rssDao);
        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String expectedMessage = mTestApplication.getString(
                R.string.feed_already_added_as, LEGACY_FEED_NAME);
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute(LEGACY_RAW_URL)
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertError(throwable -> expectedMessage.equals(throwable.getMessage()));
        assertTrue("Validation subject must carry the duplicate message",
                validationSubscriber.values().contains(expectedMessage));
        assertEquals("Legacy row must not be duplicated", 1, rssDao.loadAllRssChannel().size());
        assertTrue("Duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testExecuteCanonicalSpellingOfLegacyRawRowIsNotFoundAndEnqueues() {
        // Pins the known residual gap: a legacy raw row that differs from the canonical
        // spelling (host case, trailing slash) cannot be matched by input-derived lookup
        // params, so the canonical spelling passes the command check and is treated as
        // new. Row-merging for that case is the responsibility of
        // RssRepository.persist during feed sync.
        createProvider("legacyRowCanonicalSpelling");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        insertLegacyChannel(rssDao);
        NewRssChannelCmd cmd = mTestProvider.get(NewRssChannelCmd.class);
        String duplicateMessage = mTestApplication.getString(
                R.string.feed_already_added_as, LEGACY_FEED_NAME);
        TestSubscriber<String> validationSubscriber = cmd.getUrlValidation().test();

        TestObserver<String> observer = cmd.execute(CANONICAL_URL)
                .test().awaitDone(10, TimeUnit.SECONDS);

        observer.assertComplete();
        observer.assertValue(CANONICAL_URL);
        assertFalse("Canonical spelling of a legacy raw row must NOT hit the command duplicate path",
                validationSubscriber.values().contains(duplicateMessage));
        assertEquals("Add path must enqueue exactly one fetch worker",
                1, mFakeWorkManager.getEnqueuedWorkRequests().size());
        assertTrue("The canonical spelling misses the first lookup, so the redirect probe must run",
                mRecordingResolver.getCalls().contains(CANONICAL_URL));
        assertEquals("No new row may appear in this test", 1, rssDao.loadAllRssChannel().size());
    }

    private void insertLegacyChannel(RssDao rssDao) {
        RssChannel legacyChannel = new RssChannel();
        legacyChannel.url = LEGACY_RAW_URL;
        legacyChannel.feedName = LEGACY_FEED_NAME;
        rssDao.insertRssChannel(legacyChannel);
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers the WorkManager
     * double plus a fake-resolver {@link RedirectDuplicateChecker} FIRST: DefaultProvider
     * throws on duplicate registrations, and with setSkipSameType(true) it keeps the
     * FIRST registration - so the doubles win and the wrapped module's real WorkManager
     * and real (network-resolving) checker registrations are skipped. The fake resolver
     * records every probe call and never touches the network.
     */
    private static class WorkManagerOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final WorkManager mWorkManager;
        private final RecordingRedirectResolver mRecordingResolver;

        WorkManagerOverrideProviderModule(Application application, String dbName,
                                          WorkManager workManager,
                                          RecordingRedirectResolver recordingResolver) {
            mApplication = application;
            mDbName = dbName;
            mWorkManager = workManager;
            mRecordingResolver = recordingResolver;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerLazy(WorkManager.class, () -> mWorkManager);
            providerRegistry.registerLazy(RedirectDuplicateChecker.class, () ->
                    new RedirectDuplicateChecker(mApplication,
                            provider.get(RssDao.class),
                            new UrlNormalizer(),
                            new NoOpLogger(),
                            mRecordingResolver));
            providerRegistry.setSkipSameType(true);
            providerRegistry.registerModule(new IntegrationTestAppProviderModule(
                    mApplication, mDbName));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }

    /**
     * Resolver double that records every probe URL so tests can assert whether the
     * redirect probe ran (and how often). Defaults to never redirecting
     * ({@code url -> null}); individual tests install their own resolution.
     */
    private static class RecordingRedirectResolver implements Function<String, String> {
        private final List<String> mCalls = new ArrayList<>();
        private Function<String, String> mResolution = url -> null;

        void setResolution(Function<String, String> resolution) {
            mResolution = resolution;
        }

        List<String> getCalls() {
            return mCalls;
        }

        @Override
        public String apply(String url) {
            mCalls.add(url);
            return mResolution.apply(url);
        }
    }
}
