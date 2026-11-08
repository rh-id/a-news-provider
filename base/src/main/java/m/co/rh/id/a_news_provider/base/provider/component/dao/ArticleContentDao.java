package m.co.rh.id.a_news_provider.base.provider.component.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import m.co.rh.id.a_news_provider.base.entity.ArticleContent;

@Dao
public abstract class ArticleContentDao {

    /**
     * Inserts or replaces the cached article content of one link. REPLACE (not
     * plain INSERT) so a manual "Read full article" action can refresh already
     * cached content: the whole row is overwritten with the fresh extraction.
     * This method must be called on a background thread.
     *
     * @param articleContent the content row to upsert, keyed by link
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void upsertArticleContent(ArticleContent articleContent);

    /**
     * Finds the cached article content of the given link.
     * This method must be called on a background thread.
     *
     * @param link the item link to look up
     * @return the cached content, or null when nothing was fetched for this link yet
     */
    @Query("SELECT * FROM article_content WHERE link = :link")
    public abstract ArticleContent findArticleContentByLink(String link);

    /**
     * Deletes every cached article content whose link no longer exists on any
     * rss_item row. Runs AFTER item deletion (the keep-read-items prune at the
     * end of a sync, and channel deletion): the content cache is link-keyed and
     * untouched by sync upserts, so item rows can disappear while their content
     * rows survive - those rows are garbage nobody can ever open again (no
     * rss_item carries the link anymore, so no detail page can reference them).
     * <p>
     * The subquery mirrors the link-validity rules used everywhere else in the
     * codebase ({@code link IS NOT NULL AND link != ''}): rows with a null or
     * empty link can never be fetched content keys anyway
     * ({@code ArticleContent.link} is a NOT NULL primary key that is only ever
     * written from a validated non-empty item link), so excluding them from the
     * IN-subquery is safe and keeps all-null-link edge cases from wiping the
     * whole table. Returns the number of deleted rows so callers can log it.
     * This method must be called on a background thread.
     *
     * @return the number of orphaned content rows deleted
     */
    @Query("DELETE FROM article_content WHERE link NOT IN " +
            "(SELECT DISTINCT link FROM rss_item WHERE link IS NOT NULL AND link != '')")
    public abstract int deleteOrphanArticleContent();
}
