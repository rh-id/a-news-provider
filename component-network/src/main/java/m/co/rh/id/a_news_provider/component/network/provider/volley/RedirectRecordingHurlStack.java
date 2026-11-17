package m.co.rh.id.a_news_provider.component.network.provider.volley;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import androidx.annotation.Nullable;

import javax.net.ssl.SSLSocketFactory;

import com.android.volley.toolbox.HurlStack;

import m.co.rh.id.a_news_provider.component.network.http.RedirectFollower;

/**
 * Volley {@link HurlStack} that makes redirect hops VISIBLE (issue #50).
 * The stock HurlStack lets {@link HttpURLConnection} auto-follow
 * redirects and reports only the original request url, so a feed that moved
 * to a new address (301/308) silently fetches from the new host forever
 * without the stored feed url ever being corrected - and a cross-scheme
 * redirect (http to https) fails outright because the platform never
 * auto-follows those. This override replaces {@code createConnection} so
 * every Volley request runs through {@link RedirectRecordingConnection},
 * which follows the chain manually and records the outcome in a
 * {@link RedirectRecord}.
 * <p>
 * ThreadLocal handoff contract: Volley's RequestQueue runs
 * {@code DEFAULT_NETWORK_THREAD_POOL_SIZE} (= 4 in Volley 1.2.1)
 * NetworkDispatcher threads, and EACH dispatcher executes one request's
 * network round trip and its parse BACK-TO-BACK on its own thread:
 * {@code NetworkDispatcher.processRequest} calls
 * {@code mNetwork.performRequest(request)} - which reaches
 * {@code createConnection} through this stack - and then, with no interleaving
 * in between, calls {@code request.parseNetworkResponse(networkResponse)} on
 * the same thread. The slot is installed fresh at the START of every attempt
 * ({@code createConnection} resets it, which also covers BasicNetwork's
 * internal retry loop: every retry re-enters performRequest and therefore
 * re-enters createConnection), and consumed or cleared at the end of that
 * request's parse. Set/consume therefore bracket exactly ONE request
 * lifecycle per dispatcher thread - concurrent requests use different
 * threads' slots and can never see each other's record, and one dispatcher
 * can never overlap its own two phases. Callers must still consume or
 * {@link #clear()} the record in ALL paths (including parse failures) so a
 * stale record can never leak into a LATER request's parse on the same
 * thread.
 */
public class RedirectRecordingHurlStack extends HurlStack {

    /**
     * Per-dispatcher-thread record slot. Static by design: each
     * NetworkDispatcher thread brackets exactly one request lifecycle between
     * the install in {@code createConnection} and the consume/clear in the
     * request's {@code parseNetworkResponse} (see the class javadoc for the
     * full invariant), so a static slot is safe despite the 4-thread pool.
     */
    private static final ThreadLocal<RedirectRecord> CURRENT_RECORD = new ThreadLocal<>();

    /**
     * The stack's SSL socket factory, kept here so every recording connection
     * can pre-install it: HurlStack only calls {@code setSSLSocketFactory}
     * when the REQUEST url is https, but a hop chain can CROSS into https
     * from an http request url - without this field those hops would fall
     * back to the default SSL factory and lose the extended trust anchors
     * (bundled ISRG roots) the rest of the app uses.
     */
    @Nullable
    private final SSLSocketFactory mSslSocketFactory;

    /**
     * Hop connection seam (production: plain {@code url.openConnection()});
     * kept as a field purely so JVM tests can inject stub connections without
     * any network I/O.
     */
    private final RedirectFollower.ConnectionFactory mConnectionFactory;

    /**
     * Production constructor (mirrors HurlStack's rewriter+factory signature;
     * the app never uses a url rewriter).
     *
     * @param sslSocketFactory the extended-trust SSL socket factory applied to
     *                         every https hop, may be null to keep the platform default
     */
    public RedirectRecordingHurlStack(@Nullable SSLSocketFactory sslSocketFactory) {
        this(sslSocketFactory, url -> (HttpURLConnection) url.openConnection());
    }

    /**
     * Test constructor: injects the hop connection factory so unit tests can
     * run full request/redirect flows against stub connections.
     */
    RedirectRecordingHurlStack(@Nullable SSLSocketFactory sslSocketFactory,
                               RedirectFollower.ConnectionFactory connectionFactory) {
        super(null, sslSocketFactory);
        mSslSocketFactory = sslSocketFactory;
        mConnectionFactory = connectionFactory;
    }

    /**
     * Returns a recording connection for the given url and resets the
     * thread's record FIRST: a fresh record per attempt guarantees the
     * consumer can never observe a chain from an earlier attempt (hygiene
     * even if a caller forgets to clear - the overwritten record is simply
     * unreachable).
     */
    @Override
    protected HttpURLConnection createConnection(URL url) throws IOException {
        CURRENT_RECORD.set(new RedirectRecord());
        return new RedirectRecordingConnection(url, mSslSocketFactory,
                mConnectionFactory, CURRENT_RECORD.get());
    }

    /**
     * Returns and CLEARS this thread's redirect record (issue #50).
     * Called by {@code RssRequest.parseNetworkResponse} on the success path
     * after parsing; any other caller must ensure the slot is emptied in all
     * paths via this method or {@link #clear()} - a left-over record would
     * rewrite a LATER request's feed url with a chain that never belonged to
     * it.
     *
     * @return the record of the attempt that just ran on this thread, or null
     * when no recording connection ran (e.g. a cache-served response)
     */
    @Nullable
    public static RedirectRecord consume() {
        RedirectRecord record = CURRENT_RECORD.get();
        CURRENT_RECORD.remove();
        return record;
    }

    /**
     * Clears this thread's record without consuming it - the parse-failure
     * path's hygiene call so a stale chain can never leak into a later
     * request.
     */
    public static void clear() {
        CURRENT_RECORD.remove();
    }
}
