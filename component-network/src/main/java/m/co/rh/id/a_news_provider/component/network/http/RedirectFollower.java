package m.co.rh.id.a_news_provider.component.network.http;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import androidx.annotation.Nullable;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;

/**
 * Shared HTTP-redirect primitives extracted from the app module's
 * RedirectDuplicateChecker (issue #50) plus the manual hop-following
 * loop used by the recording connection of the Volley stack
 * ({@code RedirectRecordingConnection}).
 * <p>
 * Why this lives in component-network: the recording Volley stack (also
 * component-network) and the app-module duplicate probe both need the exact
 * same redirect semantics - the followed status set, the Location resolution
 * and the hop limit - and duplicating them would let the probe and the fetch
 * drift apart (a feed the probe accepts could then redirect differently
 * during the actual sync). The shared helper keeps one decision table.
 * <p>
 * The hop-following here is deliberately a MANUAL loop
 * ({@code setInstanceFollowRedirects(false)} on every connection): the Java
 * platform auto-follows same-scheme redirects invisibly and reports only the
 * original URL (HttpURLConnection does not follow cross-scheme redirects at
 * all, so an http-&gt;https 301 feed simply failed). Following
 * manually lets the caller see every hop status and the FINAL url, which the
 * permanent-redirect URL rewrite of issue #50 is built on.
 * <p>
 * All connections are created through the injected {@link ConnectionFactory}
 * instead of {@code URL.openConnection()} so JVM unit tests can run full
 * chains against hand-written stub connections (same seam pattern as
 * RedirectDuplicateChecker's injected resolver constructor). Stateless and
 * dependency-free: every input is an explicit parameter, so the instance is
 * thread-safe and cheap to share.
 */
public class RedirectFollower {

    /**
     * Maximum number of redirect hops that may be FOLLOWED. Lineage: the
     * pre-existing RedirectDuplicateChecker add-time probe used 5, and issue
     * #50 made that limit the SHARED bound for the Volley stack too, so a
     * malicious/looping server is bounded the same way everywhere (the
     * Android platform default is {@code http.max.redirects} = 20 for its own
     * auto-following - deliberately tighter here, a feed chain longer than 5
     * is a loop or a misconfigured server).
     */
    public static final int MAX_REDIRECTS = 5;

    /**
     * The HTTP redirect target header, shared with callers that read it
     * directly (RedirectDuplicateChecker's probe) so the header name exists
     * exactly once.
     */
    public static final String HEADER_LOCATION = "Location";
    private static final String SCHEME_HTTP = "http";
    private static final String SCHEME_HTTPS = "https";
    private static final String METHOD_GET = "GET";

    /**
     * Headers that must NOT be replayed onto a different host (issue #50,
     * defensive): a cross-host hop hands the request to a different
     * server, and credentials issued for the original host must not leak
     * there. The app's own requests (RssRequest, ArticleHtmlRequest) carry
     * none of these today, but the stack is generic transport - the strip
     * costs nothing and removes an entire class of future credential-leak
     * bugs if an authenticated request is ever routed through it.
     */
    private static final Set<String> SENSITIVE_HEADERS =
            new LinkedHashSet<>(Arrays.asList("Authorization", "Cookie"));

    /**
     * The connection seam. Production passes {@code URL::openConnection};
     * tests hand in a factory of pre-programmed stub connections so redirect
     * chains run fully offline.
     */
    @FunctionalInterface
    public interface ConnectionFactory {
        HttpURLConnection create(URL url) throws IOException;
    }

    /**
     * Outcome of a followed chain: the TERMINAL connection (its
     * {@code getResponseCode()} has already been invoked, so the body stream
     * and headers are ready to read), every status observed along the way and
     * whether the LAST FOLLOWED hop was a permanent redirect. Immutable once
     * returned by {@link #follow}.
     */
    public static final class Chain {
        /**
         * The terminal connection of the chain. NOT one of the intermediate
         * hops - those are disconnected by the follower - and already
         * "connected" in the sense that its response code was read.
         */
        public final HttpURLConnection connection;
        /**
         * Every response status observed, in order, including the terminal
         * one. Never empty.
         */
        public final List<Integer> hopStatuses;
        /**
         * True only when the last FOLLOWED hop (the redirect that produced
         * the final destination) was 301 or 308 - "permanent" means the LAST
         * hop per issue #50, not any hop: a 301-then-302 chain ends at a
         * temporary address and must not rewrite the stored feed URL.
         */
        public final boolean permanentRedirect;

        private Chain(HttpURLConnection connection, List<Integer> hopStatuses,
                      boolean permanentRedirect) {
            this.connection = connection;
            this.hopStatuses = hopStatuses;
            this.permanentRedirect = permanentRedirect;
        }

        /**
         * The final destination URL (the terminal connection's own URL).
         */
        public URL finalUrl() {
            return connection.getURL();
        }
    }

    private final ConnectionFactory mConnectionFactory;

