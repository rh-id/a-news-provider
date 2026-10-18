package m.co.rh.id.a_news_provider.base.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.RoomWarnings;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;
import m.co.rh.id.a_news_provider.base.model.LinkCount;

@Dao
public abstract class RssDao {

    /**
     * Maximum number of links bound per UPDATE statement. SQLite older than 3.32
     * (Android 11 and below) allows at most 999 host parameters per query, and the
     * caller's keyset page size is 100 (see BaseRssItemsCmd.PAGE_SIZE), so 500 keeps
     * every chunk safely within the limit while halving the number of statements.
     */
    private static final int MARK_READ_BY_LINKS_CHUNK_SIZE = 500;

    /**
     * Maximum number of links bound per COUNT statement of
     * {@link #countRssItemsByLinks(List)}. Same 999-host-parameter ceiling as
     * {@link #MARK_READ_BY_LINKS_CHUNK_SIZE}, but here the caller is the
     * all-channels list window (issue #45), which grows by pages of 100 and can
     * exceed 1000 accumulated rows in REFRESH mode, so the chunking is not
     * theoretical - it is the steady state for a deep scroll.
     */
    private static final int COUNT_BY_LINKS_CHUNK_SIZE = 500;

    @Query("SELECT * FROM rss_channel ORDER BY feed_name")
    public abstract List<RssChannel> loadAllRssChannel();

    @Query("SELECT * FROM rss_channel WHERE id = :id")
    public abstract RssChannel findRssChannelById(long id);

    /**
     * Finds a channel matching either the raw or the normalized URL variant.
     * Ordered by id ASC so that, while duplicate channels from before URL
     * normalization still exist, the oldest row (lowest id) deterministically wins.
     * This method must be called on a background thread.
     *
     * @param url           the raw URL to match
     * @param normalizedUrl the normalized URL variant to match
     * @return the first matching channel, or null when none exists
     */
    @Query("SELECT * FROM rss_channel WHERE url = :url OR url = :normalizedUrl ORDER BY id ASC LIMIT 1")
    public abstract RssChannel findRssChannelByUrlVariants(String url, String normalizedUrl);

    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId")
    public abstract List<RssItem> findRssItemsByChannelId(long channelId);

    /**
     * Finds the UNREAD rss items of a channel that were last touched by a sync at or
     * after the given time, i.e. rows whose {@code updated_date_time} is at or after
     * {@code sinceMillis}. Backs the sync notification guard (issue #37): since
     * {@link #upsertRssItemsForChannel(RssChannel, List)} retains items that fall out
     * of a feed instead of deleting them, counting ALL unread items of a channel
     * would re-notify forever with a growing count. {@code updated_date_time} doubles
     * as "last seen in a feed sync" for sync-touched rows, so this query returns
     * exactly what the just-finished sync touched. Note that
     * {@link #updateRssItem(RssItem)} (manual item edit) also stamps
     * {@code updated_date_time}, so a manually-edited retained item can appear in one
     * notification pass - accepted edge; the by-link state updates
     * {@link #updateRssItemsIsReadByLink(boolean, String)} and
     * {@link #updateRssItemsIsFavoriteByLink(boolean, String)} do NOT stamp, so
     * read/favorite toggles never resurrect an item into the notification window.
     * This method must be called on a background thread.
     *
     * @param channelId   the channel id of the items to find
     * @param sinceMillis epoch millis lower bound (inclusive) on updated_date_time;
     *                    pass the sync start captured by the sync worker
     * @return the channel's unread items touched at or after the given time
     */
    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId AND is_read = 0 AND updated_date_time >= :sinceMillis")
    public abstract List<RssItem> findUnreadRssItemsByChannelIdUpdatedSince(long channelId, long sinceMillis);

    /**
     * Finds the rss items of a channel that were last touched by a sync at or after
     * the given time, read or unread. The unfiltered counterpart of
     * {@link #findUnreadRssItemsByChannelIdUpdatedSince(long, long)} - backs the sync
     * change-notifier payload (issue #37) so the home list only re-processes rows the
     * just-finished sync actually touched, and retained items (whose
     * {@code updated_date_time} keeps the stamp of the last sync that carried them)
     * are not announced as new after the upgrade. See that method for the full
     * "last seen in a feed sync" semantics of {@code updated_date_time}.
     * This method must be called on a background thread.
     *
     * @param channelId   the channel id of the items to find
     * @param sinceMillis epoch millis lower bound (inclusive) on updated_date_time;
     *                    pass the sync start captured by the sync worker
     * @return the channel's items touched at or after the given time
     */
    @Query("SELECT * FROM rss_item WHERE channel_id = :channelId AND updated_date_time >= :sinceMillis")
    public abstract List<RssItem> findRssItemsByChannelIdUpdatedSince(long channelId, long sinceMillis);

    @Query("SELECT COUNT(id) FROM rss_item")
    public abstract int countRssItem();

    @Query("SELECT COUNT(id) FROM rss_item WHERE is_read = 0 AND channel_id = :channelId")
    public abstract int countUnReadRssItems(long channelId);

    @Transaction
    public void insertRssChannel(RssChannel rssChannel, RssItem... rssItems) {
        if (rssChannel.createdDateTime == null) {
            Date date = new Date();
            rssChannel.createdDateTime = date;
            rssChannel.updatedDateTime = date;
        }
        long channelId = insert(rssChannel);
        rssChannel.id = channelId;
        if (rssItems != null && rssItems.length > 0) {
            for (RssItem rssItem : rssItems) {
                rssItem.channelId = channelId;
            }
            insertRssItem(rssItems);
        }
    }

