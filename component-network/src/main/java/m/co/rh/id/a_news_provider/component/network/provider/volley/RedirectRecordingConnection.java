package m.co.rh.id.a_news_provider.component.network.provider.volley;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocketFactory;

import androidx.annotation.Nullable;

import m.co.rh.id.a_news_provider.component.network.http.RedirectFollower;

/**
 * A {@link HttpsURLConnection} wrapper that follows redirects MANUALLY and
 * records the chain's outcome (issue #50). Delegation/hop-switch
 * design: every request configuration Volley performs before the response
 * (method, headers, timeouts, SSL factory, hostname verifier) is captured on
 * the wrapper itself - there is NO real connection open at construction time -
 * and the FIRST response read ({@link #getResponseCode()} / {@link #connect()})
 * runs the captured configuration through {@link RedirectFollower#follow},
 * which opens hop connections one by one through the injected factory until a
 * non-followed response appears; from that moment {@code mChain} holds the
 * TERMINAL connection and every subsequent read (body stream, error stream,
 * headers, content metadata) is delegated to it, so HurlStack's normal
 * response extraction sees exactly the final hop's response while
 * {@link #getURL()} and the injected {@link RedirectRecord} expose the FINAL
 * url and the permanent-redirect decision that Volley otherwise loses.
 * <p>
 * Why this wrapper extends {@link HttpsURLConnection} instead of plain
 * {@link HttpURLConnection}: HurlStack installs its custom SSLSocketFactory
 * by hard-casting the connection returned from {@code createConnection} to
 * {@code HttpsURLConnection} for https urls (verified against Volley 1.2.1
 * bytecode) - a wrapper over plain HttpURLConnection would throw
 * ClassCastException on EVERY https feed, which is the most common feed
 * scheme. The wrapper is returned for http urls too (where no cast happens),
 * which is harmless and keeps one code path.
 * <p>
 * Limitations, deliberate: app requests through this stack are GET with no
 * body (RssRequest, ArticleHtmlRequest), so the follower re-issues the method
 * and headers but never a request body, and output streams are not delegated.
 * Thread confinement by contract: Volley opens and reads this connection
 * entirely on one NetworkDispatcher thread, and that SAME thread runs the
 * request's parse immediately afterwards - each dispatcher executes
 * {@code performRequest} and {@code parseNetworkResponse} back-to-back with
 * no interleaving between them (the pool has 4 dispatcher threads, but a
 * single request's two phases never split across or interleave with other
 * work) - which is what makes the ThreadLocal record handoff in
 * {@link RedirectRecordingHurlStack} sound.
 */
class RedirectRecordingConnection extends HttpsURLConnection {

    private final RedirectFollower mFollower;
    private final RedirectRecord mRecord;
    /**
     * Headers captured from Volley's {@code setRequestProperty} calls and
     * replayed on every hop by the follower. NOT forwarded to a live delegate:
     * once the terminal connection exists it is already connected and
     * HttpURLConnection rejects late header writes with IllegalStateException.
     */
    private final Map<String, String> mRequestHeaders = new LinkedHashMap<>();
    private int mConnectTimeoutMillis;
    private int mReadTimeoutMillis;
    @Nullable
    private SSLSocketFactory mSslSocketFactory;
    @Nullable
    private HostnameVerifier mHostnameVerifier;
    /**
     * The followed chain, null until the first response read triggers
     * {@link #chain()}. All post-follow reads route through it.
     */
    @Nullable
    private RedirectFollower.Chain mChain;

    /**
     * Creates the wrapper for the given request url. The real connection is
     * NOT opened here - opening happens hop-by-hop inside the follower when
     * the response is first read, through the injected {@code connectionFactory}
     * (production: {@code URL::openConnection}; tests: stub connections).
     *
     * @param url               the request url (the wrapper's initial URL)
     * @param sslSocketFactory  the stack's configured SSL socket factory,
     *                          pre-installed so cross-scheme hops (http to
     *                          https) still use the extended trust even when
     *                          HurlStack never calls setSSLSocketFactory
     *                          (it only does for https request urls)
     * @param connectionFactory the hop connection seam
     * @param record            the per-attempt record to fill once followed
     */
    RedirectRecordingConnection(URL url,
                                @Nullable SSLSocketFactory sslSocketFactory,
                                RedirectFollower.ConnectionFactory connectionFactory,
                                RedirectRecord record) {
        super(url);
        mSslSocketFactory = sslSocketFactory;
        mFollower = new RedirectFollower(connectionFactory);
        mRecord = record;
    }

