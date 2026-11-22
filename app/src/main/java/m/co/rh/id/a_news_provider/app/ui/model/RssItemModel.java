package m.co.rh.id.a_news_provider.app.ui.model;

import java.util.List;

public class RssItemModel {
    public CharSequence pubDate;
    public CharSequence title;
    public CharSequence description;
    public List<String> categories;
    public boolean isRead;
    public boolean isFavorite;
    public Long id;
    /**
     * Absolute number of copies (rss_item rows across ALL channels) sharing this
     * story's link, filled by PagedRssItemsCmd only when the all-channels view
     * runs the link-grouped queries (issue #45). Values &lt;= 1 mean "no known
     * duplicates" and hide the badge.
     */
    public int duplicateCount;
    /**
     * The item's feed-provided thumbnail url (issue #58), straight from
     * {@code RssItem.mediaImage} - rendered by the card layout's NetworkImageView.
     * Null/empty hides the whole thumbnail. Not used by the standard/compact rows.
     */
    public String mediaImage;
    /**
     * Display name of the item's feeding channel (issue #58), resolved on the
     * background mapping pass in the shared row SV with a per-pass memoized
     * channel lookup. Fallback chain feed name -&gt; channel title -&gt; "" (same
     * semantics as the copies expansion' resolver), so a deleted channel never
     * breaks the row render. Empty for the layouts that don't render it.
     */
    public String channelName;
    /**
     * The feeding channel's image url (issue #58) for the card layout's small
     * source icon; null when the channel has no image or is unknown.
     */
    public String channelIconUrl;
}