    @Transaction
    public void insertRssItem(RssItem... rssItems) {
        for (RssItem rssItem : rssItems) {
            if (rssItem.createdDateTime == null) {
                Date date = new Date();
                rssItem.createdDateTime = date;
                rssItem.updatedDateTime = date;
            }
            rssItem.id = insert(rssItem);
        }
    }

    @Transaction
    public void updateRssItem(RssItem rssItem) {
        rssItem.updatedDateTime = new Date();
        update(rssItem);
    }

    /**
     * Atomically upserts the parsed feed items of an existing channel, replacing the
     * former delete-all-and-reinsert behavior of {@code updateRssChannel} (issue #37).
     * Retention semantics: an item that falls out of the feed's current list is NOT
     * deleted - it is retained in the database with its read/favorite state, its
     * original id and its original timestamps, so history survives every sync (and
     * item ids stay stable, which keeps detail-page reload-by-id working and stops
     * the per-sync delete+reinsert row churn).
     * <p>
     * Matching, decided inside the transaction:
     * <ul>
     * <li>Items with a VALID link (non-null, non-empty) match an existing channel item
     * by exact link. A match is updated IN PLACE: the feed-provided fields (title,
     * link, description, pubDate, mediaImage, mediaVideo, categories) are overwritten
     * from the parsed item while id, isRead, isFavorite and createdDateTime are
     * preserved, and updatedDateTime is refreshed. No match means INSERT with
     * createdDateTime = updatedDateTime = now (the same null-stamp pattern as
     * {@link #insertRssItem(RssItem...)}).</li>
     * <li>Items with an INVALID link (null or empty) can never match by link, so they
     * match existing invalid-link channel items by the null-safe tuple key
     * {@code title + '\u0000' + pubDate-millis (-1 when null)} and get the same
     * update-in-place / insert treatment. This keeps linkless items from duplicating
     * on every sync (the parser yields "" links for RSS items with an EMPTY
     * &lt;link/&gt; element, null for items with NO &lt;link&gt; element, and null
     * for Atom entries without an href - null and "" share one matching bucket) -
     * under the former delete+reinsert merge, a linkless favorite was reinserted
     * with a fresh id on every sync and accumulated forever. Accepted edge: several
     * DISTINCT linkless items that also share a null/empty title and a null pubDate
     * collapse onto a single row (key "\u0000-1") - bounded loss, stable across
     * syncs.</li>
     * <li>The match maps are maintained while the batch is processed, so a duplicate
     * link (or duplicate invalid-link key) WITHIN one parsed batch updates the row
     * persisted by the earlier copy instead of inserting it twice.</li>
     * </ul>
     * Unmatched database items are left COMPLETELY untouched - nothing is deleted
     * here (deleting a feed's history remains {@link #deleteRssChannel(RssChannel)}'s
     * job). As a consequence, {@code updated_date_time} for sync-touched items now
     * doubles as "last seen in a feed sync": updated and inserted rows get the
     * current stamp while retained rows keep the stamp of the last sync that carried
     * them. The chained notification workers exploit this by only considering rows
     * with {@code updated_date_time >= syncStart} (see
     * {@link #findUnreadRssItemsByChannelIdUpdatedSince(long, long)}), so retained
     * items are not re-announced as unread forever after the upgrade.
     * <p>
     * The SELECT-then-decide pattern (instead of SQLite's ON CONFLICT upsert) is
     * deliberate: upsert and row-values are unavailable on the framework SQLite
     * shipped with the minimum SDK (API 21), and the state merge needs the existing
     * row's values (preserve isRead/isFavorite/createdDateTime) anyway.
     * Note that {@link #updateRssItem(RssItem)} (manual item edit) also stamps
     * {@code updated_date_time}, so a manually-edited retained item can appear in one
     * notification pass - accepted edge; the by-link state updates
     * {@link #updateRssItemsIsReadByLink(boolean, String)} and
     * {@link #updateRssItemsIsFavoriteByLink(boolean, String)} do NOT stamp, so
     * read/favorite toggles never resurrect an item into the notification window.
     * This method must be called on a background thread.
     *
     * @param rssChannel  the existing channel to update (id must be set);
     *                    updatedDateTime is refreshed like the former updateRssChannel
     * @param parsedItems the freshly parsed feed items, in feed order
     * @return the merged persisted rows in authoritative database form: updated and
     * inserted items first (in parsed order), followed by the retained items the
     * feed no longer carries (in database order). One row per DISTINCT item -
     * intra-batch duplicates sharing a link (or an invalid-link tuple key) collapse
     * onto the single persisted row they updated and appear once.
     */
    @Transaction
    public List<RssItem> upsertRssItemsForChannel(RssChannel rssChannel, List<RssItem> parsedItems) {
        Date now = new Date();
        rssChannel.updatedDateTime = now;
        update(rssChannel);
        List<RssItem> existingItems = findRssItemsByChannelId(rssChannel.id);
        HashMap<String, RssItem> existingByLink = new HashMap<>();
        HashMap<String, RssItem> existingByInvalidKey = new HashMap<>();
        if (existingItems != null) {
            for (RssItem existingItem : existingItems) {
                if (hasValidLink(existingItem)) {
                    existingByLink.put(existingItem.link, existingItem);
                } else {
                    existingByInvalidKey.put(invalidLinkKey(existingItem), existingItem);
                }
            }
        }
        HashSet<Long> persistedBatchIds = new HashSet<>();
        ArrayList<RssItem> persistedItems = new ArrayList<>();
        if (parsedItems != null) {
            for (RssItem parsedItem : parsedItems) {
                RssItem persisted;
                if (hasValidLink(parsedItem)) {
                    RssItem matched = existingByLink.get(parsedItem.link);
                    persisted = matched != null
                            ? updateMatchedItem(matched, parsedItem, now)
                            : insertItem(rssChannel.id, parsedItem, now);
                    existingByLink.put(persisted.link, persisted);
                } else {
                    String key = invalidLinkKey(parsedItem);
                    RssItem matched = existingByInvalidKey.get(key);
                    persisted = matched != null
                            ? updateMatchedItem(matched, parsedItem, now)
                            : insertItem(rssChannel.id, parsedItem, now);
                    existingByInvalidKey.put(invalidLinkKey(persisted), persisted);
                }
                // Only the FIRST persisted row per distinct id enters the returned
                // list: two parsed items sharing a link (or an invalid-link tuple
                // key) resolve to the same row, and appending it twice would
                // misreport the merge. The set also anchors the retained-row
                // filtering below (retained = existing rows never persisted here).
                if (persisted.id != null && persistedBatchIds.add(persisted.id)) {
                    persistedItems.add(persisted);
                }
            }
        }
        // Retained = existing rows the parsed feed no longer carries. Left untouched:
        // no field, stamp or state is written, keeping "last seen in feed" intact.
        ArrayList<RssItem> mergedItems = new ArrayList<>(persistedItems);
        if (existingItems != null) {
            for (RssItem existingItem : existingItems) {
                if (!persistedBatchIds.contains(existingItem.id)) {
                    mergedItems.add(existingItem);
                }
            }
        }
        return mergedItems;
    }

