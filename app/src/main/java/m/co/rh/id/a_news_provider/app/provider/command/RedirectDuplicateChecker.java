package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.component.network.http.RedirectFollower;
import m.co.rh.id.a_news_provider.component.network.ssl.ExtendedTrustManager;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Decides whether a requested feed URL redirects to a channel that is already
 * added. When the requested channel is genuinely new, probes where it redirects
 * to; if it ends up at an already added channel the add must be rejected with an
 * inline error instead of enqueueing the fetch.
 * <p>
 * Runs inside
 * {@link m.co.rh.id.a_news_provider.app.provider.component.service.RssService#addNewFeed(String)}
 * as part of the add check, before the fetch worker is enqueued, so the add dialog
 * shows the redirect case inline. This class owns both the decision and the
 * built-in network probing
 * (the redirect chain is followed manually with {@link HttpURLConnection}
 * because at ADD time there is no fetch yet whose redirect record could be
 * consumed - the Volley stack only records the chain of the requests it
 * actually executes). The redirect semantics - followed status codes, Location
 * resolution, hop limit - are the SHARED primitives of
 * {@link RedirectFollower} (issue #50) that the Volley stack uses too,
 * so the add-time probe and the actual fetch can never disagree about where a
 * feed's chain ends. HTTPS probe connections use
 * {@link ExtendedTrustManager#createSslSocketFactory(Context)} - the same
 * extended trust (system store plus bundled ISRG roots) as the Volley
 * stack, so the probe and the actual fetch see identical
 * certificates.
 * <p>
 * Probe failures fail open: they never reject the add, the feed proceeds with the
 * normal fetch.
 */
public class RedirectDuplicateChecker {
    private static final String TAG = RedirectDuplicateChecker.class.getName();

    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final String REQUEST_METHOD_GET = "GET";

    private final Context mAppContext;
    private final RssDao mRssDao;
    private final UrlNormalizer mUrlNormalizer;
    private final ILogger mLogger;
    private final RedirectProbeFunction mRedirectProbe;

    /**
     * The redirect probe. Declares {@link IOException} so the built-in
     * {@link #resolveRedirects(String)} can be referenced directly and its
     * IOException reaches {@link #isRedirectToExistingChannel(String)}
     * unwrapped, instead of a RuntimeException wrapper.
     */
    @FunctionalInterface
    private interface RedirectProbeFunction {
        String apply(String url) throws IOException;
    }

    /**
     * Production constructor: resolves the dependencies from the given
     * {@link Provider} and the redirect probe is the built-in
     * {@link #resolveRedirects(String)} network resolution.
     */
    public RedirectDuplicateChecker(Provider provider) {
        mAppContext = provider.getContext();
        mRssDao = provider.get(RssDao.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
        mLogger = provider.get(ILogger.class);
        mRedirectProbe = this::resolveRedirects;
    }

    /**
     * When the requested channel is genuinely new, probes where it redirects to.
     * If it ends up at an already added channel, the add should be rejected.
     * Probe failures never block the normal fetch.
     *
     * @param url the requested (normalized) feed URL
     * @return true when the add should be rejected because of a redirect to a duplicate channel
     */
    public boolean isRedirectToExistingChannel(String url) {
        try {
            RssChannel existingByUrl =
                    mRssDao.findRssChannelByUrlVariants(url, mUrlNormalizer.normalizeUrl(url));
            if (existingByUrl != null) {
                // already known by requested URL - keep the normal fetch + persist merge behavior
                return false;
            }
            String resolvedUrl = mRedirectProbe.apply(url);
            if (resolvedUrl == null || resolvedUrl.equals(url)) {
                return false;
            }
            RssChannel existingByRedirect = mRssDao.findRssChannelByUrlVariants(
                    resolvedUrl, mUrlNormalizer.normalizeUrl(resolvedUrl));
            if (existingByRedirect == null) {
                return false;
            }
            return true;
        } catch (Throwable probeError) {
            // probe failure must not block adding - proceed with the normal fetch
            mLogger.e(TAG, mAppContext.getString(R.string.error_feed_add), probeError);
            return false;
        }
    }

    /**
     * Follows up to {@link RedirectFollower#MAX_REDIRECTS} HTTP redirects
     * starting from the given URL and returns the final URL. The followed
     * status codes and the Location resolution are the shared
     * {@link RedirectFollower} primitives. Unlike the Volley stack's follower
     * this probe deliberately FAILS OPEN on the hop limit and on unresolvable
     * targets - returning the last known URL instead of throwing - because a
     * probe failure must never block adding a feed. Connection errors and
     * malformed input still throw.
     * <p>
     * This is the OVERRIDE SEAM for instrumented tests: with only the single
     * {@link #RedirectDuplicateChecker(Provider)} constructor kept, test
     * provider modules inject a network-free recording resolver by overriding
     * this method in an anonymous subclass, so the fake probe flows through the
     * exact same production wiring as the real one.
     *
     * @param url the URL to start resolving from
     * @return the final URL after following redirects
     * @throws IOException when a connection cannot be established or times out
     */
    protected String resolveRedirects(String url) throws IOException {
        String currentUrl = url;
        if (!isSupportedScheme(currentUrl)) {
            return currentUrl;
        }
        SSLSocketFactory sslSocketFactory = null;
        if (mAppContext != null) {
            sslSocketFactory = ExtendedTrustManager.createSslSocketFactory(mAppContext);
        }
        for (int hop = 0; hop < RedirectFollower.MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = openConnection(currentUrl, sslSocketFactory);
            int status = connection.getResponseCode();
            if (!RedirectFollower.isRedirect(status)) {
                connection.disconnect();
                return currentUrl;
            }
            String location = connection.getHeaderField(RedirectFollower.HEADER_LOCATION);
            connection.disconnect();
            String nextUrl = RedirectFollower.resolveLocation(currentUrl, location);
            if (nextUrl == null) {
                // No usable redirect target - the current URL is the best known destination
                return currentUrl;
            }
            currentUrl = nextUrl;
        }
        return currentUrl;
    }

    private static HttpURLConnection openConnection(String url,
                                                    SSLSocketFactory sslSocketFactory) throws IOException {
        URL parsedUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) parsedUrl.openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setRequestMethod(REQUEST_METHOD_GET);
        if (connection instanceof HttpsURLConnection && sslSocketFactory != null) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
        }
        return connection;
    }

    private static boolean isSupportedScheme(String url) {
        if (url == null) {
            return false;
        }
        String trimmed = url.trim();
        return trimmed.regionMatches(true, 0, SCHEME_HTTP + ":", 0, SCHEME_HTTP.length() + 1)
                || trimmed.regionMatches(true, 0, SCHEME_HTTPS + ":", 0, SCHEME_HTTPS.length() + 1);
    }
}
