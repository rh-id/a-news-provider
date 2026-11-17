package m.co.rh.id.a_news_provider.base.model;

import java.io.Serializable;
import java.util.ArrayList;

import androidx.annotation.Nullable;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

public class RssModel implements Serializable {
    private RssChannel rssChannel;
    private ArrayList<RssItem> rssItems;

    /**
     * The FINAL url of the fetch that produced this model, when redirects
     * were followed (issue #50). Set ONLY by RssRequest right after
     * parsing, from the network layer's redirect record; null when the
     * response came without redirect hops (direct response, cache hit,
     * parse failure). NOT serialized anywhere and never stored as-is - it
     * travels in-memory only, from the request to the worker, whose guarded
     * rewrite (RssSyncWorker) decides whether the stored feed url may adopt
     * it.
     */
    @Nullable
    private String finalUrl;

    /**
     * Whether the LAST followed redirect hop was permanent (301/308, issue
     * #50). Meaningful only while {@link #finalUrl} is non-null:
     * temporary final hops (302/303/307) keep this false so the worker does
     * NOT rewrite the feed url even though the content was fetched fine.
     * Same in-memory-only contract as {@link #finalUrl}.
     */
    private boolean permanentRedirect;

    public RssModel(RssChannel rssChannel, ArrayList<RssItem> rssItems) {
        this.rssChannel = rssChannel;
        this.rssItems = rssItems;
    }

    public RssChannel getRssChannel() {
        return rssChannel;
    }

    public ArrayList<RssItem> getRssItems() {
        return rssItems;
    }

    @Nullable
    public String getFinalUrl() {
        return finalUrl;
    }

    public void setFinalUrl(@Nullable String finalUrl) {
        this.finalUrl = finalUrl;
    }

    public boolean isPermanentRedirect() {
        return permanentRedirect;
    }

    public void setPermanentRedirect(boolean permanentRedirect) {
        this.permanentRedirect = permanentRedirect;
    }

    @Override
    public String toString() {
        return "RssModel{" +
                "rssChannel=" + rssChannel +
                ", rssItems=" + rssItems +
                ", finalUrl=" + finalUrl +
                ", permanentRedirect=" + permanentRedirect +
                '}';
    }
}