    /**
     * Overwrites the feed-provided fields of an existing (matched) row from the
     * parsed item, preserving id, isRead, isFavorite and createdDateTime, and
     * refreshes updatedDateTime. Shared by the valid-link and invalid-link match
     * paths of {@link #upsertRssItemsForChannel(RssChannel, List)}.
     *
     * @param matched    the existing database row to update in place
     * @param parsedItem the parsed item carrying the fresh feed fields
     * @param now        the shared sync timestamp for updated_date_time
     * @return the updated row (same instance as {@code matched})
     */
    private RssItem updateMatchedItem(RssItem matched, RssItem parsedItem, Date now) {
        matched.title = parsedItem.title;
        matched.link = parsedItem.link;
        matched.description = parsedItem.description;
        matched.pubDate = parsedItem.pubDate;
        matched.mediaImage = parsedItem.mediaImage;
        matched.mediaVideo = parsedItem.mediaVideo;
        matched.categories = parsedItem.categories;
        matched.updatedDateTime = now;
        update(matched);
        return matched;
    }

    /**
     * Inserts a parsed item that matched no existing row, stamping
     * createdDateTime = updatedDateTime = now (the {@link #insertRssItem(RssItem...)}
     * null-stamp pattern, applied unconditionally because a parsed item never
     * carries stamps). Shared by the valid-link and invalid-link insert paths of
     * {@link #upsertRssItemsForChannel(RssChannel, List)}.
     *
     * @param channelId  the channel the item belongs to
     * @param parsedItem the parsed item to insert
     * @param now        the shared sync timestamp
     * @return the inserted row with its generated id
     */
    private RssItem insertItem(long channelId, RssItem parsedItem, Date now) {
        parsedItem.channelId = channelId;
        parsedItem.createdDateTime = now;
        parsedItem.updatedDateTime = now;
        parsedItem.id = insert(parsedItem);
        return parsedItem;
    }

    /**
     * True when the item's link is usable as a match key (non-null, non-empty).
     * Rows failing this test are matched by the
     * {@link #invalidLinkKey(RssItem)} tuple instead.
     */
    private static boolean hasValidLink(RssItem rssItem) {
        return rssItem.link != null && !rssItem.link.isEmpty();
    }

    /**
     * Null-safe tuple key for matching INVALID-link (null or empty) items:
     * {@code title + '\u0000' + pubDate-millis}, the millis being -1 when the pub
     * date is null. The '\u0000' separator cannot occur in real feed text, so
     * distinct (title, pubDate) pairs always produce distinct keys (plain
     * concatenation could collide: title "ab" with no date vs title "ab-" with
     * date 1). Empty and null links deliberately share one matching space: the
     * parser yields "" for an RSS item with an EMPTY &lt;link/&gt; element, null
     * for an item with NO &lt;link&gt; element, and null for Atom entries without
     * an href - all describe the same "this item has no link" state. Degenerate
     * case: two distinct linkless items that also share a null/empty title and a
     * null pubDate share the key "\u0000-1" and collapse onto one row (accepted,
     * bounded, stable across syncs).
     */
    private static String invalidLinkKey(RssItem rssItem) {
        String title = rssItem.title != null ? rssItem.title : "";
        long pubDateMillis = rssItem.pubDate != null ? rssItem.pubDate.getTime() : -1L;
        return title + '\u0000' + pubDateMillis;
    }

    @Insert
    protected abstract long insert(RssChannel rssChannel);

    @Insert
    protected abstract long insert(RssItem rssItem);

    @Update
    public abstract void update(RssChannel rssChannel);

    @Update
    protected abstract void update(RssItem rssItem);

    @Delete
    protected abstract void delete(RssChannel rssChannel);

    @Query("DELETE FROM rss_item WHERE channel_id = :rssChannelId")
    public abstract void deleteRssItemsByChannelId(long rssChannelId);

