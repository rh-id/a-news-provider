package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JVM tests for the redirect-duplicate decision consumed by NewRssChannelCmd.
 * All 5 decision branches plus the swallow-and-continue failure paths are
 * covered: Context/RssDao/ILogger are mocked, {@link UrlNormalizer} is real and
 * the redirect resolution is injected as a recording lambda so probe invocations
 * (and their absence) can be asserted.
 * <p>
 * Also absorbs the former component-network resolver tests (deleted with the
 * merge): the pure redirect decision logic ({@code isRedirect},
 * {@code resolveLocation}) moved into {@link RedirectDuplicateChecker} as
 * package-private static helpers, and its network-free logic tests are kept
 * below in the section "Redirect probe decision logic (absorbed)".
 */
public class RedirectDuplicateCheckerTest {
    private static final String TAG = RedirectDuplicateChecker.class.getName();
    private static final String REQUEST_URL = "https://Example.com/rss/";
    // UrlNormalizer lowercases the host and strips the trailing slash
    private static final String NORMALIZED_REQUEST_URL = "https://example.com/rss";
    private static final String RESOLVED_URL = "https://Existing.com/feed/";
    // UrlNormalizer result for RESOLVED_URL
    private static final String NORMALIZED_RESOLVED_URL = "https://existing.com/feed";
    private static final String ERROR_MESSAGE = "msg";

    private Context mAppContext;
    private RssDao mRssDao;
    private ILogger mLogger;
    private List<String> mResolverCalls;
    private RedirectDuplicateChecker mChecker;

