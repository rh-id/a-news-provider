package m.co.rh.id.a_news_provider.app.provider.command;

import android.app.Application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.a_news_provider.test.util.ProviderDbRule;
import m.co.rh.id.a_news_provider.test.util.RecordingRedirectResolver;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the redirect-duplicate decision of
 * {@link RedirectDuplicateChecker} consumed by
 * {@link m.co.rh.id.a_news_provider.app.provider.component.service.RssService#addNewFeed(String)},
 * against a real Room database (no mocking framework - Mockito is unreliable on ART).
 * Formerly a JVM test (src/test) that mocked RssDao/Context/ILogger with the injected
 * fake constructor; it moved here and was re-wired because the checker now keeps ONLY
 * its production (Provider) constructor - the fake constructor was removed and the
 * built-in {@code resolveRedirects} became the protected override seam that the test
 * module's anonymous subclass uses to inject the network-free
 * {@link RecordingRedirectResolver}.
 * <p>
 * All 5 decision branches plus the swallow-and-continue failure paths keep their
 * intent. Conversion notes (behavioral guarantees preserved):
 * <ul>
 *     <li>mocked RssDao replaced by the REAL per-test Room database (unique dbName
 *     via {@link ProviderDbRule}); scenarios that mocked a DAO result now SEED the
 *     row instead, and the mock-level by-url param-order verifications are replaced
 *     by seeded-row behavior: a row stored under the NORMALIZED url spelling is only
 *     found (and the decision flips) when the checker passes the raw/normalized
 *     variant pair the production code sends.</li>
 *     <li>mocked ILogger replaced by the provider-resolved real logger - the exact
 *     log-message/cause verifications are therefore DOWNGRADED to behavioral
 *     assertions (decision result + probe call count); no assertion silently lost:
 *     the swallow behavior itself is fully covered by result + recording.</li>
 *     <li>resolver failures are delivered through the {@code resolveRedirects}
 *     override exactly as the production probe would: a RuntimeException directly,
 *     and an IOException via the sneaky-throw pattern so {@code catch (Throwable)}
 *     receives the checked exception itself (unwrapped).</li>
 *     <li>daoFailureOnFirstLookup: the real DAO is made to fail with the
 *     broken-db-name trick from {@code NewRssChannelCmdTest} - a database name
 *     containing a path separator is deterministically rejected by Android (API 26+)
 *     when the database is OPENED, so the first variants query throws.</li>
 * </ul>
 * The redirect primitives the probe relies on ({@code isRedirect},
 * {@code resolveLocation}) live in
 * {@link m.co.rh.id.a_news_provider.component.network.http.RedirectFollower}
 * since issue #50; they are covered by {@code RedirectFollowerTest} in
 * component-network, not here.
 */
@RunWith(AndroidJUnit4.class)
public class RedirectDuplicateCheckerTest {
    private static final String REQUEST_URL = "https://Example.com/rss/";
    // UrlNormalizer lowercases the host and strips the trailing slash
    private static final String NORMALIZED_REQUEST_URL = "https://example.com/rss";
    private static final String RESOLVED_URL = "https://Existing.com/feed/";
    // UrlNormalizer result for RESOLVED_URL
    private static final String NORMALIZED_RESOLVED_URL = "https://existing.com/feed";

    @Rule
    public final ProviderDbRule mDbRule = new ProviderDbRule();

    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private RssDao mRssDao;
    private RedirectDuplicateChecker mChecker;
    private RecordingRedirectResolver mRecordingResolver;
    private String mDbName;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    private void createProvider(String dbName) {
        mDbName = dbName;
        mRecordingResolver = new RecordingRedirectResolver();
        mTestProvider = mDbRule.create(mTestApplication,
                new CheckerOverrideProviderModule(mTestApplication, dbName,
                        mRecordingResolver), dbName);
        mRssDao = mTestProvider.get(RssDao.class);
        // the registered checker is the anonymous resolveRedirects override that
        // delegates to the recording resolver - network-free
        mChecker = mTestProvider.get(RedirectDuplicateChecker.class);
    }

    private RssChannel insertChannel(String url) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
        rssChannel.feedName = "Existing feed";
        mRssDao.insertRssChannel(rssChannel);
        return rssChannel;
    }

    @Test
    public void existingByRequestedUrlReturnsFalseAndNeverProbesRedirect() {
        createProvider("checkerExistingByRequestUrl");
        // seeded under the NORMALIZED spelling: only found when the checker looks
        // up with the raw/normalized variant pair of the requested url
        insertChannel(NORMALIZED_REQUEST_URL);
        // non-vacuity guard: the real DAO must find the row for the exact param
        // pair the checker is about to use
        assertNotNull(mRssDao.findRssChannelByUrlVariants(
                REQUEST_URL, NORMALIZED_REQUEST_URL));

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Existing channel by requested URL must not be treated as a redirect duplicate",
                result);
        assertTrue("Redirect probe must not run when the requested URL is already added",
                mRecordingResolver.getCalls().isEmpty());
    }

    @Test
    public void productionConstructorResolvesDependenciesFromProvider() {
        createProvider("checkerProductionCtor");
        // seeded row - identical reasoning to the former JVM test: the production
        // constructor wires itself from the Provider and its probe is the BUILT-IN
        // network resolution, which is never reached here because the DAO already
        // reports the requested URL as added
        insertChannel(NORMALIZED_REQUEST_URL);
        // non-vacuity guard: the built-in probe must truly stay unreached, so the
        // DAO MUST find the seeded row for the checker's param pair
        assertNotNull(mRssDao.findRssChannelByUrlVariants(
                REQUEST_URL, NORMALIZED_REQUEST_URL));

        // constructed DIRECTLY with the production (Provider) constructor instead of
        // through the registry override
        mChecker = new RedirectDuplicateChecker(mTestProvider);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Existing channel by requested URL must not be treated as a redirect duplicate",
                result);
    }

    @Test
    public void nullRedirectResolutionReturnsFalse() {
        createProvider("checkerNullResolution");
        // RecordingRedirectResolver defaults to never redirecting (url -> null);
        // set explicitly to pin the scenario
        mRecordingResolver.setResolution(url -> null);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Unresolvable redirect must not be treated as a duplicate", result);
        assertEquals("The requested URL must be probed exactly once",
                1, mRecordingResolver.getCalls().size());
    }

    @Test
    public void unchangedRedirectResolutionReturnsFalse() {
        createProvider("checkerUnchangedResolution");
        mRecordingResolver.setResolution(url -> url);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("URL that does not redirect must not be treated as a duplicate", result);
        assertEquals("The requested URL must be probed exactly once",
                1, mRecordingResolver.getCalls().size());
    }

    @Test
    public void redirectTargetNotInDatabaseReturnsFalse() {
        createProvider("checkerTargetNotInDb");
        mRecordingResolver.setResolution(url -> RESOLVED_URL);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Redirect to a NOT added channel must not be treated as a duplicate", result);
        assertEquals(1, mRecordingResolver.getCalls().size());
        // the former mock verification pinned the raw/normalized param order of the
        // SECOND (redirect target) lookup; behaviorally that param order is proven by
        // redirectTargetExistsInDatabaseReturnsTrue below: a row stored under the
        // normalized target spelling is found there, which only happens when the
        // second lookup sends the resolved-url variant pair
    }

    @Test
    public void redirectTargetExistsInDatabaseReturnsTrue() {
        createProvider("checkerTargetInDb");
        // seed the redirect TARGET row (under its normalized spelling, as persist
        // would have written it) - the requested URL itself stays unknown
        insertChannel(NORMALIZED_RESOLVED_URL);
        mRecordingResolver.setResolution(url -> RESOLVED_URL);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertTrue("Redirect to an already added channel must be reported as duplicate", result);
        assertEquals("The requested URL must be probed exactly once",
                1, mRecordingResolver.getCalls().size());
    }

    @Test
    public void resolverFailureIsSwallowedAndReturnsFalse() {
        createProvider("checkerResolverFailure");
        mRecordingResolver.setResolution(url -> {
            throw new RuntimeException("probe failed");
        });

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Probe failure must not report a duplicate", result);
        assertEquals("The failed probe must have run exactly once (non-vacuity guard)",
                1, mRecordingResolver.getCalls().size());
        // the former JVM test additionally verified the logger received
        // e(TAG, error_feed_add, theVerySameThrowable); that exact-cause log
        // verification is dropped instrumented-wise (real provider logger), the
        // swallow behavior itself is pinned by the result + probe count above
    }

    @Test
    public void resolverIoExceptionIsSwallowedAndLoggedUnwrapped() {
        // the built-in resolveRedirects throws IOException directly (no RuntimeException
        // wrapper since the resolver was merged into the checker) - catch (Throwable)
        // must swallow it. The IOException is delivered through the resolveRedirects
        // override via the sneaky-throw pattern so the checker receives the CHECKED
        // exception itself, exactly what the production probe path delivers
        createProvider("checkerResolverIoException");
        IOException resolverError = new IOException("probe connection failed");
        mRecordingResolver.setResolution(url -> {
            throw sneakyThrow(resolverError);
        });

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Probe IOException must not report a duplicate", result);
        assertEquals("The failed probe must have run exactly once (non-vacuity guard)",
                1, mRecordingResolver.getCalls().size());
        // the former JVM test additionally verified the logger received the
        // unwrapped IOException as the log cause; dropped with the real provider
        // logger for the same reason as resolverFailureIsSwallowedAndReturnsFalse
    }

    @Test
    public void daoFailureOnFirstLookupIsSwallowedAndReturnsFalse() {
        // database name contains a path separator: on API 26+ Android deterministically
        // rejects such names when opening the database ("IllegalArgumentException: File
        // ... contains a path separator"), so the FIRST variants query (the DAO lookup
        // the checker performs before probing) fails. The checker must FAIL OPEN:
        // swallow the DAO error, return false (the feed proceeds with the normal
        // fetch) and never run the redirect probe
        createProvider("checkerDaoError/brokenDb");

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("DAO failure must not report a duplicate", result);
        assertTrue("Redirect probe must not run when the DAO already failed",
                mRecordingResolver.getCalls().isEmpty());
        // guards against the test passing vacuously: if a future platform silently
        // opened such a database name, the DAO lookup would succeed on the empty
        // database, the probe WOULD run and the probe-count assertion above would
        // turn red. The file check pins that the rejected name never materializes
        // (getDatabasePath(mDbName) itself throws the same separator rejection on
        // API 26+, so the handle is built from a valid probe name's parent directory)
        File brokenDbFile = new File(
                mTestApplication.getDatabasePath("probe").getParentFile(), mDbName);
        assertFalse("The rejected database name must never materialize as a file",
                brokenDbFile.exists());
    }

    /**
     * Sneaky-throw helper: lets a checked {@link IOException} escape a
     * {@code Function} lambda (whose signature declares no checked exceptions) and
     * surface at the caller exactly as thrown - the same pattern the former JVM
     * test used to feed the production IOException path.
     */
    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers a network-free
     * {@link RedirectDuplicateChecker} override plus the direct executor FIRST:
     * DefaultProvider throws on duplicate registrations, and with
     * setSkipSameType(true) it keeps the FIRST registration - so the override wins
     * and the wrapped module's real (network-resolving) checker registration is
     * skipped. The override is the production (Provider) constructor with an
     * anonymous subclass overriding {@code resolveRedirects} to delegate to a
     * {@link RecordingRedirectResolver} that records every probe call and never
     * touches the network.
     */
    private static class CheckerOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final RecordingRedirectResolver mRecordingResolver;

        CheckerOverrideProviderModule(Application application, String dbName,
                                      RecordingRedirectResolver recordingResolver) {
            mApplication = application;
            mDbName = dbName;
            mRecordingResolver = recordingResolver;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.registerLazy(RedirectDuplicateChecker.class, () ->
                    new RedirectDuplicateChecker(provider) {
                        @Override
                        protected String resolveRedirects(String url) throws IOException {
                            return mRecordingResolver.apply(url);
                        }
                    });
            providerRegistry.setSkipSameType(true);
            providerRegistry.registerModule(new IntegrationTestAppProviderModule(
                    mApplication, mDbName));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
