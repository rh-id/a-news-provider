package m.co.rh.id.a_news_provider.base.model;

/**
 * One row of {@link m.co.rh.id.a_news_provider.base.dao.RssDao#countRssItemsByLinks(List)}:
 * a link and how many rss_item rows (channel copies) carry it. Room maps query
 * result POJO fields by COLUMN NAME, so the field names must match the query's
 * column aliases exactly - which they do here ({@code link} and {@code cnt}),
 * same as {@link ChannelUnreadCount}.
 */
public class LinkCount {
    public String link;
    public int cnt;
}
