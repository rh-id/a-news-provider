package m.co.rh.id.a_news_provider.base.model;

/**
 * Per-channel rss_item count row of
 * {@code RssDao.countRssItemsByChannel()} (issue #42 feed-health dashboard).
 * Room maps the {@code @Query} result columns by name, so the field names and
 * types mirror the SELECT aliases exactly (same convention as
 * {@link ChannelUnreadCount}): {@code channel_id} stays snake_case.
 * <p>
 * {@code cnt} is the TOTAL item count of the channel and {@code unread} the
 * unread subset. Rows are GROUP BY results: a channel without any rss_item
 * row is omitted by the query - the consumer (FeedHealthCmd) zero-fills those
 * channels so empty feeds never vanish from the dashboard.
 */
public class ChannelItemCount {
    public Long channel_id;
    public int cnt;
    public int unread;
}
