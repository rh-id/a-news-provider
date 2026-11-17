package m.co.rh.id.a_news_provider.component.network.provider.volley;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.component.network.http.RedirectFollower;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * JVM tests for the recording connection of the Volley stack (issue #50)
 * and its ThreadLocal handoff with
 * {@link RedirectRecordingHurlStack}. The connection is the riskiest piece of
 * the change - it sits between HurlStack and the platform connection - so its
 * delegate/hop-switch contract is pinned here offline through the injected
 * stub factory: the response Volley reads must be the FINAL hop's, the
 * wrapper exposes the FINAL url, and the record carries the hop statuses and
 * the last-hop permanent decision.
 */
public class RedirectRecordingConnectionTest {

    /**
     * Pre-programmed hop with a body, recording the headers the wrapper
     * configures.
     */
    private static final class StubConnection extends HttpURLConnection {
        private final int status;
        private final String location;
        private final byte[] body;
        private final Map<String, String> sentHeaders = new LinkedHashMap<>();

        private StubConnection(URL url, int status, String location, byte[] body) {
            super(url);
            this.status = status;
            this.location = location;
            this.body = body;
        }

        @Override
        public void connect() {
            // nothing to do - the stub is always "ready"
        }

        @Override
        public void disconnect() {
            // nothing to do
        }

        @Override
        public boolean usingProxy() {
            return false;
        }

        @Override
        public int getResponseCode() {
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
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void setInstanceFollowRedirects(boolean instanceFollowRedirects) {
            // the per-hop platform-follow contract is pinned in
            // RedirectFollowerTest; nothing to record here
        }

        @Override
        public void setRequestProperty(String field, String newValue) {
            sentHeaders.put(field, newValue);
        }

        @Override
        public Map<String, List<String>> getHeaderFields() {
            Map<String, List<String>> fields = new LinkedHashMap<>();
            if (location != null) {
                fields.put(RedirectFollower.HEADER_LOCATION,
                        Collections.singletonList(location));
            }
            fields.put("X-Hop", Collections.singletonList(getURL().getHost()));
            return fields;
        }
    }

    /**
     * Route-map factory: a miss fails the test loudly instead of looping.
     */
    private static RedirectFollower.ConnectionFactory routes(Map<String, StubConnection> map) {
        return route -> {
            StubConnection stub = map.get(route.toString());
            if (stub == null) {
                throw new AssertionError("unexpected connection attempt: " + route);
            }
            return stub;
        };
    }

    private static RedirectRecordingConnection connection(
            Map<String, StubConnection> routeMap, String url, RedirectRecord record)
            throws IOException {
        return new RedirectRecordingConnection(new URL(url), (SSLSocketFactory) null,
                routes(routeMap), record);
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[64];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    @Test
    public void responseReadsComeFromFinalHopAfterPermanentChain() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://b.example.com/feed",
                "old-body".getBytes()));
        StubConnection finalHop = new StubConnection(
                new URL("https://b.example.com/feed"), 200, null,
                "final-body".getBytes());
        routeMap.put("https://b.example.com/feed", finalHop);

        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", new RedirectRecord());

        assertEquals("getResponseCode must report the FINAL hop's status",
                200, connection.getResponseCode());
        assertArrayEquals("the body must be the FINAL hop's body",
                "final-body".getBytes(),
                readAll(connection.getInputStream()));
        assertEquals("header map reads must hit the FINAL hop",
                "b.example.com", connection.getHeaderFields().get("X-Hop").get(0));
        assertEquals("getURL must expose the FINAL url",
                new URL("https://b.example.com/feed"), connection.getURL());
    }

    @Test
    public void recordCapturesHopStatusesFinalUrlAndPermanence() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 301, "https://b.example.com/feed",
                "old-body".getBytes()));
        routeMap.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null, "final".getBytes()));

        RedirectRecord record = new RedirectRecord();
        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", record);
        connection.getResponseCode();

        assertTrue(record.isPermanentRedirect());
        assertEquals("https://b.example.com/feed", record.getFinalUrl());
        assertEquals(2, record.getHopStatuses().size());
        assertEquals(Integer.valueOf(301), record.getHopStatuses().get(0));
        assertEquals(Integer.valueOf(200), record.getHopStatuses().get(1));
    }

    @Test
    public void temporaryChainRecordsNotPermanent() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 302, "https://b.example.com/feed",
                "moved".getBytes()));
        routeMap.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null, "final".getBytes()));

        RedirectRecord record = new RedirectRecord();
        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", record);
        connection.getResponseCode();

        assertFalse(record.isPermanentRedirect());
        assertEquals("https://b.example.com/feed", record.getFinalUrl());
    }

    @Test
    public void directResponseRecordsSameUrlAndNotPermanent() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 200, null, "body".getBytes()));

        RedirectRecord record = new RedirectRecord();
        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", record);
        assertEquals(200, connection.getResponseCode());

        assertFalse(record.isPermanentRedirect());
        assertEquals("a direct response's final url is the request url itself",
                "https://a.example.com/rss", record.getFinalUrl());
        assertEquals(1, record.getHopStatuses().size());
    }

    @Test
    public void wrapperForcesManualFollowRegardlessOfCallerFlag() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 200, null, "body".getBytes()));

        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", new RedirectRecord());
        // a caller (HurlStack's default createConnection would) asking for
        // platform auto-follow must not enable it - the wrapper follows itself
        connection.setInstanceFollowRedirects(true);

        assertFalse(connection.getInstanceFollowRedirects());
    }

    @Test
    public void capturedHeadersReplayOntoFinalHop() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 302, "https://b.example.com/feed",
                "moved".getBytes()));
        StubConnection finalHop = new StubConnection(
                new URL("https://b.example.com/feed"), 200, null, "final".getBytes());
        routeMap.put("https://b.example.com/feed", finalHop);

        RedirectRecordingConnection connection =
                connection(routeMap, "https://a.example.com/rss", new RedirectRecord());
        connection.setRequestProperty("Accept", "application/xml");
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(8000);
        connection.getResponseCode();

        assertEquals("the captured header must be replayed onto every hop",
                "application/xml", finalHop.sentHeaders.get("Accept"));
        assertEquals(9000, connection.getConnectTimeout());
        assertEquals(8000, connection.getReadTimeout());
    }

    @Test
    public void threadLocalRecordIsFreshPerAttemptAndClearedOnConsume() throws Exception {
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 200, null, "body".getBytes()));

        RedirectRecordingHurlStack stack =
                new RedirectRecordingHurlStack((SSLSocketFactory) null, routes(routeMap));

        // no record before any connection was created
        assertNull(RedirectRecordingHurlStack.consume());

        // createConnection installs a FRESH record on the thread (lazy - no
        // network happens by constructing the wrapper)
        HttpURLConnection connection =
                stack.createConnection(new URL("https://a.example.com/rss"));
        assertNotNull(connection);
        RedirectRecord record = RedirectRecordingHurlStack.consume();
        assertNotNull("createConnection must install a record", record);

        // consume clears the slot: a second consume sees nothing
        assertNull("consume must clear the ThreadLocal",
                RedirectRecordingHurlStack.consume());

        // a SECOND attempt installs a fresh, unrelated record
        HttpURLConnection secondConnection =
                stack.createConnection(new URL("https://a.example.com/rss"));
        assertNotNull(secondConnection);
        RedirectRecord secondRecord = RedirectRecordingHurlStack.consume();
        assertNotNull(secondRecord);
        assertTrue("fresh record per attempt", secondRecord != record);
        assertTrue(secondRecord.getHopStatuses().isEmpty());

        // clear() empties the slot without consuming
        stack.createConnection(new URL("https://a.example.com/rss"));
        RedirectRecordingHurlStack.clear();
        assertNull(RedirectRecordingHurlStack.consume());
    }

    @Test
    public void chainThroughTheStackSeamRecordsPermanentOutcome() throws Exception {
        // the wrapper uses the same RedirectFollower the duplicate checker
        // delegates its shared primitives to; a permanent chain through the
        // STACK seam (not just the connection constructor) must produce the
        // same record shape
        Map<String, StubConnection> routeMap = new LinkedHashMap<>();
        routeMap.put("https://a.example.com/rss", new StubConnection(
                new URL("https://a.example.com/rss"), 308, "https://b.example.com/feed",
                "moved".getBytes()));
        routeMap.put("https://b.example.com/feed", new StubConnection(
                new URL("https://b.example.com/feed"), 200, null, "final".getBytes()));

        RedirectRecordingHurlStack stack =
                new RedirectRecordingHurlStack((SSLSocketFactory) null, routes(routeMap));
        HttpURLConnection connection =
                stack.createConnection(new URL("https://a.example.com/rss"));
        assertEquals(200, connection.getResponseCode());

        RedirectRecord record = RedirectRecordingHurlStack.consume();
        assertNotNull(record);
        assertTrue("308 is permanent", record.isPermanentRedirect());
        assertEquals("https://b.example.com/feed", record.getFinalUrl());
    }
}
