package m.co.rh.id.a_news_provider.base.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Cached full-text article body for an rss_item, keyed by the item's link
 * (Phase 1 of the in-app article reader, issue #28). A summary-only feed item
 * can fetch and extract the full page text once; the row is then reused on
 * every later open of any rss_item row sharing that link (cross-feed copies
 * included - the same link-keying the read/favorite state already uses).
 * <p>
 * Deliberately a SEPARATE table (not a column on rss_item): the sync upsert
 * {@code upsertRssItemsForChannel} overwrites the feed-provided fields of
 * matched rows on every sync, so a stored description column would be clobbered
 * and the fetched text would need re-extraction after each sync. Being
 * link-keyed and untouched by syncs, fetched content survives feed updates
 * indefinitely; rows whose link no longer exists on any rss_item are removed by
 * {@code ArticleContentDao.deleteOrphanArticleContent()} at the end of a sync
 * prune and after channel deletion.
 */
@Entity(tableName = "article_content")
public class ArticleContent {

    /**
     * The rss_item link this content was fetched for. Primary key with NO
     * autogenerate: the link IS the identity (one cached article per link),
     * mirroring the by-link state sync of rss_item.
     */
    @NonNull
    @PrimaryKey
    @ColumnInfo(name = "link")
    public String link;

    /**
     * The extracted, sanitized article HTML (readability-style extraction,
     * text-only: images/scripts/iframes are stripped by the extractor).
     */
    @NonNull
    @ColumnInfo(name = "content")
    public String content;

    /**
     * When the content was fetched, in epoch millis. Informational only today
     * (no staleness policy is applied yet), kept for future refresh decisions.
     */
    @ColumnInfo(name = "fetched_date_time")
    public long fetchedDateTime;

    public ArticleContent(@NonNull String link, @NonNull String content,
                          long fetchedDateTime) {
        this.link = link;
        this.content = content;
        this.fetchedDateTime = fetchedDateTime;
    }

    @NonNull
    @Override
    public String toString() {
        return "ArticleContent{" +
                "link='" + link + '\'' +
                ", content length=" + content.length() +
                ", fetchedDateTime=" + fetchedDateTime +
                '}';
    }
}