    @Transaction
    public void deleteRssChannel(RssChannel rssChannel) {
        delete(rssChannel);
        deleteRssItemsByChannelId(rssChannel.id);
    }

    @Query("SELECT * FROM rss_item WHERE id = :rssItemId")
    public abstract RssItem findRssItemById(long rssItemId);

    @Query("UPDATE rss_item SET is_read = :isRead WHERE link = :link")
    public abstract void updateRssItemsIsReadByLink(boolean isRead, String link);

    @Query("SELECT channel_id, COUNT(id) as cnt FROM rss_item WHERE is_read = 0 GROUP BY channel_id")
    public abstract List<ChannelUnreadCount> countUnReadRssItemsByChannel();

    /**
     * Loads one keyset-pagination page of rss items filtered by optional channel,
     * read and favorite state, newest first. A null cursor loads the first page;
     * otherwise the page starts strictly after the cursor row in the list's order,
     * which keeps page boundaries stable under concurrent inserts (no LIMIT-offset
     * duplicates or skips).
     * <p>
     * The cursor predicate spells out the tuple comparison of the list's two sort
     * keys - (COALESCE(pub_date, created_date_time), id) &lt; cursor - because SQLite
     * row-values are unavailable on the framework SQLite shipped with old API levels.
     * The second sort key is id, not created_date_time: created_date_time is not
     * unique (same-millisecond inserts happen when a sync batch is persisted), and a
     * non-unique tiebreaker makes the strict cursor comparison skip every row that
     * shares the boundary value on the next page. id is the unique autoincrement
     * primary key, so every row has a well-defined position. The visible order is
     * unchanged by this choice: created_date_time is assigned at insert time in id
     * order (see {@link #insertRssItem(RssItem...)}), so rows without a feed pub
     * date keep their insertion order either way.
     * This method must be called on a background thread.
     *
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded row in epoch millis, null to load the first page
     * @param afterId      id of the last already-loaded row, ignored when afterSortKey is null
     * @param limit        maximum number of items to return
     * @return list of rss items matching the filters, newest first,
     * continuing after the cursor row
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) < :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id < :afterId)) ORDER BY COALESCE(pub_date, created_date_time) DESC, id DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsPageDesc(Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Loads one keyset-pagination page of rss items filtered by optional channel,
     * read and favorite state, oldest first. A null cursor loads the first page;
     * otherwise the page starts strictly after the cursor row in the list's order.
     * See {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)} for
     * the cursor mechanics and the id-tiebreaker rationale.
     * This method must be called on a background thread.
     *
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded row in epoch millis, null to load the first page
     * @param afterId      id of the last already-loaded row, ignored when afterSortKey is null
     * @param limit        maximum number of items to return
     * @return list of rss items matching the filters, oldest first,
     * continuing after the cursor row
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) > :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id > :afterId)) ORDER BY COALESCE(pub_date, created_date_time) ASC, id ASC LIMIT :limit")
    public abstract List<RssItem> findRssItemsPageAsc(Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Loads one keyset-pagination page of rss items GROUPED BY LINK, filtered by
     * optional channel, read and favorite state, newest first. Used by the
     * all-channels view of the home list (issue #45): rows sharing a link - the
     * same story syndicated by several feeds - collapse into ONE list row, the
     * group's representative, so each story appears once no matter how many feeds
     * carry it. The non-grouped counterpart is
     * {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)}.
     * <p>
     * Grouping expression: {@code COALESCE(NULLIF(link, ''), 'rss_item#' || id)}.
     * A plain GROUP BY link would collapse ALL null-link rows into a single group
     * and all empty-string links into another one (GROUP BY treats NULLs as one
     * group, and '' = '') - the same trap the {@link #markOldItemsRead(long)} query
     * guards against. NULLIF(link,'') rewrites the empty string to NULL first so
     * the COALESCE falls through to the per-row-unique {@code 'rss_item#' || id}
     * sentinel, giving every invalid-link row its own singleton group; valid links
     * pass through unchanged.
     * <p>
     * Representative row = the group's NEWEST copy (tiebreak: highest id). The
     * single 1-argument MAX aggregate over the zero-padded composite string
     * {@code printf('%015d_%015d', max(COALESCE(pub_date, created_date_time), 0), id)}
     * activates SQLite's bare-column special case (needs SQLite &ge; 3.7.11; API 21
     * ships 3.8.4.3, so this is safe): every bare column of the result - id, link,
     * title, the effective date, ... - is taken from the row that produced the
     * maximum, making the representative fully deterministic. Both decimal fields
     * are zero-padded to 15 digits so the BINARY string comparison matches the
     * intended numeric (effectiveDate, id) comparison; the inner 2-ARGUMENT max is
     * SQLite's scalar max, clamping pre-1970 (negative epoch) dates to 0 so no
     * minus sign can break the zero-padding alignment. NO window function is used:
     * those require SQLite 3.25 / API 30.
     * <p>
     * The HAVING clause carries the keyset cursor predicate against the
     * representative's bare columns (the COALESCE expression is spelled out again -
     * the select alias cannot be referenced there): groups whose representative
     * sits at or before the cursor position in the list's order are skipped. See
     * {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)} for
     * the cursor mechanics and the id-tiebreaker rationale; ORDER BY and cursor use
     * the exact same two keys, so keyset pagination walks the collapsed list
     * without repeating or skipping groups across page boundaries.
     * <p>
     * Filters apply at COPY level BEFORE grouping: {@code is_read = :isRead} and
     * {@code is_favorite = :isFavorite} restrict the candidate copies first, so a
     * group survives when at least one matching copy exists and its representative
     * is the newest MATCHING copy. This query is only meant for the all-channels
     * view (grouping is meaningless within a single channel where a link is
     * already unique after MIGRATION_8_9); the channelId parameter exists so the
     * command can keep one query shape.
     * <p>
     * The select's extra {@code rep_sort_key} column is the aggregate that anchors
     * the bare-column special case; it is deliberately NOT a field of
     * {@link RssItem}, hence the RoomWarnings.QUERY_MISMATCH suppression. Room
     * ignores unmapped cursor columns. The suppression (instead of
     * &#64;RewriteQueriesToDropUnusedColumns) is deliberate: a query rewrite that
     * drops the MAX aggregate would silently disable the bare-column special case.
     * This method must be called on a background thread.
     *
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded group representative in epoch millis,
     *                     null to load the first page
     * @param afterId      id of the last already-loaded group representative,
     *                     ignored when afterSortKey is null
     * @param limit        maximum number of groups to return
     * @return one representative per link group matching the filters, newest first,
     * continuing after the cursor group
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, MAX(printf('%015d_%015d', max(COALESCE(pub_date, created_date_time), 0), id)) AS rep_sort_key FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) GROUP BY COALESCE(NULLIF(link, ''), 'rss_item#' || id) HAVING (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) < :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id < :afterId)) ORDER BY COALESCE(pub_date, created_date_time) DESC, id DESC LIMIT :limit")
    public abstract List<RssItem> findRssItemsPageGroupedByLinkDesc(Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Loads one keyset-pagination page of rss items GROUPED BY LINK, oldest first -
     * the ASC mirror of
     * {@link #findRssItemsPageGroupedByLinkDesc(Long, Integer, Integer, Long, Long, int)}.
     * See that method for the full rationale of the grouping expression, the
     * printf-based deterministic representative, the copy-level filters and the
     * HAVING-carried keyset cursor; only the comparison directions differ.
     * This method must be called on a background thread.
     *
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded group representative in epoch millis,
     *                     null to load the first page
     * @param afterId      id of the last already-loaded group representative,
     *                     ignored when afterSortKey is null
     * @param limit        maximum number of groups to return
     * @return one representative per link group matching the filters, oldest first,
     * continuing after the cursor group
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, MAX(printf('%015d_%015d', max(COALESCE(pub_date, created_date_time), 0), id)) AS rep_sort_key FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) GROUP BY COALESCE(NULLIF(link, ''), 'rss_item#' || id) HAVING (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) > :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id > :afterId)) ORDER BY COALESCE(pub_date, created_date_time) ASC, id ASC LIMIT :limit")
    public abstract List<RssItem> findRssItemsPageGroupedByLinkAsc(Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Finds the rss item directly older than the given anchor position, using the
     * same optional filters and ordering as
     * {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)}.
     * The compound predicate spells out the tuple comparison of the list's two sort
     * keys - (COALESCE(pub_date, created_date_time), id) &lt; anchor - because SQLite
     * row-values are unavailable on the framework SQLite shipped with old API levels.
     * The tiebreaker is id, not created_date_time: created_date_time is not unique
     * (same-millisecond inserts happen when a sync batch is persisted), which made
     * neighbor navigation skip same-millisecond rows; id is the unique autoincrement
     * primary key, and the list queries' secondary ORDER BY uses the same key, so
     * navigation walks exactly the visible list order.
     * Returns the anchor's next row in the newest-first list order.
     * This method must be called on a background thread.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorDate the anchor item's effective date (pub_date, falling back to
     *                   created_date_time) in epoch millis
     * @param anchorId   the anchor item's id (unique autoincrement primary key)
     * @return the closest older rss item, or null when the anchor is the oldest matching item
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (COALESCE(pub_date, created_date_time) < :anchorDate OR (COALESCE(pub_date, created_date_time) = :anchorDate AND id < :anchorId)) ORDER BY COALESCE(pub_date, created_date_time) DESC, id DESC LIMIT 1")
    public abstract RssItem findOlderRssItem(Long channelId, Integer isRead, Integer isFavorite, long anchorDate, long anchorId);

    /**
     * Finds the rss item directly newer than the given anchor position, using the
     * same optional filters and ordering as
     * {@link #findRssItemsPageAsc(Long, Integer, Integer, Long, Long, int)}.
     * The compound predicate spells out the tuple comparison of the list's two sort
     * keys - (COALESCE(pub_date, created_date_time), id) &gt; anchor - because SQLite
     * row-values are unavailable on the framework SQLite shipped with old API levels.
     * The tiebreaker is id, not created_date_time: created_date_time is not unique
     * (same-millisecond inserts happen when a sync batch is persisted), which made
     * neighbor navigation skip same-millisecond rows; id is the unique autoincrement
     * primary key, and the list queries' secondary ORDER BY uses the same key, so
     * navigation walks exactly the visible list order.
     * Returns the anchor's next row in the oldest-first list order.
     * This method must be called on a background thread.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorDate the anchor item's effective date (pub_date, falling back to
     *                   created_date_time) in epoch millis
     * @param anchorId   the anchor item's id (unique autoincrement primary key)
     * @return the closest newer rss item, or null when the anchor is the newest matching item
     */
    @Query("SELECT * FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (COALESCE(pub_date, created_date_time) > :anchorDate OR (COALESCE(pub_date, created_date_time) = :anchorDate AND id > :anchorId)) ORDER BY COALESCE(pub_date, created_date_time) ASC, id ASC LIMIT 1")
    public abstract RssItem findNewerRssItem(Long channelId, Integer isRead, Integer isFavorite, long anchorDate, long anchorId);

