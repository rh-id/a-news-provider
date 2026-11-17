package m.co.rh.id.a_news_provider.component.network;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.component.network.parser.RssFeedParser;
import m.co.rh.id.a_news_provider.component.network.provider.volley.RedirectRecord;
import m.co.rh.id.a_news_provider.component.network.provider.volley.RedirectRecordingHurlStack;
import m.co.rh.id.aprovider.Provider;

public class RssRequest extends Request<RssModel> {
    /**
     * Per-attempt socket timeout (issue #50). Volley's default is
     * 2500 ms, which is fine for small endpoints but routinely too short for
     * slow RSS servers, and more importantly the request must finish WELL
     * before the sync worker's 15-second future wait: with the old default a
     * stalled attempt could burn its internal retry budget and resolve ~15s
     * in, racing the worker's timeout so a request that eventually SUCCEEDED
     * was misfiled as a failure (wrong failure counters, wrong error text).
     * 10s < 15s shrinks that boundary race well inside the window for any
     * normally-sized feed (a slow-drip large body could still resolve past
     * 15s on per-read timeout granularity). Note this also lengthens the
     * worst-case latency of feed ADD (NewRssWorker shares this request): an
     * unresponsive URL now blocks the add dialog up to 10s instead of ~5s -
     * accepted, an accurate timeout beats a fast wrong one there too.
     */
    private static final int REQUEST_TIMEOUT_MILLIS = 10000;
    /**
     * Zero INTERNAL Volley retries (issue #50). The default of 1
     * re-sent every failed request once after ~2.5s, which on a 429/5xx rate
     * limiter meant each sync DOUBLE-hit the exact endpoint that just said
     * "slow down" - the opposite of what the new per-feed backoff is for.
     * Retrying within the same sync is now the backoff policy's job ACROSS
     * syncs; the request itself is single-shot.
     */
    private static final int MAX_RETRIES = 0;
    /**
     * Retry backoff multiplier of Volley's RetryPolicy contract. Moot with
     * {@link #MAX_RETRIES} = 0 (there is never a retry to back off), but the
     * three-argument DefaultRetryPolicy constructor requires it, so it is
     * pinned to the neutral 1f explicitly rather than left as a bare literal.
     */
    private static final float RETRY_BACKOFF_MULTIPLIER = 1f;

    private final Context mAppContext;
    private final RssFeedParser mParser;
    private final Response.Listener<RssModel> mListener;

    /**
     * Builds the single-shot RSS fetch request. Installs the custom
     * {@link DefaultRetryPolicy} HERE, in the request itself, so every caller
     * (the sync worker, feed add, manual refresh) gets the same bounded
     * behavior - a per-caller policy would fork the transport contract.
     * See the constant javadocs above for why each of the three numbers is
     * what it is.
     */
    public RssRequest(int method, String url, @Nullable Response.ErrorListener errorListener, Response.Listener<RssModel> listener, Provider provider) {
        super(method, url, errorListener);
        mListener = listener;
        mAppContext = provider.getContext().getApplicationContext();
        mParser = provider.get(RssFeedParser.class);
        setRetryPolicy(new DefaultRetryPolicy(REQUEST_TIMEOUT_MILLIS,
                MAX_RETRIES, RETRY_BACKOFF_MULTIPLIER));
    }

    @Override
    protected Response<RssModel> parseNetworkResponse(NetworkResponse response) {
        try {
            String responseString = decodeResponse(response);
            RssModel rssModel = mParser.parse(responseString, getUrl());
            if (rssModel == null) {
                throw new XmlPullParserException(mAppContext.getString
                        (R.string.unable_to_parse, getUrl())
                );
            }
            applyRedirectOutcome(rssModel);
            return Response.success(rssModel, HttpHeaderParser.parseCacheHeaders(response));
        } catch (XmlPullParserException e) {
            return Response.error(new ParseError(e));
        } catch (Throwable throwable) {
            return Response.error(new VolleyError(throwable));
        } finally {
            // clear in EVERY path (issue #50): with a parse failure the
            // chain record of THIS attempt must still be discarded - a stale
            // record surviving on this dispatcher thread would rewrite a LATER
            // request's feed url with a redirect that never belonged to it
            RedirectRecordingHurlStack.clear();
        }
    }

    /**
     * Transports the network layer's redirect outcome onto the parsed model
     * (issue #50). The recording stack left the chain record on this
     * dispatcher thread's ThreadLocal, and Volley runs
     * {@code performRequest} and {@code parseNetworkResponse} back-to-back on
     * one NetworkDispatcher thread (the pool has 4 threads, but a request's
     * two phases execute without interleaving - see
     * {@link RedirectRecordingHurlStack}'s javadoc), so this consume reads
     * exactly the record THIS request's connection installed, never another
     * concurrent request's. The outcome attaches ONLY when the chain actually
     * moved somewhere else (final url differs from the request url) - a
     * same-url chain or a cache-served response (no record at all) leaves the
     * model untouched, which keeps cached syncs rewrite-free by construction.
     * Note the response parsed above IS the final hop's response (manual
     * following switched the delegate before Volley ever saw a status), so
     * status/headers/body and the recorded final url describe the same fetch.
     */
    private void applyRedirectOutcome(RssModel rssModel) {
        RedirectRecord record = RedirectRecordingHurlStack.consume();
        if (record == null || record.getFinalUrl() == null) {
            return;
        }
        if (record.getFinalUrl().equals(getUrl())) {
            return;
        }
        rssModel.setFinalUrl(record.getFinalUrl());
        rssModel.setPermanentRedirect(record.isPermanentRedirect());
    }

    private String decodeResponse(NetworkResponse response) {
        String charset = HttpHeaderParser.parseCharset(response.headers);
        try {
            return new String(response.data, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(response.data, StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void deliverResponse(RssModel response) {
        mListener.onResponse(response);
    }
}