    /**
     * Creates a follower bound to the given connection factory. Injecting the
     * factory (instead of hard-coding {@code URL.openConnection()}) is the
     * seam that keeps redirect chains JVM-testable - mirrors
     * RedirectDuplicateChecker's injected-resolver constructor pattern.
     */
    public RedirectFollower(ConnectionFactory connectionFactory) {
        mConnectionFactory = connectionFactory;
    }

    /**
     * Follows a redirect chain manually starting at {@code startUrl}, up to
     * {@link #MAX_REDIRECTS} FOLLOWED hops. Every connection is created
     * through the injected factory and configured per hop: the request method
     * and headers are re-issued (all app requests are GET; a 303 rewrites the
     * method to GET per its RFC semantics), timeouts and the SSL socket
     * factory / hostname verifier are re-applied, caching and platform
     * auto-follow are disabled, and sensitive headers are stripped on
     * cross-host hops.
     * <p>
     * Statuses 301, 302, 303, 307 and 308 are followed; the not-followable
     * 3xx codes (300/304/305/306) and a followed-code response WITHOUT a
     * usable Location header are returned as the TERMINAL response instead of
     * erroring - the caller sees exactly what the server sent and decides
     * (the feed parser validates content anyway). Scheme changes
     * http&lt;-&gt;https ARE followed (matches RedirectDuplicateChecker; the
     * parser validates content). Exceeding the hop limit throws.
     *
     * @param startUrl             the first request target
     * @param requestMethod        the HTTP method to re-issue on every hop
     *                             (plain verb as Volley spells it, e.g. "GET")
     * @param headers              request headers to re-issue on every hop
     * @param connectTimeoutMillis per-hop connect timeout
     * @param readTimeoutMillis    per-hop read timeout
     * @param sslSocketFactory     applied to https hops when non-null
     * @param hostnameVerifier     applied to https hops when non-null
     * @return the terminal {@link Chain}
     * @throws IOException when a connection cannot be established, times out,
     *                     or the chain exceeds {@link #MAX_REDIRECTS} hops
     */
    public Chain follow(URL startUrl,
                        String requestMethod,
                        Map<String, String> headers,
                        int connectTimeoutMillis,
                        int readTimeoutMillis,
                        @Nullable SSLSocketFactory sslSocketFactory,
                        @Nullable HostnameVerifier hostnameVerifier) throws IOException {
        URL currentUrl = startUrl;
        // defensive copy: stripping sensitive headers on a cross-host hop must
        // not mutate the caller's map (the wrapper keeps its capture pristine)
        Map<String, String> hopHeaders = new LinkedHashMap<>(headers);
        List<Integer> hopStatuses = new ArrayList<>();
        boolean permanentRedirect = false;
        String method = requestMethod;
        int hopsFollowed = 0;
        while (true) {
            HttpURLConnection connection = mConnectionFactory.create(currentUrl);
            configureConnection(connection, method, hopHeaders, connectTimeoutMillis,
                    readTimeoutMillis, sslSocketFactory, hostnameVerifier);
            int status;
            try {
                status = connection.getResponseCode();
            } catch (IOException e) {
                // the in-flight hop is neither a consumed intermediate (those
                // are disconnected below) nor the terminal connection the
                // caller receives - without this it would leak its socket;
                // rethrow the original error untouched
                connection.disconnect();
                throw e;
            }
            hopStatuses.add(status);
            if (!isRedirect(status)) {
                return new Chain(connection, hopStatuses, permanentRedirect);
            }
            String location = connection.getHeaderField(HEADER_LOCATION);
            URL nextUrl = parseLocation(currentUrl, location);
            if (nextUrl == null) {
                // 3xx without a usable Location: treat as the terminal
                // response, no error - the caller reads the body/headers the
                // server sent and decides (parser validates content anyway)
                return new Chain(connection, hopStatuses, permanentRedirect);
            }
            if (hopsFollowed >= MAX_REDIRECTS) {
                // the shared RedirectDuplicateChecker lineage limit of 5 (see
                // MAX_REDIRECTS); a chain this long is a loop or a
                // misconfigured server, not a feed
                connection.disconnect();
                throw new IOException("Too many redirects");
            }
            // this hop IS followed: remember it (the last remembered hop wins,
            // which is exactly the "last hop is permanent" rule of issue #50)
            permanentRedirect = isPermanentRedirect(status);
            if (HttpURLConnection.HTTP_SEE_OTHER == status && !METHOD_GET.equals(method)) {
                // 303 semantically means "fetch the target with GET"
                method = METHOD_GET;
            }
            if (isCrossHost(currentUrl, nextUrl)) {
                stripSensitiveHeaders(hopHeaders);
            }
            hopsFollowed++;
            currentUrl = nextUrl;
            connection.disconnect();
        }
    }