    /**
     * Finds the link-GROUP directly older than the given anchor position - the
     * grouped counterpart of
     * {@link #findOlderRssItem(Long, Integer, Integer, long, long)} used by the
     * item detail page when the home list shows the collapsed all-channels view
     * (issue #45). GROUP BY expression, printf-anchored deterministic
     * representative (newest copy of the group, tiebreak by id) and copy-level
     * filters are EXACTLY the ones of
     * {@link #findRssItemsPageGroupedByLinkDesc(Long, Integer, Integer, Long, Long, int)}
     * - only the keyset predicate is anchored at the current item instead of a
     * page cursor. Because every key matches, navigation walks exactly the
     * collapsed list order: the returned group is the previous visible row of the
     * grouped list, and its representative is what the list itself displayed.
     * The anchor passed in must itself be a representative row of the grouped
     * list (its effective date + id), otherwise the walk lands between groups.
     * This method must be called on a background thread.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorDate the anchor's effective date (pub_date, falling back to
     *                   created_date_time) in epoch millis
     * @param anchorId   the anchor's id (unique autoincrement primary key)
     * @return the representative of the closest older link group, or null when the
     * anchor is the oldest visible group
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, MAX(printf('%015d_%015d', max(COALESCE(pub_date, created_date_time), 0), id)) AS rep_sort_key FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) GROUP BY COALESCE(NULLIF(link, ''), 'rss_item#' || id) HAVING (COALESCE(pub_date, created_date_time) < :anchorDate OR (COALESCE(pub_date, created_date_time) = :anchorDate AND id < :anchorId)) ORDER BY COALESCE(pub_date, created_date_time) DESC, id DESC LIMIT 1")
    public abstract RssItem findOlderRssItemGroupedByLink(Long channelId, Integer isRead, Integer isFavorite, long anchorDate, long anchorId);

    /**
     * Finds the link-GROUP directly newer than the given anchor position - the
     * grouped counterpart of
     * {@link #findNewerRssItem(Long, Integer, Integer, long, long)}, ASC mirror of
     * {@link #findOlderRssItemGroupedByLink(Long, Integer, Integer, long, long)}.
     * GROUP BY expression, printf-anchored deterministic representative
     * (newest copy of the group, tiebreak by id) and copy-level filters are EXACTLY
     * the ones of
     * {@link #findRssItemsPageGroupedByLinkAsc(Long, Integer, Integer, Long, Long, int)},
     * so navigation walks exactly the collapsed list order: the returned group is
     * the next visible row of the grouped list. The anchor passed in must itself be
     * a representative row of the grouped list (its effective date + id), otherwise
     * the walk lands between groups.
     * This method must be called on a background thread.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorDate the anchor's effective date (pub_date, falling back to
     *                   created_date_time) in epoch millis
     * @param anchorId   the anchor's id (unique autoincrement primary key)
     * @return the representative of the closest newer link group, or null when the
     * anchor is the newest visible group
     */
    @SuppressWarnings(RoomWarnings.QUERY_MISMATCH)
    @Query("SELECT *, MAX(printf('%015d_%015d', max(COALESCE(pub_date, created_date_time), 0), id)) AS rep_sort_key FROM rss_item WHERE (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) GROUP BY COALESCE(NULLIF(link, ''), 'rss_item#' || id) HAVING (COALESCE(pub_date, created_date_time) > :anchorDate OR (COALESCE(pub_date, created_date_time) = :anchorDate AND id > :anchorId)) ORDER BY COALESCE(pub_date, created_date_time) ASC, id ASC LIMIT 1")
    public abstract RssItem findNewerRssItemGroupedByLink(Long channelId, Integer isRead, Integer isFavorite, long anchorDate, long anchorId);