    /**
     * The followed chain, running it once on first use. All Volley reads go
     * through here so the hop-switch happens exactly once per attempt.
     */
    private RedirectFollower.Chain chain() throws IOException {
        if (mChain == null) {
            mChain = mFollower.follow(getURL(), getRequestMethod(), mRequestHeaders,
                    mConnectTimeoutMillis, mReadTimeoutMillis, mSslSocketFactory,
                    mHostnameVerifier);
            // record the outcome for the request-level consumer (RssRequest);
            // the ThreadLocal handoff lives on the stack, this fills THIS
            // attempt's record
            for (int status : mChain.hopStatuses) {
                mRecord.addHopStatus(status);
            }
            // the FINAL delegate's own URL is the source of truth (it reflects
            // any normalization the platform applied to the last hop target)
            mRecord.setOutcome(mChain.connection.getURL().toString(),
                    mChain.permanentRedirect);
        }
        return mChain;
    }

    @Nullable
    private HttpURLConnection currentConnection() {
        return mChain == null ? null : mChain.connection;
    }

    // ------------------------------------------------------------------
    // Response reads (the volley-facing surface, delegated to the terminal
    // connection once the chain has been followed)
    // ------------------------------------------------------------------

    @Override
    public int getResponseCode() throws IOException {
        return chain().connection.getResponseCode();
    }

    @Nullable
    @Override
    public InputStream getInputStream() throws IOException {
        return chain().connection.getInputStream();
    }

