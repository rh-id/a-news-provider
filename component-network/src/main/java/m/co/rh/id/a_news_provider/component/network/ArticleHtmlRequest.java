package m.co.rh.id.a_news_provider.component.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.RetryPolicy;

import java.util.Locale;
import java.util.Map;

/**
 * Volley GET request for a full article page (issue #28 phase 1): fetches the
 * RAW response bytes of an rss_item link and hands them to the caller for
 * readability-style extraction.
 * <p>
 * Deliberate differences from {@link RssRequest}:
 * <ul>
 * <li><b>No Volley disk cache</b> - {@code setShouldCache(false)}. The article
 * body is cached in Room (article_content, keyed by link), which already
 * deduplicates fetches; writing large HTML pages into the 20 MiB feed-XML
 * DiskBasedCache would evict feed data nobody else re-fetches on a schedule.</li>
 * <li><b>Raw bytes, no manual charset decode</b> - the response is delivered as
 * the untouched {@code byte[]}; the extractor feeds them to
 * {@code Jsoup.parse(byte[]...)}, which handles BOM and meta-charset detection
 * itself (windows-1252 legacy pages included). Decoding with
 * {@code HttpHeaderParser.parseCharset} would trust the HTTP header over the
 * page's own meta declaration, which real-world pages frequently contradict.</li>
 * <li><b>Content-Type guard</b> - only HTML responses are accepted
 * ({@link #isHtmlContentType(Map)}); a PDF/image/JSON response fails fast with
 * a {@link ParseError} instead of being parsed as HTML.</li>
 * <li><b>Timeout 15 s, no retries</b> - a full-page fetch is on-demand user
 * intent (manual action or opt-in auto-fetch); silently retrying would double
 * the wait before the failure toast.</li>
 * </ul>
 */
public class ArticleHtmlRequest extends Request<byte[]> {

    private static final int TIMEOUT_MS = 15000;

    private final Response.Listener<byte[]> mListener;

    public ArticleHtmlRequest(String url,
                              @Nullable Response.ErrorListener errorListener,
                              Response.Listener<byte[]> listener) {
        super(Method.GET, url, errorListener);
        mListener = listener;
        // Room (article_content) is the cache; do not pollute the feed-XML cache
        setShouldCache(false);
        RetryPolicy retryPolicy = new DefaultRetryPolicy(TIMEOUT_MS, 0,
                DefaultRetryPolicy.DEFAULT_BACKOFF_MULT);
        setRetryPolicy(retryPolicy);
    }

    @Override
    protected Response<byte[]> parseNetworkResponse(NetworkResponse response) {
        if (!isHtmlContentType(response.headers)) {
            return Response.error(new ParseError());
        }
        // raw bytes only - charset detection is delegated to the extractor's
        // Jsoup parse, which honors BOM and <meta charset> declarations
        // (cacheEntry null: this request never uses the Volley cache anyway)
        return Response.success(response.data, null);
    }

    @Override
    protected void deliverResponse(byte[] response) {
        mListener.onResponse(response);
    }

    /**
     * True when the response headers declare an HTML content type. Checks the
     * Content-Type header case-insensitively (server header-field casing varies)
     * and accepts the two spellings article pages actually use: {@code text/html}
     * and {@code application/xhtml+xml}. Missing or non-HTML types return false
     * so the caller can fail fast.
     * Static and framework-free (no HttpHeaderParser/Android dependency), and
     * shared by {@link #parseNetworkResponse(NetworkResponse)} and the
     * androidTest suite.
     *
     * @param headers the network response headers, may be null
     * @return true when the headers declare an HTML (or XHTML) content type
     */
    static boolean isHtmlContentType(@Nullable Map<String, String> headers) {
        if (headers == null) {
            return false;
        }
        for (Map.Entry<String, String> headerEntry : headers.entrySet()) {
            String name = headerEntry.getKey();
            if (name == null || !"Content-Type".equalsIgnoreCase(name)) {
                continue;
            }
            String value = headerEntry.getValue();
            if (value == null) {
                return false;
            }
            String lowerCaseValue = value.toLowerCase(Locale.US);
            return lowerCaseValue.contains("text/html")
                    || lowerCaseValue.contains("application/xhtml+xml");
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "ArticleHtmlRequest{url=" + getUrl() + "}";
    }
}
