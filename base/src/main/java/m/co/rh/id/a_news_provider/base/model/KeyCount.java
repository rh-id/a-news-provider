package m.co.rh.id.a_news_provider.base.model;

import androidx.room.ColumnInfo;

/**
 * One row of
 * {@link m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao#countRssItemsByDedupKeys(java.util.List)}:
 * a dedup cluster key and how many rss_item rows (channel copies) carry it.
 * Room maps query result POJO fields by COLUMN NAME, so the field names must
 * match the query's column aliases exactly - which they do here
 * ({@code dedup_key} and {@code cnt}), same as {@link LinkCount} and
 * {@link ChannelUnreadCount}. The {@code dedup_key} column is bound through
 * {@link ColumnInfo} (instead of relying on a query alias) because the column
 * name is spelled with an underscore while the Java field follows camelCase,
 * mirroring the rss_item entity's own mapping of the same column.
 */
public class KeyCount {
    /**
     * The dedup cluster key this count row belongs to (the query's GROUP BY
     * value), never empty for a returned row: empty keys never enter the
     * caller's chunk input.
     */
    @ColumnInfo(name = "dedup_key")
    public String dedupKey;

    /**
     * Absolute number of rss_item rows carrying {@link #dedupKey} across ALL
     * channels, regardless of any active read/favorite filter.
     */
    public int cnt;
}