    @Nullable
    @Override
    public InputStream getErrorStream() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getErrorStream();
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getHeaderFields();
    }

    @Nullable
    @Override
    public String getHeaderField(String name) {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getHeaderField(name);
    }

    @Nullable
    @Override
    public String getHeaderFieldKey(int position) {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getHeaderFieldKey(position);
    }

    @Nullable
    @Override
    public String getHeaderField(int position) {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getHeaderField(position);
    }

    @Override
    public int getContentLength() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? -1 : connection.getContentLength();
    }

    @Nullable
    @Override
    public String getContentType() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getContentType();
    }

    @Nullable
    @Override
    public String getContentEncoding() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? null : connection.getContentEncoding();
    }

    @Nullable
    @Override
    public String getResponseMessage() throws IOException {
        return chain().connection.getResponseMessage();
    }

    @Override
    public void connect() throws IOException {
        chain();
        connected = true;
    }

    @Override
    public void disconnect() {
        HttpURLConnection connection = currentConnection();
        if (connection != null) {
            // intermediate hops were already disconnected by the follower;
            // this closes the terminal connection HurlStack is done reading
            connection.disconnect();
        }
    }

    /**
     * The CURRENT url: the final hop target once the chain was followed, the
     * request url before that - this is the "final url" Volley never used to
     * expose.
     */
    @Override
    public URL getURL() {
        HttpURLConnection connection = currentConnection();
        return connection == null ? super.getURL() : connection.getURL();
    }

    @Override
    public boolean usingProxy() {
        HttpURLConnection connection = currentConnection();
        return connection != null && connection.usingProxy();
    }

    // ------------------------------------------------------------------
    // Request configuration (captured pre-follow, applied by the follower
    // on EVERY hop)
    // ------------------------------------------------------------------

    @Override
    public void setConnectTimeout(int timeoutMillis) {
        mConnectTimeoutMillis = timeoutMillis;
        HttpURLConnection connection = currentConnection();
        if (connection != null) {
            connection.setConnectTimeout(timeoutMillis);
        }
    }

    @Override
    public int getConnectTimeout() {
        return mConnectTimeoutMillis;
    }

    @Override
    public void setReadTimeout(int timeoutMillis) {
        mReadTimeoutMillis = timeoutMillis;
        HttpURLConnection connection = currentConnection();
        if (connection != null) {
            connection.setReadTimeout(timeoutMillis);
        }
    }

    @Override
    public int getReadTimeout() {
        return mReadTimeoutMillis;
    }

    @Override
    public void setUseCaches(boolean useCaches) {
        // captured implicitly: the follower hard-codes useCaches(false) per
        // hop (the app never wants HTTP caching at this layer); forwarding
        // only keeps a post-follow delegate consistent
        HttpURLConnection connection = currentConnection();
        if (connection != null) {
            connection.setUseCaches(useCaches);
        }
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
        mRequestHeaders.put(field, newValue);
        // deliberately NOT forwarded pre-follow: the follower replays the
        // captured map on every hop itself, including hop 1
    }

    @Override
    public void addRequestProperty(String field, String newValue) {
        // same capture semantics as setRequestProperty; the follower's map is
        // last-wins, matching how the app's requests are built (single value)
        mRequestHeaders.put(field, newValue);
    }

    /**
     * Deliberately a no-op: this connection follows redirects MANUALLY, so
     * platform auto-follow must stay off on every delegate regardless of what
     * the caller requests (HurlStack's default createConnection would have
     * enabled it - our stack override never runs that code). The follower
     * sets {@code instanceFollowRedirects(false)} on every hop it opens.
     */
    @Override
    public void setInstanceFollowRedirects(boolean followRedirects) {
        // ignored on purpose - see javadoc
    }

    @Override
    public boolean getInstanceFollowRedirects() {
        // manual following is always on; the platform flag is always off
        return false;
    }

    /**
     * Stores the SSL socket factory for per-hop application. HurlStack calls
     * this AFTER {@code createConnection} returns (for https request urls);
     * the wrapper captures it instead of forwarding because the first real
     * connection does not even exist yet, and the follower installs it on
     * every hop it opens.
     */
    @Override
    public void setSSLSocketFactory(SSLSocketFactory factory) {
        mSslSocketFactory = factory;
    }

    @Override
    public SSLSocketFactory getSSLSocketFactory() {
        return mSslSocketFactory;
    }

    @Override
    public void setHostnameVerifier(HostnameVerifier verifier) {
        mHostnameVerifier = verifier;
    }

    @Override
    public HostnameVerifier getHostnameVerifier() {
        return mHostnameVerifier;
    }

    // ------------------------------------------------------------------
    // HttpsURLConnection abstracts (TLS metadata of the terminal hop; null
    // when the chain has not run yet or ended on a plain http hop - nothing
    // in the app reads these, they exist to satisfy the HttpsURLConnection
    // contract the HurlStack cast relies on)
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public String getCipherSuite() {
        return httpsConnectionOrNull() == null
                ? null : httpsConnectionOrNull().getCipherSuite();
    }

    @Nullable
    @Override
    public Certificate[] getLocalCertificates() {
        return httpsConnectionOrNull() == null
                ? null : httpsConnectionOrNull().getLocalCertificates();
    }

    @Nullable
    @Override
    public Certificate[] getServerCertificates() throws SSLPeerUnverifiedException {
        return httpsConnectionOrNull() == null
                ? null : httpsConnectionOrNull().getServerCertificates();
    }

    @Nullable
    @Override
    public Principal getPeerPrincipal() throws SSLPeerUnverifiedException {
        return httpsConnectionOrNull() == null
                ? null : httpsConnectionOrNull().getPeerPrincipal();
    }

    @Nullable
    @Override
    public Principal getLocalPrincipal() {
        return httpsConnectionOrNull() == null
                ? null : httpsConnectionOrNull().getLocalPrincipal();
    }

    @Nullable
    private HttpsURLConnection httpsConnectionOrNull() {
        HttpURLConnection connection = currentConnection();
        return connection instanceof HttpsURLConnection
                ? (HttpsURLConnection) connection : null;
    }
}
