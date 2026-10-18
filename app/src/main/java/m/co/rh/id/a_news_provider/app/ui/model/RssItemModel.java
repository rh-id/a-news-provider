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
}