    /**
     * Searches one keyset-pagination page of rss items matching the given query
     * against title, description, or categories, filtered by optional channel, read
     * and favorite state, newest first. A null cursor loads the first page; otherwise
     * the page starts strictly after the cursor row in the list's order. See
     * {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)} for the
     * cursor mechanics and the id-tiebreaker rationale.
     * The query must be pre-escaped for LIKE wildcards by the caller.
     * This method must be called on a background thread.
     *
     * @param query        pre-escaped search query matched against title, description, and categories
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded row in epoch millis, null to load the first page
     * @param afterId      id of the last already-loaded row, ignored when afterSortKey is null
     * @param limit        maximum number of items to return
     * @return list of rss items matching the query, newest first,
     * continuing after the cursor row
     */
    @Query("SELECT * FROM rss_item WHERE (title LIKE '%' || :query || '%' ESCAPE '\\' OR description LIKE '%' || :query || '%' ESCAPE '\\' OR categories LIKE '%' || :query || '%' ESCAPE '\\') AND (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) < :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id < :afterId)) ORDER BY COALESCE(pub_date, created_date_time) DESC, id DESC LIMIT :limit")
    public abstract List<RssItem> searchRssItemsPageDesc(String query, Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Searches one keyset-pagination page of rss items matching the given query
     * against title, description, or categories, filtered by optional channel, read
     * and favorite state, oldest first. A null cursor loads the first page; otherwise
     * the page starts strictly after the cursor row in the list's order. See
     * {@link #findRssItemsPageDesc(Long, Integer, Integer, Long, Long, int)} for the
     * cursor mechanics and the id-tiebreaker rationale.
     * The query must be pre-escaped for LIKE wildcards by the caller.
     * This method must be called on a background thread.
     *
     * @param query        pre-escaped search query matched against title, description, and categories
     * @param channelId    optional channel id filter, null to include all channels
     * @param isRead       optional read-state filter, null to include read and unread items
     * @param isFavorite   optional favorite filter, null to include favorite and non-favorite items
     * @param afterSortKey COALESCE(pub_date, created_date_time) of the last
     *                     already-loaded row in epoch millis, null to load the first page
     * @param afterId      id of the last already-loaded row, ignored when afterSortKey is null
     * @param limit        maximum number of items to return
     * @return list of rss items matching the query, oldest first,
     * continuing after the cursor row
     */
    @Query("SELECT * FROM rss_item WHERE (title LIKE '%' || :query || '%' ESCAPE '\\' OR description LIKE '%' || :query || '%' ESCAPE '\\' OR categories LIKE '%' || :query || '%' ESCAPE '\\') AND (:channelId IS NULL OR channel_id = :channelId) AND (:isRead IS NULL OR is_read = :isRead) AND (:isFavorite IS NULL OR is_favorite = :isFavorite) AND (:afterSortKey IS NULL OR COALESCE(pub_date, created_date_time) > :afterSortKey OR (COALESCE(pub_date, created_date_time) = :afterSortKey AND id > :afterId)) ORDER BY COALESCE(pub_date, created_date_time) ASC, id ASC LIMIT :limit")
    public abstract List<RssItem> searchRssItemsPageAsc(String query, Long channelId, Integer isRead, Integer isFavorite, Long afterSortKey, Long afterId, int limit);

    /**
     * Marks all rss items as read.
     * This method must be called on a background thread.
     */
    @Query("UPDATE rss_item SET is_read = 1 WHERE is_read = 0")
    public abstract void markAllRssItemsRead();

    /**
     * Marks all rss items of the given channel as read.
     * This method must be called on a background thread.
     *
     * @param channelId the channel id of the items to mark as read
     */
    @Query("UPDATE rss_item SET is_read = 1 WHERE channel_id = :channelId AND is_read = 0")
    public abstract void markRssItemsReadByChannelId(long channelId);

    /**
     * Marks unread rss items as read when their link matches one of the given links.
     * Matching is done by link (not id) so cross-channel duplicates stay consistent,
     * same as {@link #updateRssItemsIsReadByLink(boolean, String)} and
     * {@link #markOldItemsRead(long)}. Rows with a null or empty link can never match
     * and are explicitly excluded by the query.
     * This method must be called on a background thread.
     *
     * @param links the links of the rss items to mark as read
     * @return the number of rows marked as read for this chunk
     */
    @Query("UPDATE rss_item SET is_read = 1 " +
            "WHERE is_read = 0 AND link IS NOT NULL AND link != '' " +
            "AND link IN (:links)")
    protected abstract int markItemsReadByLinksChunk(List<String> links);

    /**
     * Marks unread rss items as read when their link matches one of the given links,
     * splitting the links into chunks of {@link #MARK_READ_BY_LINKS_CHUNK_SIZE}.
     * Chunking is required because SQLite older than 3.32 (Android 11 and below)
     * allows at most 999 host parameters per query while the caller's keyset page
     * size is 100 (BaseRssItemsCmd.PAGE_SIZE) and the full item table can be much
     * larger. link matches go through the link index (MIGRATION_11_12); before
     * that index existed each UPDATE was a full table scan, so chunking also
     * bounded the work per statement. The {@code is_read = 0} guard makes
     * repeated calls idempotent.
     * This method must be called on a background thread.
     *
     * @param links the links of the rss items to mark as read
     * @return the total number of rows marked as read
     */
    @Transaction
    public int markItemsReadByLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return 0;
        }
        int totalRows = 0;
        for (int i = 0; i < links.size(); i += MARK_READ_BY_LINKS_CHUNK_SIZE) {
            int end = Math.min(i + MARK_READ_BY_LINKS_CHUNK_SIZE, links.size());
            totalRows += markItemsReadByLinksChunk(links.subList(i, end));
        }
        return totalRows;
    }

