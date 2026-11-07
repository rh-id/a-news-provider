package m.co.rh.id.a_news_provider.component.network;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link ArticleHtmlRequest} (moved from the unit-test
 * source set per maintainer decision, merging with ArticleExtractorTest's
 * androidTest home).
 * <p>
 * The Content-Type guard tests were runnable on the plain JVM because the
 * guard is static and framework-free, but the REQUEST itself was not
 * instance-tested: constructing a Volley {@code Request} touches
 * {@code android.net.Uri}, which cannot run on plain JUnit. On a device that
 * constraint is gone, so the real instance is exercised here: the constructor
 * wiring (GET, no Volley cache, fixed retry policy, Uri-derived traffic tag),
 * the raw-bytes parse path (byte-for-byte passthrough of a non-ASCII payload -
 * the exact regression the raw-bytes-to-Jsoup design guards against), the
 * non-HTML failure path and the delivery into the listener. Hand-written
 * recording stubs only (no Mockito, module convention).
 */
@RunWith(AndroidJUnit4.class)
public class ArticleHtmlRequestTest {

    private static final String TEST_URL = "https://site.example/articles/one";

    // ---- Content-Type guard (static, kept from the unit-test suite) ----

    private static Map<String, String> headersWith(String contentType) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", contentType);
        return headers;
    }

    @Test
    public void textHtmlContentType_isAccepted() {
        assertTrue(ArticleHtmlRequest.isHtmlContentType(
                headersWith("text/html")));
    }

    @Test
    public void textHtmlWithCharsetParameter_isAccepted() {
        assertTrue(ArticleHtmlRequest.isHtmlContentType(
                headersWith("text/html; charset=iso-8859-1")));
    }

    @Test
    public void xhtmlContentType_isAccepted() {
        assertTrue(ArticleHtmlRequest.isHtmlContentType(
                headersWith("application/xhtml+xml; charset=utf-8")));
    }

    @Test
    public void nonHtmlContentTypes_areRejected() {
        assertFalse(ArticleHtmlRequest.isHtmlContentType(
                headersWith("application/pdf")));
        assertFalse(ArticleHtmlRequest.isHtmlContentType(
                headersWith("image/jpeg")));
        assertFalse(ArticleHtmlRequest.isHtmlContentType(
                headersWith("application/json")));
        assertFalse(ArticleHtmlRequest.isHtmlContentType(
                headersWith("text/plain")));
    }

    @Test
    public void headerFieldCasing_isIgnored() {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "text/html; charset=utf-8");
        assertTrue(ArticleHtmlRequest.isHtmlContentType(headers));
    }

    @Test
    public void missingOrNullHeader_isRejected() {
        assertFalse(ArticleHtmlRequest.isHtmlContentType(null));
        assertFalse(ArticleHtmlRequest.isHtmlContentType(new HashMap<>()));
        assertFalse(ArticleHtmlRequest.isHtmlContentType(headersWith(null)));
        Map<String, String> noContentType = new HashMap<>();
        noContentType.put("Server", "nginx");
        assertFalse(ArticleHtmlRequest.isHtmlContentType(noContentType));
    }

    // ---- Real instance tests (device-only: Uri needs the framework) ----

    @Test
    public void constructor_configuresGetNoCacheAndFixedRetryPolicy() {
        RecordingErrorListener errorListener = new RecordingErrorListener();
        RecordingListener listener = new RecordingListener();

        ArticleHtmlRequest request = new ArticleHtmlRequest(
                TEST_URL, errorListener, listener);

        assertEquals(Request.Method.GET, request.getMethod());
        // Room (article_content) is the cache - the Volley cache must stay off
        assertFalse(request.shouldCache());
        // full-page fetch: 15 s timeout, zero retries, no backoff change needed
        assertEquals(15000, request.getRetryPolicy().getCurrentTimeout());
        assertEquals(0, request.getRetryPolicy().getCurrentRetryCount());
        // the constructor derives the traffic tag from the URL through
        // android.net.Uri - on the JVM this cannot even run; here it must
        // produce a usable tag without crashing
        assertTrue(request.getTrafficStatsTag() >= 0);
        assertNull(errorListener.error);
        assertFalse(listener.called);
    }

    @Test
    public void parseNetworkResponse_htmlVariants_succeedWithIdenticalRawBytes() {
        ArticleHtmlRequest request = newArticleHtmlRequest();
        byte[] payload = rawPayloadBytes();

        // every spelling article pages actually use, including upper-case and
        // a legacy charset suffix
        String[] htmlContentTypes = {
                "text/html",
                "application/xhtml+xml",
                "text/html; charset=windows-1252",
                "TEXT/HTML"
        };
        for (String contentType : htmlContentTypes) {
            NetworkResponse networkResponse = new NetworkResponse(200, payload,
                    headersWith(contentType), false);

            Response<byte[]> response =
                    request.parseNetworkResponse(networkResponse);

            assertTrue("content type rejected: " + contentType,
                    response.isSuccess());
            assertNotNull(response.result);
            // byte-for-byte identical: the payload carries windows-1252-style
            // high bytes (0xE9 e-acute, 0x92 right single quote, 0x80 euro)
            // that a String round-trip through a fixed charset would corrupt -
            // the contract is raw passthrough, never a decode/re-encode
            assertArrayEquals("content type: " + contentType, payload,
                    response.result);
            assertSame("content type: " + contentType, payload, response.result);
            // the raw-bytes contract also means no Volley cache entry is built
            assertNull(response.cacheEntry);
        }
    }

    @Test
    public void parseNetworkResponse_nonHtmlAndMissingContentType_failsWithParseError() {
        ArticleHtmlRequest request = newArticleHtmlRequest();

        // JSON and PNG responses must fail fast instead of parsing as HTML
        for (String contentType : new String[]{"application/json", "image/png"}) {
            NetworkResponse networkResponse = new NetworkResponse(200,
                    rawPayloadBytes(), headersWith(contentType), false);

            Response<byte[]> response =
                    request.parseNetworkResponse(networkResponse);

            assertFalse("content type accepted: " + contentType,
                    response.isSuccess());
            assertNotNull(response.error);
            assertSame("content type: " + contentType, ParseError.class,
                    response.error.getClass());
        }
        // no Content-Type header at all: same fail-fast behavior
        NetworkResponse noContentTypeResponse = new NetworkResponse(200,
                rawPayloadBytes(), new HashMap<>(), false);

        Response<byte[]> response =
                request.parseNetworkResponse(noContentTypeResponse);

        assertFalse(response.isSuccess());
        assertNotNull(response.error);
        assertSame(ParseError.class, response.error.getClass());
    }

    @Test
    public void deliverResponse_passesParsedBytesToListenerUnchanged() {
        RecordingErrorListener errorListener = new RecordingErrorListener();
        RecordingListener listener = new RecordingListener();
        ArticleHtmlRequest request =
                new ArticleHtmlRequest(TEST_URL, errorListener, listener);
        byte[] payload = rawPayloadBytes();

        Response<byte[]> response = request.parseNetworkResponse(
                new NetworkResponse(200, payload, headersWith("text/html"), false));
        assertTrue(response.isSuccess());
        request.deliverResponse(response.result);

        assertTrue(listener.called);
        assertArrayEquals(payload, listener.delivered);
        // success path: the error listener never fired
        assertNull(errorListener.error);
    }

    /**
     * A payload containing windows-1252-style high bytes (0xE9, 0x92, 0x80)
     * around ASCII tags: any decode/re-encode through a fixed charset changes
     * these bytes, so byte-for-byte assertions prove raw passthrough.
     */
    private static byte[] rawPayloadBytes() {
        return new byte[]{'<', 'h', 't', 'm', 'l', '>',
                (byte) 0xE9, (byte) 0x92, (byte) 0x80,
                'c', 'a', 'f', (byte) 0xE9,
                '<', '/', 'h', 't', 'm', 'l', '>'};
    }

    private static ArticleHtmlRequest newArticleHtmlRequest() {
        return new ArticleHtmlRequest(TEST_URL,
                new RecordingErrorListener(), new RecordingListener());
    }

    /**
     * Hand-written recording listener stub (module convention: no Mockito in
     * androidTest): captures the bytes delivered by deliverResponse.
     */
    private static final class RecordingListener implements Response.Listener<byte[]> {
        boolean called;
        byte[] delivered;

        @Override
        public void onResponse(byte[] response) {
            called = true;
            delivered = response;
        }
    }

    /**
     * Hand-written recording error listener stub: captures any VolleyError so
     * tests can assert the error listener never fired on the success path.
     */
    private static final class RecordingErrorListener implements Response.ErrorListener {
        VolleyError error;

        @Override
        public void onErrorResponse(VolleyError error) {
            this.error = error;
        }
    }
}