    /**
     * Applies the per-hop contract: request method + headers re-issued,
     * timeouts re-applied, caching off (responses are parsed straight into the
     * caller's own storage), input on, and platform auto-follow OFF - without
     * this a same-scheme hop would be followed invisibly by the platform and
     * this loop would never see the 3xx status. The SSL socket factory and
     * hostname verifier are installed when provided so every https hop uses
     * the same extended trust as the rest of the app.
     */
    private static void configureConnection(HttpURLConnection connection,
                                            String method,
                                            Map<String, String> headers,
                                            int connectTimeoutMillis,
                                            int readTimeoutMillis,
                                            @Nullable SSLSocketFactory sslSocketFactory,
                                            @Nullable HostnameVerifier hostnameVerifier) throws IOException {
        connection.setRequestMethod(method);
        connection.setConnectTimeout(connectTimeoutMillis);
        connection.setReadTimeout(readTimeoutMillis);
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setInstanceFollowRedirects(false);
        for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
            connection.setRequestProperty(headerEntry.getKey(), headerEntry.getValue());
        }
        if (connection instanceof HttpsURLConnection) {
            if (sslSocketFactory != null) {
                ((HttpsURLConnection) connection).setSSLSocketFactory(sslSocketFactory);
            }
            if (hostnameVerifier != null) {
                ((HttpsURLConnection) connection).setHostnameVerifier(hostnameVerifier);
            }
        }
    }

    /**
     * Removes the credential headers from the map replayed on later hops.
     * Runs once per cross-host hop; case-insensitive on the header names.
     */
    private static void stripSensitiveHeaders(Map<String, String> headers) {
        Iterator<Map.Entry<String, String>> iterator = headers.entrySet().iterator();
        while (iterator.hasNext()) {
            String name = iterator.next().getKey();
            for (String sensitive : SENSITIVE_HEADERS) {
                if (sensitive.equalsIgnoreCase(name)) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * True when the hop moves to a DIFFERENT server, i.e. a different host
     * (case-insensitive) or explicitly-differing ports ({@code localhost:8080}
     * and {@code localhost:9090} are different endpoints, and credentials must
     * not wander between them). A same-host scheme upgrade on default ports
     * (http-&gt;https) is NOT cross-host: the credentials stay with the same
     * server, now on a confidential channel - which is why the ports are only
     * compared when BOTH are explicit; {@code example.com} and
     * {@code example.com:443} are the same https endpoint.
     */
    private static boolean isCrossHost(URL from, URL to) {
        if (from.getHost() == null || to.getHost() == null) {
            return !Objects.equals(from.getHost(), to.getHost());
        }
        if (!from.getHost().equalsIgnoreCase(to.getHost())) {
            return true;
        }
        int fromPort = from.getPort();
        int toPort = to.getPort();
        return fromPort != -1 && toPort != -1 && fromPort != toPort;
    }

    /**
     * Resolves the raw Location header value against the current URL into an
     * http/https target. Returns null when the header is missing/empty, the
     * resolution fails or the target uses a non-http(s) scheme (ftp:,
     * intent:, ...) - callers treat null as "no followable hop". Accepts
     * absolute URLs and relative references resolved against the current
     * hop's URL. Public shared primitive - RedirectDuplicateChecker
     * delegates here.
     *
     * @param baseUrl  the URL the redirect response came from
     * @param location the raw Location header value, may be null or empty
     * @return the absolute redirect target, or null when it cannot be
     * resolved or does not use a supported scheme
     */
    @Nullable
    public static String resolveLocation(String baseUrl, String location) {
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
     * {@link #resolveLocation(String, String)} producing a parsed {@link URL}
     * for the follower's loop. Unreachable failure path in practice (an
     * http/https URI string always parses as an http/https URL), kept
     * exception-safe because a redirect hop must never crash a sync with an
     * unexpected throw type.
     */
    @Nullable
    private static URL parseLocation(URL baseUrl, String location) {
        String resolved = resolveLocation(baseUrl.toString(), location);
        if (resolved == null) {
            return null;
        }
        try {
            return new URL(resolved);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /**
     * Whether the status code is one of the FOLLOWED redirect codes (301,
     * 302, 303, 307, 308). The other 3xx codes are deliberately not here:
     * 300/304/305/306 either carry no per-target Location semantics a feed
     * reader can honor or are responses to conditions the app never issues.
     * Public shared primitive - RedirectDuplicateChecker delegates here.
     *
     * @param statusCode the HTTP status code
     * @return true when the status code is a followed redirect
     */
    public static boolean isRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM
                || statusCode == HttpURLConnection.HTTP_MOVED_TEMP
                || statusCode == HttpURLConnection.HTTP_SEE_OTHER
                || statusCode == 307
                || statusCode == 308;
    }

    /**
     * Whether the redirect status is PERMANENT (301 Moved Permanently or 308
     * Permanent Redirect) - the codes after which the stored feed URL may be
     * rewritten (issue #50). Temporary codes (302/303/307) return false: the
     * server may point somewhere else again on the next request.
     *
     * @param statusCode the HTTP redirect status code
     * @return true when the status marks a permanent redirect
     */
    public static boolean isPermanentRedirect(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_MOVED_PERM || statusCode == 308;
    }
}
