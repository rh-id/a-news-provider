package m.co.rh.id.a_news_provider.app.provider.service;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import m.co.rh.id.a_news_provider.app.provider.command.RedirectDuplicateChecker;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link RssService}, against a real Room database (no
 * mocking framework - Mockito is unreliable on ART). The service API is
 * synchronous and the bulk add blocks on its futures, so the tests are plain
 * direct calls without any awaiting.
 * <p>
 * Module pattern from NewRssChannelCmdTest: a {@link FakeWorkManager} and a
 * fake-resolver {@link RedirectDuplicateChecker} (never touches the network) are
 * registered BEFORE the wrapped test module, so with setSkipSameType(true) the
 * doubles win over the wrapped module's real (network-resolving) registrations.
 * The DAO-error fail-closed behavior is simulated by giving that test's provider
 * a database name containing a path separator, which Android (API 26+)
 * deterministically rejects when the database is OPENED, so the first DAO query
 * (the duplicate check) fails.
 */
@RunWith(AndroidJUnit4.class)
public class RssServiceTest {
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
                new ServiceOverrideProviderModule(mTestApplication, dbName,
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
                // database may never have been opened (e.g. the brokenDb tests)
            }
            mTestProvider.dispose();
        }
        if (mDbName != null) {
            mTestApplication.deleteDatabase(mDbName);
        }
    }

    @Test
    public void testAddNewFeedExistingRowReturnsDuplicateAndDoesNotEnqueue() {
        createProvider("svcDuplicate");
        RssChannel existingChannel = insertExampleChannel();

        RssService rssService = mTestProvider.get(RssService.class);
        RssService.AddFeedResult result = rssService.addNewFeed("https://example.com/feed");

        assertEquals(RssService.AddFeedResult.Kind.DUPLICATE, result.kind);
        assertEquals("Duplicate must carry the already added channel",
                existingChannel.id, result.existing.id);
        assertEquals("Example feed", result.existing.feedName);
        assertTrue("Duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
        assertTrue("Duplicate path must not run the redirect probe",
                mRecordingResolver.getCalls().isEmpty());
    }

    @Test
    public void testAddNewFeedDifferentlySpelledRowReturnsDuplicate() {
        // a canonical row found through the NORMALIZED lookup param of a differently
        // spelled input (host case, trailing slash) must hit the duplicate path
        createProvider("svcNormalizedVariantDuplicate");
        insertExampleChannel();

        RssService rssService = mTestProvider.get(RssService.class);
        RssService.AddFeedResult result = rssService.addNewFeed("https://EXAMPLE.com/feed/");

        assertEquals(RssService.AddFeedResult.Kind.DUPLICATE, result.kind);
        assertEquals("Example feed", result.existing.feedName);
        assertTrue("Normalized-variant duplicate must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testAddNewFeedRedirectToExistingChannelReturnsDuplicateRedirect() {
        // the requested URL is genuinely new (first variants lookup misses), but the
        // faked redirect chain ends at an already added channel - the probe must
        // report it as a duplicate redirect without enqueueing
        createProvider("svcRedirectDuplicate");
        insertExampleChannel();
        mRecordingResolver.setResolution(url -> "https://example.com/feed");

        RssService rssService = mTestProvider.get(RssService.class);
        RssService.AddFeedResult result = rssService.addNewFeed("https://old.example.com/feed");

        assertEquals(RssService.AddFeedResult.Kind.DUPLICATE_REDIRECT, result.kind);
        assertTrue("The recording resolver must capture the requested url",
                mRecordingResolver.getCalls().contains("https://old.example.com/feed"));
        assertTrue("Redirect-duplicate path must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testAddNewFeedNewUrlReturnsAddedAndEnqueuesWorkerWithNormalizedUrl() {
        // empty database - the feed is genuinely new
        createProvider("svcNewUrlEnqueuesNormalized");
        RssService rssService = mTestProvider.get(RssService.class);

        RssService.AddFeedResult result = rssService.addNewFeed("example.com/newfeed/");

        assertEquals(RssService.AddFeedResult.Kind.ADDED, result.kind);
        assertEquals("https://example.com/newfeed", result.requestUrl);
        assertEquals("Add path must enqueue exactly one fetch worker",
                1, mFakeWorkManager.getEnqueuedWorkRequests().size());
        // the result's request URL AND the enqueued worker's input data are both
        // asserted to be the NORMALIZED url, pinning the end-to-end spelling
        assertEquals("Enqueued worker must receive the NORMALIZED url as input",
                "https://example.com/newfeed",
                mFakeWorkManager.getEnqueuedWorkRequests().get(0).getWorkSpec().input
                        .getString(ConstantsKey.KEY_STRING_URL));
    }

    @Test
    public void testAddNewFeedDaoCheckFailureReturnsDbErrorAndDoesNotEnqueue() {
        // database name contains a path separator: on API 26+ Android deterministically
        // rejects such names when opening the database ("IllegalArgumentException: File
        // ... contains a path separator"), so the first DAO query (the duplicate check)
        // fails. addNewFeed() must fail closed: DB_ERROR with the cause and the worker
        // is NOT enqueued
        createProvider("svcDaoError/brokenDb");
        RssService rssService = mTestProvider.get(RssService.class);

        RssService.AddFeedResult result = rssService.addNewFeed("https://example.com/feed");

        assertEquals(RssService.AddFeedResult.Kind.DB_ERROR, result.kind);
        assertNotNull("DB_ERROR must carry the failure cause", result.cause);
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
    public void testAddNewFeedInvalidUrlsReturnInvalidAndDoNotEnqueue() {
        createProvider("svcInvalidUrl");
        RssService rssService = mTestProvider.get(RssService.class);

        RssService.AddFeedResult invalidResult = rssService.addNewFeed("invalid url");
        assertEquals(RssService.AddFeedResult.Kind.INVALID, invalidResult.kind);
        assertEquals(RssService.UrlError.INVALID, invalidResult.urlError);

        RssService.AddFeedResult httpResult = rssService.addNewFeed("http://example.com/feed");
        assertEquals(RssService.AddFeedResult.Kind.INVALID, httpResult.kind);
        assertEquals(RssService.UrlError.HTTP_NOT_ALLOWED, httpResult.urlError);

        RssService.AddFeedResult emptyResult = rssService.addNewFeed(null);
        assertEquals(RssService.AddFeedResult.Kind.INVALID, emptyResult.kind);
        assertEquals(RssService.UrlError.EMPTY, emptyResult.urlError);

        assertTrue("Invalid URLs must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testCheckUrl() {
        createProvider("svcCheckUrl");
        RssService rssService = mTestProvider.get(RssService.class);

        assertEquals(RssService.UrlError.EMPTY, rssService.checkUrl(null));
        assertEquals(RssService.UrlError.EMPTY, rssService.checkUrl(""));
        assertEquals(RssService.UrlError.INVALID, rssService.checkUrl("invalid url"));
        // the pattern check precedes the http-scheme check, so a host-less http url
        // is rejected as INVALID before the scheme check is ever reached
        assertEquals(RssService.UrlError.INVALID, rssService.checkUrl("http://x"));
        assertEquals(RssService.UrlError.HTTP_NOT_ALLOWED,
                rssService.checkUrl("http://example.com/feed"));
        assertNull("A valid https url must pass the check",
                rssService.checkUrl("https://example.com/feed"));
    }

    @Test
    public void testPrependSchemeAddsHttpsOnlyWhenSchemeIsMissing() {
        assertEquals("https://example.com/feed",
                RssService.prependScheme("example.com/feed"));
        assertEquals("https://CoolFeed.com/rss/",
                RssService.prependScheme("https://CoolFeed.com/rss/"));
        assertEquals("http://example.com/feed",
                RssService.prependScheme("http://example.com/feed"));
        assertNull(RssService.prependScheme(null));
    }

    @Test
    public void testBuildRequestUrlPrependsSchemeAndNormalizes() {
        createProvider("svcBuildRequestUrl");
        RssService rssService = mTestProvider.get(RssService.class);

        assertEquals("https://example.com/feed",
                rssService.buildRequestUrl("example.com/feed/"));
        assertEquals("https://example.com/feed",
                rssService.buildRequestUrl("https://EXAMPLE.com/feed/"));
        assertNull(rssService.buildRequestUrl(null));
    }

    @Test
    public void testAddNewFeedsMixedListResultsInOrderAndQueuesOnlyTheFreshFeed() {
        createProvider("svcBulkMixed");
        insertExampleChannel();

        RssService rssService = mTestProvider.get(RssService.class);
        List<RssService.AddFeedResult> results = rssService.addNewFeeds(Arrays.asList(
                "https://example.com/feed", "https://example.com/feed",
                "https://fresh.example.com/rss"));

        assertEquals("One result per input url, same order", 3, results.size());
        assertEquals(RssService.AddFeedResult.Kind.DUPLICATE, results.get(0).kind);
        assertEquals(RssService.AddFeedResult.Kind.DUPLICATE_IN_FILE, results.get(1).kind);
        assertEquals(RssService.AddFeedResult.Kind.ADDED, results.get(2).kind);
        assertEquals("https://fresh.example.com/rss", results.get(2).requestUrl);
        assertEquals("Exactly the fresh feed must be enqueued",
                1, mFakeWorkManager.getEnqueuedWorkRequests().size());
        assertEquals("https://fresh.example.com/rss",
                mFakeWorkManager.getEnqueuedWorkRequests().get(0).getWorkSpec().input
                        .getString(ConstantsKey.KEY_STRING_URL));
    }

    @Test
    public void testAddNewFeedsAllDatabaseDuplicatesReturnDuplicateAndDoNotEnqueue() {
        createProvider("svcBulkAllDbDuplicates");
        insertChannel("https://one.example.com/rss", "One");
        insertChannel("https://two.example.com/rss", "Two");
        insertChannel("https://three.example.com/rss", "Three");

        RssService rssService = mTestProvider.get(RssService.class);
        List<RssService.AddFeedResult> results = rssService.addNewFeeds(Arrays.asList(
                "https://one.example.com/rss", "https://two.example.com/rss",
                "https://three.example.com/rss"));

        assertEquals(3, results.size());
        for (RssService.AddFeedResult result : results) {
            assertEquals("Every url is already in the database",
                    RssService.AddFeedResult.Kind.DUPLICATE, result.kind);
        }
        assertTrue("Database duplicates must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testAddNewFeedsDaoFailureReturnsDbErrorForEachUrlAndDoesNotEnqueue() {
        // brokenDb trick from testAddNewFeedDaoCheckFailureReturnsDbErrorAndDoesNotEnqueue -
        // the bulk add must fail closed PER URL instead of throwing
        createProvider("svcBulkDaoError/brokenDb");
        RssService rssService = mTestProvider.get(RssService.class);

        List<RssService.AddFeedResult> results = rssService.addNewFeeds(Arrays.asList(
                "https://example.com/feed", "https://other.example.com/rss",
                "https://third.example.com/rss"));

        assertEquals(3, results.size());
        for (RssService.AddFeedResult result : results) {
            assertEquals("Each url must fail closed individually",
                    RssService.AddFeedResult.Kind.DB_ERROR, result.kind);
            assertNotNull("DB_ERROR must carry the failure cause", result.cause);
        }
        assertTrue("DAO failures must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    @Test
    public void testAddNewFeedsInvalidEntryReturnsInvalidAndDoesNotEnqueueIt() {
        createProvider("svcBulkInvalidEntry");
        RssService rssService = mTestProvider.get(RssService.class);

        List<RssService.AddFeedResult> results = rssService.addNewFeeds(Arrays.asList(
                "invalid url", "https://fresh.example.com/rss"));

        assertEquals(2, results.size());
        assertEquals(RssService.AddFeedResult.Kind.INVALID, results.get(0).kind);
        assertEquals(RssService.UrlError.INVALID, results.get(0).urlError);
        assertEquals(RssService.AddFeedResult.Kind.ADDED, results.get(1).kind);
        assertEquals("The invalid entry must not be enqueued, the valid one must",
                1, mFakeWorkManager.getEnqueuedWorkRequests().size());
        assertEquals("https://fresh.example.com/rss",
                mFakeWorkManager.getEnqueuedWorkRequests().get(0).getWorkSpec().input
                        .getString(ConstantsKey.KEY_STRING_URL));
    }

    @Test
    public void testAddNewFeedsRedirectToExistingReturnsDuplicateRedirectAndProbesEachNewUrl() {
        createProvider("svcBulkRedirect");
        insertExampleChannel();
        mRecordingResolver.setResolution(url -> "https://example.com/feed");

        RssService rssService = mTestProvider.get(RssService.class);
        List<RssService.AddFeedResult> results = rssService.addNewFeeds(Arrays.asList(
                "https://new.example.com/rss", "https://other.example.com/rss"));

        assertEquals(2, results.size());
        for (RssService.AddFeedResult result : results) {
            assertEquals("Both genuinely new urls redirect to an added channel",
                    RssService.AddFeedResult.Kind.DUPLICATE_REDIRECT, result.kind);
        }
        assertEquals("The probe must run once per genuinely new url",
                2, mRecordingResolver.getCalls().size());
        assertTrue(mRecordingResolver.getCalls().contains("https://new.example.com/rss"));
        assertTrue(mRecordingResolver.getCalls().contains("https://other.example.com/rss"));
        assertTrue("Redirect-duplicates must not enqueue the fetch worker",
                mFakeWorkManager.getEnqueuedWorkRequests().isEmpty());
    }

    private RssChannel insertExampleChannel() {
        return insertChannel("https://example.com/feed", "Example feed");
    }

    private RssChannel insertChannel(String url, String feedName) {
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = url;
        existingChannel.feedName = feedName;
        rssDao.insertRssChannel(existingChannel);
        return existingChannel;
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers the WorkManager
     * double plus a fake-resolver {@link RedirectDuplicateChecker} FIRST: DefaultProvider
     * throws on duplicate registrations, and with setSkipSameType(true) it keeps the
     * FIRST registration - so the doubles win and the wrapped module's real WorkManager
     * and real (network-resolving) checker registrations are skipped. The fake resolver
     * records every probe call and never touches the network.
     */
    private static class ServiceOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final WorkManager mWorkManager;
        private final RecordingRedirectResolver mRecordingResolver;

        ServiceOverrideProviderModule(Application application, String dbName,
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
     * ({@code url -> null}); individual tests install their own resolution. The
     * call list is thread-safe - the bulk add probes genuinely new urls on
     * concurrent executor threads.
     */
    private static class RecordingRedirectResolver implements Function<String, String> {
        private final List<String> mCalls = Collections.synchronizedList(new ArrayList<>());
        private Function<String, String> mResolution = url -> null;

        void setResolution(Function<String, String> resolution) {
            mResolution = resolution;
        }

        List<String> getCalls() {
            // snapshot so callers never iterate the live list while probe tasks append to it
            return new ArrayList<>(mCalls);
        }

        @Override
        public String apply(String url) {
            mCalls.add(url);
            return mResolution.apply(url);
        }
    }
}