    /**
     * Counts, per link, how many rss_item rows (channel copies) carry it. Backs the
     * "Also in N feeds" duplicate badge of the collapsed all-channels list (issue
     * #45): each representative row of
     * {@link #findRssItemsPageGroupedByLinkDesc(Long, Integer, Integer, Long, Long, int)}
     * reports the absolute number of copies its link has across ALL channels,
     * regardless of any active read/favorite filter.
     * <p>
     * Null/empty input entries are dropped BEFORE the query: a null link can never
     * match the IN clause anyway, but an empty-string link WOULD match and count
     * every empty-link row of the table as copies of each other - they are not
     * (see the singleton-group rationale on
     * {@link #findRssItemsPageGroupedByLinkDesc(Long, Integer, Integer, Long, Long, int)}).
     * Returns an empty list for null/empty input.
     * <p>
     * The links are chunked at {@link #COUNT_BY_LINKS_CHUNK_SIZE} per statement for
     * the same host-parameter ceiling as {@link #markItemsReadByLinks(List)} - the
     * caller's window can exceed 1000 links after a deep scroll in REFRESH mode,
     * and SQLite older than 3.32 (Android 11 and below) allows at most 999 host
     * parameters per query.
     * This method must be called on a background thread.
     *
     * @param links the links to count copies for, may contain null/empty entries
     *              (ignored)
     * @return one {@link LinkCount} per distinct valid link that has at least one
     * row, empty when no input link matches
     */
    @Transaction
    public List<LinkCount> countRssItemsByLinks(List<String> links) {
        if (links == null || links.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<String> validLinks = new ArrayList<>(links.size());
        for (String link : links) {
            if (link != null && !link.isEmpty()) {
                validLinks.add(link);
            }
        }
        if (validLinks.isEmpty()) {
            return Collections.emptyList();
        }
        List<LinkCount> result = new ArrayList<>();
        for (int i = 0; i < validLinks.size(); i += COUNT_BY_LINKS_CHUNK_SIZE) {
            int end = Math.min(i + COUNT_BY_LINKS_CHUNK_SIZE, validLinks.size());
            List<LinkCount> chunkResult =
                    countRssItemsByLinksChunk(validLinks.subList(i, end));
            if (chunkResult != null && !chunkResult.isEmpty()) {
                result.addAll(chunkResult);
            }
        }
        return result;
    }

    /**
     * Counts copies for one chunk of at most {@link #COUNT_BY_LINKS_CHUNK_SIZE}
     * links. Protected chunk method of {@link #countRssItemsByLinks(List)}, which
     * owns the null/empty filtering and the chunk loop.
     * This method must be called on a background thread.
     *
     * @param links the non-empty links of this chunk
     * @return one {@link LinkCount} per link of the chunk that has at least one row
     */
    @Query("SELECT link, COUNT(*) AS cnt FROM rss_item WHERE link IN (:links) GROUP BY link")
    protected abstract List<LinkCount> countRssItemsByLinksChunk(List<String> links);

    /**
     * Auto mark-read (unread retention window): marks unread, non-favorite rss items
     * as read when they are old. An item is old when
     * COALESCE(pub_date, created_date_time) is strictly before the given cutoff.
     * The update is link-consistent with {@link #updateRssItemsIsReadByLink(boolean, String)}:
     * every row whose link belongs to an old-dated row is marked too, so cross-channel
     * duplicates stay in sync. Rows with a null or empty link are explicitly excluded -
     * a null link can never match the IN subquery and all empty-link rows would
     * wrongly couple together.
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are affected
     * @return the number of rows marked as read
     */
    @Query("UPDATE rss_item SET is_read = 1 " +
            "WHERE is_read = 0 AND is_favorite = 0 " +
            "AND link IS NOT NULL AND link != '' " +
            "AND link IN (" +
            "SELECT link FROM rss_item " +
            "WHERE COALESCE(pub_date, created_date_time) < :cutoffMillis " +
            "AND link IS NOT NULL AND link != '')")
    public abstract int markOldItemsRead(long cutoffMillis);

    /**
     * Prunes the read-item archive (issue #37 phase 2): deletes read, non-favorite
     * rss items whose {@code updated_date_time} is strictly before the given cutoff.
     * The criterion is the upsert retention's "last seen in feed" stamp, NOT the
     * publication date: {@link #upsertRssItemsForChannel(RssChannel, List)} re-stamps
     * every item still carried by its feed on every sync, so items still present in
     * a feed are never pruned - only items that fell out of a feed and then aged out
     * get deleted. Pruning by pub_date would delete items still in the feed, which
     * would come back UNREAD on the next sync and re-notify (the pre-retention
     * reinsertion trap). Unread items are NEVER deleted: compose with the unread
     * retention window {@link #markOldItemsRead(long)}, which ages unread to read at
     * the end of a sync - this prune then removes them in a later sync once aged.
     * Favorites are NEVER deleted, whatever their age. The scope is deliberately
     * PER ROW, not per link: the same story syndicated in two feeds has per-copy
     * last-seen stamps, so one copy can be pruned while the other is retained -
     * each copy ages by its own feed's behavior. Only read, non-favorite rows are
     * ever affected, so no unread resurrection or state corruption is possible.
     * Paused channels are included
     * on purpose: the setting is a global opt-in, and a paused feed keeps its
     * history only until it ages out under the same window (same precedent as
     * {@link #markOldItemsRead(long)}, which already affects paused channels). When
     * the keep-read-items setting is off (default) this method is never called, so
     * nothing is ever deleted.
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are deleted
     * @return the number of deleted rows
     */
    @Query("DELETE FROM rss_item WHERE is_read = 1 AND is_favorite = 0 AND updated_date_time < :cutoffMillis")
    public abstract int deleteOldReadRssItems(long cutoffMillis);

    /**
     * Updates the favorite state of rss items matching the given link.
     * This method must be called on a background thread.
     *
     * @param isFavorite the favorite state to set
     * @param link       the link of the rss items to update
     */
    @Query("UPDATE rss_item SET is_favorite = :isFavorite WHERE link = :link")
    public abstract void updateRssItemsIsFavoriteByLink(boolean isFavorite, String link);

    /**
     * Updates the paused state of the given channel. A paused channel keeps its
     * history but is skipped by the sync worker (no sync, no notifications).
     * Its unread counts can still decrease via the optional auto mark-read
     * retention window, see {@link #markOldItemsRead(long)}.
     * This method must be called on a background thread.
     *
     * @param channelId the channel id to update
     * @param isPaused  the paused state to set
     */
    @Query("UPDATE rss_channel SET is_paused = :isPaused WHERE id = :channelId")
    public abstract void updateRssChannelIsPaused(long channelId, boolean isPaused);

    /**
     * Finds all rss items matching the given link, regardless of channel.
     * This method must be called on a background thread.
     *
     * @param link the link of the rss items to find
     * @return list of rss items matching the given link
     */
    @Query("SELECT * FROM rss_item WHERE link = :link")
    public abstract List<RssItem> findRssItemsByLink(String link);
}