    @Before
    public void setUp() {
        mAppContext = mock(Context.class);
        mRssDao = mock(RssDao.class);
        mLogger = mock(ILogger.class);
        mResolverCalls = new ArrayList<>();
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao,
                new UrlNormalizer(), mLogger, recordingResolver(url -> url));
    }

    /**
     * Resolution step of a fake probe; declares {@link IOException} like the
     * production built-in {@code resolveRedirects} does.
     */
    @FunctionalInterface
    private interface RecordingResolution {
        String apply(String url) throws IOException;
    }

    /**
     * Resolver lambda that records every probe so tests can assert whether (and
     * how often) the redirect was resolved. A checked {@link IOException} thrown
     * by the resolution propagates through the returned {@link Function} via the
     * sneaky-throw pattern (unchecked at runtime), so the checker's
     * {@code catch (Throwable)} receives the IOException itself - exactly what
     * the production resolveRedirects path delivers.
     */
    private Function<String, String> recordingResolver(RecordingResolution resolution) {
        return url -> {
            mResolverCalls.add(url);
            try {
                return resolution.apply(url);
            } catch (IOException e) {
                throw sneakyThrow(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> E sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    private void stubErrorMessage() {
        when(mAppContext.getString(anyInt())).thenReturn(ERROR_MESSAGE);
    }

    @Test
    public void existingByRequestedUrlReturnsFalseAndNeverProbesRedirect() {
        // exact param order pinned: raw URL first, normalized URL second
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(new RssChannel());

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Existing channel by requested URL must not be treated as a redirect duplicate",
                result);
        assertEquals("Redirect probe must not run when the requested URL is already added",
                0, mResolverCalls.size());
        verify(mRssDao).findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL);
    }

    @Test
    public void productionConstructorResolvesDependenciesFromProvider() {
        // production constructor wires itself from the Provider; the probe is the
        // built-in network resolution but is never reached here because the DAO
        // already reports the requested URL as added
        Provider mockProvider = mock(Provider.class);
        when(mockProvider.getContext()).thenReturn(mAppContext);
        when(mockProvider.get(RssDao.class)).thenReturn(mRssDao);
        when(mockProvider.get(UrlNormalizer.class)).thenReturn(new UrlNormalizer());
        when(mockProvider.get(ILogger.class)).thenReturn(mLogger);
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(new RssChannel());

        mChecker = new RedirectDuplicateChecker(mockProvider);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Existing channel by requested URL must not be treated as a redirect duplicate",
                result);
        assertEquals("Redirect probe must not run when the requested URL is already added",
                0, mResolverCalls.size());
        verify(mRssDao).findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL);
    }

    @Test
    public void nullRedirectResolutionReturnsFalse() {
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> null));

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Unresolvable redirect must not be treated as a duplicate", result);
        assertEquals(1, mResolverCalls.size());
        verify(mRssDao, times(1)).findRssChannelByUrlVariants(anyString(), anyString());
    }

    @Test
    public void unchangedRedirectResolutionReturnsFalse() {
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> url));

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("URL that does not redirect must not be treated as a duplicate", result);
        assertEquals(1, mResolverCalls.size());
        verify(mRssDao, times(1)).findRssChannelByUrlVariants(anyString(), anyString());
    }

    @Test
    public void redirectTargetNotInDatabaseReturnsFalse() {
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> RESOLVED_URL));
        when(mRssDao.findRssChannelByUrlVariants(RESOLVED_URL, NORMALIZED_RESOLVED_URL))
                .thenReturn(null);

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Redirect to a NOT added channel must not be treated as a duplicate", result);
        // raw/normalized param order pinned for the second (redirect target) lookup too
        verify(mRssDao).findRssChannelByUrlVariants(RESOLVED_URL, NORMALIZED_RESOLVED_URL);
    }

    @Test
    public void redirectTargetExistsInDatabaseReturnsTrue() {
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> RESOLVED_URL));
        when(mRssDao.findRssChannelByUrlVariants(RESOLVED_URL, NORMALIZED_RESOLVED_URL))
                .thenReturn(new RssChannel());

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertTrue("Redirect to an already added channel must be reported as duplicate", result);
        verify(mRssDao).findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL);
        verify(mRssDao).findRssChannelByUrlVariants(RESOLVED_URL, NORMALIZED_RESOLVED_URL);
    }

    @Test
    public void resolverFailureIsSwallowedAndReturnsFalse() {
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        stubErrorMessage();
        RuntimeException resolverError = new RuntimeException("probe failed");
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> {
                    throw resolverError;
                }));

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Probe failure must not report a duplicate", result);
        verify(mLogger).e(eq(TAG), eq(ERROR_MESSAGE), same(resolverError));
    }

    @Test
    public void resolverIoExceptionIsSwallowedAndLoggedUnwrapped() {
        // the built-in resolveRedirects throws IOException directly (no RuntimeException
        // wrapper since the resolver was merged into the checker) - catch (Throwable)
        // must swallow it and log the IOException itself as the cause
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenReturn(null);
        stubErrorMessage();
        IOException resolverError = new IOException("probe connection failed");
        mChecker = new RedirectDuplicateChecker(mAppContext, mRssDao, new UrlNormalizer(),
                mLogger, recordingResolver(url -> {
                    throw resolverError;
                }));

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("Probe IOException must not report a duplicate", result);
        verify(mLogger).e(eq(TAG), eq(ERROR_MESSAGE), same(resolverError));
    }

    @Test
    public void daoFailureOnFirstLookupIsSwallowedAndReturnsFalse() {
        RuntimeException daoError = new RuntimeException("db unavailable");
        when(mRssDao.findRssChannelByUrlVariants(REQUEST_URL, NORMALIZED_REQUEST_URL))
                .thenThrow(daoError);
        stubErrorMessage();

        boolean result = mChecker.isRedirectToExistingChannel(REQUEST_URL);

        assertFalse("DAO failure must not report a duplicate", result);
        assertEquals("Redirect probe must not run when the DAO already failed",
                0, mResolverCalls.size());
        verify(mLogger).e(eq(TAG), eq(ERROR_MESSAGE), same(daoError));
    }

    // =========================================================================
    // Redirect probe decision logic (absorbed from the former component-network
    // resolver tests, deleted with the merge) - network-free tests for the
    // package-private static helpers the built-in probe uses.
    // =========================================================================

    @Test
    public void testIsRedirectTrueForFollowedCodes() {
        assertTrue(RedirectDuplicateChecker.isRedirect(301));
        assertTrue(RedirectDuplicateChecker.isRedirect(302));
        assertTrue(RedirectDuplicateChecker.isRedirect(303));
        assertTrue(RedirectDuplicateChecker.isRedirect(307));
        assertTrue(RedirectDuplicateChecker.isRedirect(308));
    }

    @Test
    public void testIsRedirectFalseForOtherStatuses() {
        assertFalse(RedirectDuplicateChecker.isRedirect(200));
        assertFalse(RedirectDuplicateChecker.isRedirect(201));
        assertFalse(RedirectDuplicateChecker.isRedirect(304));
        assertFalse(RedirectDuplicateChecker.isRedirect(404));
        assertFalse(RedirectDuplicateChecker.isRedirect(500));
    }

    @Test
    public void testResolveLocationAbsolute() {
        assertEquals("https://example.org/feed",
                RedirectDuplicateChecker.resolveLocation("https://example.com/a",
                        "https://example.org/feed"));
    }

    @Test
    public void testResolveLocationRelative() {
        assertEquals("https://example.com/a/feed",
                RedirectDuplicateChecker.resolveLocation("https://example.com/a/", "feed"));
    }

    @Test
    public void testResolveLocationRootRelative() {
        assertEquals("https://example.com/feed",
                RedirectDuplicateChecker.resolveLocation("https://example.com/a/b", "/feed"));
    }

    @Test
    public void testResolveLocationNull() {
        assertNull(RedirectDuplicateChecker.resolveLocation("https://example.com", null));
    }

    @Test
    public void testResolveLocationEmpty() {
        assertNull(RedirectDuplicateChecker.resolveLocation("https://example.com", ""));
    }

    @Test
    public void testResolveLocationUnsupportedScheme() {
        assertNull(RedirectDuplicateChecker.resolveLocation("https://example.com",
                "ftp://example.org/feed"));
    }

    @Test
    public void testResolveLocationMalformed() {
        assertNull(RedirectDuplicateChecker.resolveLocation("https://example.com",
                "http://exa mple.com/feed"));
    }
}
