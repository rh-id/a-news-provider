package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.function.Function;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.component.network.ssl.ExtendedTrustManager;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Decides whether a requested feed URL redirects to a channel that is already
 * added. When the requested channel is genuinely new, probes where it redirects
 * to; if it ends up at an already added channel the add must be rejected with an
 * inline error instead of enqueueing the fetch.
 * <p>
 * Runs inside {@link NewRssChannelCmd#execute(String)} as part of the add check,
 * before the fetch worker is enqueued, so the add dialog shows the redirect case
 * inline. This class owns both the decision and the built-in network probing
 * (the redirect chain is followed manually with {@link HttpURLConnection}
 * because the Volley stack reports the original request URL and never captures
 * redirect targets). HTTPS probe connections use
 * {@link ExtendedTrustManager#createSslSocketFactory(Context)} - the same
 * extended trust (system store plus bundled ISRG roots) as the Volley
 * {@code HurlStack}, so the probe and the actual fetch see identical
 * certificates.
 * <p>
 * Probe failures fail open: they never reject the add, the feed proceeds with the
 * normal fetch.
 */
public class RedirectDuplicateChecker {
    private static final String TAG = RedirectDuplicateChecker.class.getName();

    private static final int MAX_REDIRECTS = 5;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 10_000;
    private static final String HEADER_LOCATION = "Location";
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
     * Test/fake constructor: injects the redirect resolution so tests can run
     * without any network (JVM tests and instrumented fake registrations).
     */
    public RedirectDuplicateChecker(Context appContext,
                                    RssDao rssDao,
                                    UrlNormalizer urlNormalizer,
                                    ILogger logger,
                                    Function<String, String> redirectResolver) {
        mAppContext = appContext;
        mRssDao = rssDao;
        mUrlNormalizer = urlNormalizer;
        mLogger = logger;
        mRedirectProbe = redirectResolver::apply;
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
     * Follows up to {@value MAX_REDIRECTS} HTTP redirects starting from the given URL
     * and returns the final URL. Redirect status codes 301, 302, 303, 307 and 308 are
     * followed; only http and https targets are accepted. When a response is not a
     * redirect, carries no Location header, the hop limit is reached or the target
     * cannot be resolved, the last known URL is returned. Connection errors and
     * malformed input throw.
     *
     * @param url the URL to start resolving from
     * @return the final URL after following redirects
     * @throws IOException when a connection cannot be established or times out
     */
    private String resolveRedirects(String url) throws IOException {
        String currentUrl = url;
        if (!isSupportedScheme(currentUrl)) {
            return currentUrl;
        }
        SSLSocketFactory sslSocketFactory = null;
        if (mAppContext != null) {
            sslSocketFactory = ExtendedTrustManager.createSslSocketFactory(mAppContext);
        }
        for (int hop = 0; hop < MAX_REDIRECTS; hop++) {
            HttpURLConnection connection = openConnection(currentUrl, sslSocketFactory);
            int status = connection.getResponseCode();
            if (!isRedirect(status)) {
                connection.disconnect();
                return currentUrl;
            }
            String location = connection.getHeaderField(HEADER_LOCATION);
            connection.disconnect();
            String nextUrl = resolveLocation(currentUrl, location);
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

    /**
     * Resolves the Location header value against the current URL.
     * Package-private for testing.
     *
     * @param baseUrl  the URL the redirect response came from
     * @param location the raw Location header value, may be null or empty
     * @return the absolute redirect target, or null when it cannot be resolved
     * or does not use a supported scheme
     */
    static String resolveLocation(String baseUrl, String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        try {
            URI resolved = new URI(baseUrl.trim()).resolve(location.trim());
            String scheme = resolved.getScheme();
            if (!SCHEME_HTTP.equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme)) {
                return null;
            }
            return resolved.toString();
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Whether the status code is one of the followed redirect codes (301, 302, 303, 307, 308).
     * Package-private for testing.
     *
     * @param statusCode the HTTP status code
     * @return true when the status code is a followed redirect
     */
    static boolean isRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM
                || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                || statusCode == HttpURLConnection.HTTP_SEE_OTHER
                || statusCode == 307
                || statusCode == 308;
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
