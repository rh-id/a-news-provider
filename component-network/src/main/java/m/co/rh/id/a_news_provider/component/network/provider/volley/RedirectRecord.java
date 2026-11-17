package m.co.rh.id.a_news_provider.component.network.provider.volley;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.Nullable;

/**
 * The redirect outcome of ONE network attempt, captured by
 * {@link RedirectRecordingHurlStack} and consumed by
 * {@code RssRequest.parseNetworkResponse} (issue #50).
 * <p>
 * Lives only in memory, handed from the connection level to the request level
 * through a ThreadLocal (see the stack's javadoc for the thread-safety
 * argument); it is never serialized, never cached and never stored - the
 * WORKER decides what to do with the outcome (RssSyncWorker's guarded URL
 * rewrite), the request only transports it on the parsed {@code RssModel}.
 */
public class RedirectRecord {

    /**
     * Every response status observed during the attempt, in hop order,
     * including the terminal response. Empty until the connection followed
     * its chain (i.e. until the first getResponseCode()-triggered follow
     * completed).
     */
    private final List<Integer> mHopStatuses = new ArrayList<>();

    /**
     * The FINAL url of the chain (the last delegate connection's URL), null
     * until the chain was followed.
     */
    @Nullable
    private String mFinalUrl;

    /**
     * True only when the last FOLLOWED hop was 301/308 - the "permanent"
     * decision of issue #50. Meaningless while {@link #mFinalUrl} is null.
     */
    private boolean mPermanentRedirect;

    /**
     * @return every response status observed during the attempt, in order
     * (read-only view; empty before the chain was followed)
     */
    public List<Integer> getHopStatuses() {
        return Collections.unmodifiableList(mHopStatuses);
    }

    /**
     * @return the final url of the followed chain, or null when the response
     * was served without following any hop
     */
    @Nullable
    public String getFinalUrl() {
        return mFinalUrl;
    }

    /**
     * @return true when the LAST FOLLOWED hop was a permanent redirect
     * (301/308); false when no hop was followed or the last followed hop was
     * temporary (302/303/307)
     */
    public boolean isPermanentRedirect() {
        return mPermanentRedirect;
    }

    void addHopStatus(int status) {
        mHopStatuses.add(status);
    }

    void setOutcome(@Nullable String finalUrl, boolean permanentRedirect) {
        mFinalUrl = finalUrl;
        mPermanentRedirect = permanentRedirect;
    }
}
