package m.co.rh.id.a_news_provider.component.network.http;

import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.component.network.http.RedirectFollower.Chain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JVM tests for the shared redirect primitives and the manual hop follower
 * (issue #50), mirroring RedirectDuplicateCheckerTest's style: no
 * Mockito, every chain runs against hand-written stub connections through the
 * injected factory seam, fully offline.
 * <p>
 * Covered: permanent/temporary last-hop classification, relative Locations,
 * cross-scheme hops, the hop cap, terminal (non-followed) responses, sensitive
 * header stripping on cross-host hops, header/method/timeout forwarding and
 * per-hop connection configuration.
 */
public class RedirectFollowerTest {

    private static final int TIMEOUT_MILLIS = 10_000;

    /**
     * Pre-programmed hop: responds {@code status} and (for redirects) hands
     * out {@code location}. Records everything the follower configures so the
     * per-hop contract can be asserted. Extends {@link HttpsURLConnection}
     * (NOT plain HttpURLConnection) so the per-hop SSL factory / hostname
     * verifier installation can be asserted; tests that pass a null factory
     * are unaffected since the follower only installs when non-null.
     * Deliberately NOT final: the throwing variant below extends it.
     */
    private static class StubConnection extends HttpsURLConnection {
        private final int status;
        private final String location;
        private final Map<String, String> sentHeaders = new LinkedHashMap<>();
        private final List<String> setMethods = new ArrayList<>();
        private int connectTimeout = -1;
        private int readTimeout = -1;
        private boolean useCaches = true;
        private boolean doInput = false;
        private boolean instanceFollowRedirects = true;
        private boolean disconnected;
        private SSLSocketFactory recordedSslSocketFactory;
        private HostnameVerifier recordedHostnameVerifier;

        private StubConnection(URL url, int status, String location) {
            super(url);
            this.status = status;
            this.location = location;
        }

        @Override
        public void connect() {
            // nothing to do - the stub is always "ready"
        }

        @Override
        public void disconnect() {
            disconnected = true;
        }

        /**
         * Disconnect flag accessor for subclasses (direct private-field access
         * through a subclass-typed receiver does not compile).
         */
        boolean isDisconnected() {
            return disconnected;
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() throws IOException {
            return status;
        }

        @Override
        public String getHeaderField(String name) {
            if (RedirectFollower.HEADER_LOCATION.equalsIgnoreCase(name)) {
                return location;
            }
            return null;
        }

        @Override
        public void setRequestMethod(String method) throws ProtocolException {
            setMethods.add(method);
        }

        @Override
        public void setConnectTimeout(int timeout) {
            connectTimeout = timeout;
        }

        @Override
        public void setReadTimeout(int timeout) {
            readTimeout = timeout;
        }

        @Override
        public void setUseCaches(boolean useCaches) {
            this.useCaches = useCaches;
        }

        @Override
        public void setDoInput(boolean newValue) {
            doInput = newValue;
        }

        @Override
        public void setInstanceFollowRedirects(boolean instanceFollowRedirects) {
            this.instanceFollowRedirects = instanceFollowRedirects;
        }

        @Override
        public void setSSLSocketFactory(SSLSocketFactory factory) {
            recordedSslSocketFactory = factory;
        }

        @Override
        public void setHostnameVerifier(HostnameVerifier verifier) {
            recordedHostnameVerifier = verifier;
        }

        @Override
        public String getCipherSuite() {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] getLocalCertificates() {
            return null;
        }

        @Override
        public java.security.cert.Certificate[] getServerCertificates() {
            return null;
        }

        @Override
        public void setRequestProperty(String field, String newValue) {
            sentHeaders.put(field, newValue);
        }
    }

    /**
     * A follower whose connections come from the given url-to-hop map; a
     * route miss fails the test loudly (AssertionError) instead of silently
     * looping. The lookup keys on the URL STRING (the follower hands URL
     * objects to the factory, and Map.get(URL) would never equal the String
     * keys).
     */
    private static RedirectFollower follower(Map<String, StubConnection> routes) {
        return new RedirectFollower(url -> {
            StubConnection stub = routes.get(url.toString());
            if (stub == null) {
                throw new AssertionError("unexpected connection attempt: " + url);
            }
            return stub;
        });
    }

    private static Chain follow(RedirectFollower follower, String startUrl) throws IOException {
        return follow(follower, startUrl, Collections.emptyMap());
    }

    private static Chain follow(RedirectFollower follower, String startUrl,
                                Map<String, String> headers) throws IOException {
        return follower.follow(new URL(startUrl), "GET", headers,
                TIMEOUT_MILLIS, TIMEOUT_MILLIS,
                (SSLSocketFactory) null, (HostnameVerifier) null);
    }

    // =========================================================================
    // isRedirect / isPermanentRedirect / resolveLocation primitives
    // (RedirectDuplicateChecker delegates here - see its test for the
    // original absorbed cases; the extras below pin the issue-#50 additions)
    // =========================================================================

    @Test
    public void isRedirectCoversExactlyTheFollowedCodes() {
        assertTrue(RedirectFollower.isRedirect(301));
        assertTrue(RedirectFollower.isRedirect(302));
        assertTrue(RedirectFollower.isRedirect(303));
        assertTrue(RedirectFollower.isRedirect(307));
        assertTrue(RedirectFollower.isRedirect(308));
        assertFalse("300 Multiple Choices must not be followed",
                RedirectFollower.isRedirect(300));
        assertFalse("304 Not Modified must not be followed",
                RedirectFollower.isRedirect(304));
        assertFalse("305 Use Proxy must not be followed",
                RedirectFollower.isRedirect(305));
        assertFalse("306 (unused) must not be followed",
                RedirectFollower.isRedirect(306));
        assertFalse(RedirectFollower.isRedirect(200));
        assertFalse(RedirectFollower.isRedirect(404));
    }

    @Test
    public void isPermanentRedirectCoversOnly301And308() {
        assertTrue(RedirectFollower.isPermanentRedirect(301));
        assertTrue(RedirectFollower.isPermanentRedirect(308));
        assertFalse("302 is temporary", RedirectFollower.isPermanentRedirect(302));
        assertFalse("303 is temporary", RedirectFollower.isPermanentRedirect(303));
        assertFalse("307 is temporary", RedirectFollower.isPermanentRedirect(307));
    }

    @Test
    public void resolveLocationAcceptsRelativeAndAbsolute() {
        assertEquals("https://example.com/a/feed",
                RedirectFollower.resolveLocation("https://example.com/a/", "feed"));
        assertEquals("https://example.com/a/feed",
                RedirectFollower.resolveLocation("https://example.com/a/", "/a/feed"));
        assertEquals("https://other.example.org/feed",
                RedirectFollower.resolveLocation("https://example.com/a",
                        "https://other.example.org/feed"));
    }

    @Test
    public void resolveLocationRejectsEmptyNullUnsupportedAndMalformed() {
        assertNull(RedirectFollower.resolveLocation("https://example.com", null));
        assertNull(RedirectFollower.resolveLocation("https://example.com", ""));
        assertNull(RedirectFollower.resolveLocation("https://example.com",
                "ftp://example.org/feed"));
        assertNull(RedirectFollower.resolveLocation("https://example.com",
                "http://exa mple.com/feed"));
    }

    // =========================================================================
    // Hop following
    // =========================================================================

    @Test
    public void permanent301FollowedTo200MarksPermanentRedirect() throws IOException {
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        StubConnection first = new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://b.example.com/feed");
        StubConnection second = new StubConnection(
                new URL("https://b.example.com/feed"), 200, null);
        routes.put("https://a.example.com/rss", first);
        routes.put("https://b.example.com/feed", second);

        Chain chain = follow(follower(routes), "https://a.example.com/rss");

        assertTrue("A final 301 hop must mark the chain permanent",
                chain.permanentRedirect);
        assertEquals("https://b.example.com/feed", chain.finalUrl().toString());
        assertEquals(2, chain.hopStatuses.size());
        assertEquals(Integer.valueOf(301), chain.hopStatuses.get(0));
        assertEquals(Integer.valueOf(200), chain.hopStatuses.get(1));
        assertEquals("the terminal (final) connection is returned",
                second, chain.connection);
        assertTrue("the consumed intermediate hop must be disconnected",
                first.disconnected);
        assertFalse("the terminal connection must stay open for body reads",
                second.disconnected);
    }

    @Test
    public void temporary302FollowedTo200DoesNotMarkPermanent() throws IOException {
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        routes.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 302, "https://b.example.com/feed"));
        routes.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null));

        Chain chain = follow(follower(routes), "https://a.example.com/rss");

        assertFalse("A final 302 hop must NOT rewrite the stored url",
                chain.permanentRedirect);
        assertEquals("https://b.example.com/feed", chain.finalUrl().toString());
    }

    @Test
    public void lastFollowedHopDecidesPermanence() throws IOException {
        // 301 then 302: ends at a temporary address - not permanent
        Map<String, StubConnection> temporaryEnding = new LinkedHashMap<>();
        temporaryEnding.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://b.example.com/mid"));
        temporaryEnding.put("https://b.example.com/mid", new StubConnection(
                new URL("https://b.example.com/mid"), 302, "https://b.example.com/feed"));
        temporaryEnding.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null));

        Chain temporaryChain =
                follow(follower(temporaryEnding), "https://a.example.com/rss");

        assertFalse("permanent-then-temporary must end NOT permanent (last hop wins)",
                temporaryChain.permanentRedirect);

        // 302 then 301: ends at a permanent address - permanent
        Map<String, StubConnection> permanentEnding = new LinkedHashMap<>();
        permanentEnding.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 302, "https://b.example.com/mid"));
        permanentEnding.put("https://b.example.com/mid", new StubConnection(
                new URL("https://b.example.com/mid"), 301, "https://b.example.com/feed"));
        permanentEnding.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null));

        Chain permanentChain =
                follow(follower(permanentEnding), "https://a.example.com/rss");

        assertTrue("temporary-then-permanent must end permanent (last hop wins)",
                permanentChain.permanentRedirect);
        assertEquals(3, permanentChain.hopStatuses.size());
    }

    @Test
    public void relativeLocationResolvedAgainstCurrentHop() throws IOException {
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        routes.put("https://a.example.com/rss/", new StubConnection(
                new URL("https://a.example.com/rss/"), 301, "feed.xml"));
        routes.put("https://a.example.com/rss/feed.xml", new StubConnection(
                new URL("https://a.example.com/rss/feed.xml"), 200, null));

        Chain chain = follow(follower(routes), "https://a.example.com/rss/");

        assertTrue("the followed 301 hop is permanent", chain.permanentRedirect);
        assertEquals("the relative Location must resolve against the current hop",
                "https://a.example.com/rss/feed.xml", chain.finalUrl().toString());
    }

    @Test
    public void crossSchemeHopIsFollowed() throws IOException {
        // the platform never auto-follows http->https - the whole reason the
        // follower follows manually
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        routes.put("http://a.example.com/rss", new StubConnection(
                new URL("http://a.example.com/rss"), 301, "https://a.example.com/rss"));
        routes.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 200, null));

        Chain chain = follow(follower(routes), "http://a.example.com/rss");

        assertEquals("http to https hop must be followed",
                "https://a.example.com/rss", chain.finalUrl().toString());
        assertTrue(chain.permanentRedirect);
    }

    @Test
    public void exceedingHopLimitThrowsTooManyRedirects() throws IOException {
        // an infinite same-page redirect loop
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        routes.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 302, "/rss"));

        int[] creations = {0};
        RedirectFollower follower = new RedirectFollower(url -> {
            creations[0]++;
            return new StubConnection(url, 302, "/rss");
        });

        try {
            follow(follower, "https://a.example.com/rss");
            fail("a looping chain must throw instead of returning");
        } catch (IOException e) {
            assertEquals("Too many redirects", e.getMessage());
        }
        assertEquals("the initial request plus one per followed hop must run",
                RedirectFollower.MAX_REDIRECTS + 1, creations[0]);
    }

    @Test
    public void notModified304IsTerminal() throws IOException {
        StubConnection terminalOnly = new StubConnection(
                new URL("https://a.example.com/rss"), 304, null);
        // any connection attempt for ANOTHER url would mean the 304 was
        // (wrongly) followed
        RedirectFollower follower = new RedirectFollower(url -> {
            if (!url.toString().equals("https://a.example.com/rss")) {
                throw new AssertionError("304 must not be followed");
            }
            return terminalOnly;
        });

        Chain chain = follow(follower, "https://a.example.com/rss");

        assertEquals(1, chain.hopStatuses.size());
        assertEquals(Integer.valueOf(304), chain.hopStatuses.get(0));
        assertFalse(chain.permanentRedirect);
        assertFalse("a terminal connection must not be disconnected",
                terminalOnly.disconnected);
    }

    @Test
    public void redirectWithoutLocationIsTerminal() throws IOException {
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        StubConnection noLocation = new StubConnection(
                new URL("https://a.example.com/rss"), 302, null);
        routes.put("https://a.example.com/rss", noLocation);

        Chain chain = follow(follower(routes), "https://a.example.com/rss");

        assertEquals("a 3xx without Location must be returned as the terminal response",
                1, chain.hopStatuses.size());
        assertEquals(Integer.valueOf(302), chain.hopStatuses.get(0));
        assertEquals("the request url is also the final url (nothing followed)",
                "https://a.example.com/rss", chain.finalUrl().toString());
        assertFalse(chain.permanentRedirect);
        assertFalse(noLocation.disconnected);
    }

    @Test
    public void sensitiveHeadersStrippedOnCrossHostHopOnly() throws IOException {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("Authorization", "Bearer secret-token");
        credentials.put("Cookie", "session=abc");
        credentials.put("Accept", "application/xml");

        // cross-host: Authorization/Cookie must NOT reach the new host
        Map<String, StubConnection> crossHost = new LinkedHashMap<>();
        StubConnection newHost = new StubConnection(
                new URL("https://b.example.com/feed"), 200, null);
        crossHost.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://b.example.com/feed"));
        crossHost.put("https://b.example.com/feed", newHost);

        Chain chain = follow(follower(crossHost), "https://a.example.com/rss", credentials);

        assertNull("Authorization must be stripped on a cross-host hop",
                newHost.sentHeaders.get("Authorization"));
        assertNull("Cookie must be stripped on a cross-host hop",
                newHost.sentHeaders.get("Cookie"));
        assertEquals("non-sensitive headers are still forwarded",
                "application/xml", newHost.sentHeaders.get("Accept"));

        // same-host scheme upgrade: credentials stay (same server, confidential channel)
        Map<String, StubConnection> sameHost = new LinkedHashMap<>();
        StubConnection sameHostTarget = new StubConnection(
                new URL("https://a.example.com/rss"), 200, null);
        sameHost.put("http://a.example.com/rss", new StubConnection(
                new URL("http://a.example.com/rss"), 301, "https://a.example.com/rss"));
        sameHost.put("https://a.example.com/rss", sameHostTarget);

        chain = follow(follower(sameHost), "http://a.example.com/rss", credentials);

        assertEquals("same-host hops keep Authorization",
                "Bearer secret-token", sameHostTarget.sentHeaders.get("Authorization"));
        assertEquals("same-host hops keep Cookie",
                "session=abc", sameHostTarget.sentHeaders.get("Cookie"));
    }

    @Test
    public void headersAndPerHopConfigurationForwardedToEveryHop() throws IOException {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/xml");
        headers.put("User-Agent", "NewsProvider");

        Map<String, StubConnection> routes = new LinkedHashMap<>();
        StubConnection first = new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://a.example.com/feed");
        StubConnection second = new StubConnection(
                new URL("https://a.example.com/feed"), 200, null);
        routes.put("https://a.example.com/rss", first);
        routes.put("https://a.example.com/feed", second);

        follow(follower(routes), "https://a.example.com/rss", headers);

        for (StubConnection hop : new StubConnection[]{first, second}) {
            assertEquals("headers re-issued on every hop",
                    "application/xml", hop.sentHeaders.get("Accept"));
            assertEquals("headers re-issued on every hop",
                    "NewsProvider", hop.sentHeaders.get("User-Agent"));
            assertEquals("the request method is re-issued on every hop",
                    "GET", hop.setMethods.get(0));
            assertEquals("connect timeout applied per hop",
                    TIMEOUT_MILLIS, hop.connectTimeout);
            assertEquals("read timeout applied per hop",
                    TIMEOUT_MILLIS, hop.readTimeout);
            assertFalse("caching disabled per hop", hop.useCaches);
            assertTrue("input enabled per hop", hop.doInput);
            assertFalse("platform auto-follow disabled per hop - the follower follows",
                    hop.instanceFollowRedirects);
        }
    }

    /**
     * A hop whose response read fails like a connect/read timeout does.
     */
    private static final class ThrowingConnection extends StubConnection {
        private final IOException failure;

        private ThrowingConnection(URL url, IOException failure) {
            super(url, 200, null);
            this.failure = failure;
        }

        @Override
        public int getResponseCode() throws IOException {
            throw failure;
        }
    }

    @Test
    public void sslFactoryAndHostnameVerifierInstalledOnEveryHop() throws Exception {
        // cross-scheme chain (http -> https): the https hop MUST run with the
        // caller's extended-trust factory, not the platform default - this is
        // the property that keeps an http->https redirect inside the same
        // trust anchors as the rest of the app
        Map<String, StubConnection> routes = new LinkedHashMap<>();
        StubConnection first = new StubConnection(
                new URL("http://a.example.com/rss"), 301, "https://b.example.com/feed");
        StubConnection second = new StubConnection(
                new URL("https://b.example.com/feed"), 200, null);
        routes.put("http://a.example.com/rss", first);
        routes.put("https://b.example.com/feed", second);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        // default trust/key managers - identity only, no connections are made
        sslContext.init(null, null, null);
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        HostnameVerifier hostnameVerifier = (hostname, session) -> true;

        Chain chain = follower(routes).follow(new URL("http://a.example.com/rss"), "GET",
                Collections.emptyMap(), TIMEOUT_MILLIS, TIMEOUT_MILLIS,
                sslSocketFactory, hostnameVerifier);

        assertEquals("https://b.example.com/feed", chain.finalUrl().toString());
        assertSame("the cross-scheme https hop must carry the given SSL factory",
                sslSocketFactory, second.recordedSslSocketFactory);
        assertSame(hostnameVerifier, second.recordedHostnameVerifier);
        assertSame("the follower installs on EVERY hop it opens (https-capable "
                        + "connections), not only the terminal one",
                sslSocketFactory, first.recordedSslSocketFactory);
        assertSame(hostnameVerifier, first.recordedHostnameVerifier);
    }

    @Test
    public void inFlightHopDisconnectedWhenResponseCodeFails() {
        IOException failure = new IOException("connect timed out");
        ThrowingConnection[] failingHop = new ThrowingConnection[1];
        RedirectFollower follower = new RedirectFollower(url -> {
            failingHop[0] = new ThrowingConnection(url, failure);
            return failingHop[0];
        });

        try {
            follow(follower, "https://a.example.com/rss");
            fail("the connection failure must propagate to the caller");
        } catch (IOException e) {
            assertSame("the original error must be rethrown untouched", failure, e);
            assertTrue("the failing hop is neither a consumed intermediate nor "
                            + "the returned terminal connection - it must be disconnected",
                    failingHop[0].isDisconnected());
        }
    }
}
