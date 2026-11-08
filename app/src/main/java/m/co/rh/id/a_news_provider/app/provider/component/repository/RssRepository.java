package m.co.rh.id.a_news_provider.app.provider.component.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.provider.component.AppDatabase;
import m.co.rh.id.a_news_provider.base.util.RssTitleNormalizer;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

/**
 * Repository for handling RSS feed persistence logic.
 * Moved from RssRequest to separate network layer from app layer.
 * Preserves read and favorite item state across feed syncs and retains items
 * that fall out of a feed's current list instead of deleting them (issue #37).
 * Also assigns the dedup cluster keys that back the cross-feed near-duplicate
 * story collapse (issue #45 phase 2): every synced item leaves persist() with a
 * dedup_key, and all rows sharing a link converge onto one key per sync.
 */
public class RssRepository {
    private final RssDao mRssDao;
    private final AppDatabase mAppDatabase;
    private final UrlNormalizer mUrlNormalizer;
    private final RssTitleNormalizer mRssTitleNormalizer;

    public RssRepository(Provider provider) {
        mRssDao = provider.get(RssDao.class);
        mAppDatabase = provider.get(AppDatabase.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
        mRssTitleNormalizer = provider.get(RssTitleNormalizer.class);
    }

    /**
     * Persists a parsed RSS feed model to the database, handling merge logic for existing channels.
     * The whole sync write runs inside ONE database transaction: the dedup-key assignment
     * (issue #45 phase 2), the healing by-link key writes it issues, and the insert/upsert are
     * atomic - a mid-sync half-keyed state (some rows already carrying the new key while their
     * same-link siblings are not yet healed, or parsed rows not yet persisted) is never observable
     * to concurrent readers. Inside the transaction, BEFORE the insert/upsert,
     * {@link #assignDedupKeys(List)} stamps every parsed item with its duplicate-cluster key so
     * inserted rows are born keyed and the upsert's field copy
     * ({@link RssDao#upsertRssItemsForChannel(RssChannel, List)}) writes the authoritative key.
     * Existing channels are upserted item-by-item: items are
     * matched by link (or by a title/pub-date tuple for linkless items), matched rows keep their
     * id and read/favorite state while their feed fields are refreshed, and items that are no
     * longer part of the parsed feed are retained (never deleted). The upsert is the
     * single source of truth for the state merge; see
     * {@link RssDao#upsertRssItemsForChannel(RssChannel, List)} for the full retention semantics.
     * (The upsert is itself a nested @Transaction - Room transactions re-enter on the same
     * thread, the inner one joins the outer one.)
     * This method must be called on a background thread.
     *
     * @param parsed the parsed RSS feed model from the network response
     * @return the persisted model with database ID populated, containing the merged item list
     * (persisted parsed items followed by the retained items the feed no longer carries)
     */
    public RssModel persist(RssModel parsed) {
        RssChannel responseRssChannel = parsed.getRssChannel();
        String originalUrl = responseRssChannel.url;
        String normalizedUrl = mUrlNormalizer.normalizeUrl(originalUrl);
        return mAppDatabase.runInTransaction(() -> {
            // Match both the raw and normalized URL variants so legacy un-normalized rows are found
            RssChannel rssChannel = mRssDao.findRssChannelByUrlVariants(originalUrl, normalizedUrl);
            responseRssChannel.url = normalizedUrl;
            if (rssChannel == null) {
                // New channel - insert directly, with dedup keys assigned first so
                // every inserted row is born carrying its cluster key
                assignDedupKeys(parsed.getRssItems());
                mRssDao.insertRssChannel(parsed.getRssChannel(), parsed.getRssItems().toArray(new RssItem[0]));
                return parsed;
            } else {
                // Existing channel - preserve channel fields and upsert items atomically
                copyPreservedFields(rssChannel, responseRssChannel);
                assignDedupKeys(parsed.getRssItems());
                List<RssItem> persistedItems =
                        mRssDao.upsertRssItemsForChannel(responseRssChannel, parsed.getRssItems());
                return new RssModel(responseRssChannel, new ArrayList<>(persistedItems));
            }
        });
    }

    /**
     * Copies preserved fields from existing DB channel to parsed channel.
     * Preserves: id, feedName, createdDateTime, updatedDateTime
     */
    private void copyPreservedFields(RssChannel fromDb, RssChannel toUpdate) {
        toUpdate.id = fromDb.id;
        toUpdate.feedName = fromDb.feedName;
        toUpdate.createdDateTime = fromDb.createdDateTime;
        toUpdate.updatedDateTime = fromDb.updatedDateTime;
    }

    /**
     * Assigns the dedup cluster key of every parsed item of one sync batch
     * (issue #45 phase 2), in feed order. Runs INSIDE the persist() transaction,
     * before the insert/upsert, so inserted rows are born keyed and the upsert's
     * field copy writes the authoritative key. Per parsed item, the first rule
     * that yields a key wins:
     * <ol>
     * <li><b>Union by link, window-independent (P0-1)</b>: when the item's link
     * is valid (non-null, non-empty), the in-batch link map is consulted first;
     * on a miss, {@link RssDao#findRssItemsByLink(String)} (indexed, deliberately
     * NOT bounded by the recency window) is queried and the first non-null,
     * non-empty stored dedup_key is adopted (DAO result order). This keeps the
     * phase-1 same-link invariant independent of any recency heuristic - a row
     * untouched for months still hands its key to a new copy of the same
     * link.</li>
     * <li><b>Exact normalized-title adoption</b>: otherwise the in-batch
     * normalizedTitle map is consulted, so a second copy of an already-keyed
     * title within one batch joins the first copy's cluster.</li>
     * <li><b>Fuzzy adoption, window-bounded</b>: otherwise, when the normalized
     * title has at least {@link RssTitleNormalizer#MIN_TOKENS_FOR_FUZZY} tokens,
     * the best candidate with token-set Jaccard similarity at or above
     * {@link RssTitleNormalizer#JACCARD_THRESHOLD} wins (ties: highest similarity
     * first, then the first candidate in scan order - deterministic). Candidates
     * are (a) the recent rows of
     * {@link RssDao#findRecentRssItemsForDedupMatching(long)} fetched ONCE before
     * the loop, their normalized title, token set and key memoized per candidate
     * - never recomputed per compared item - and (b) the items of this same batch
     * already processed, which are not yet visible through any DAO query during
     * the loop (the insert/upsert only runs after assignment completes) but must
     * merge with their batch siblings (two similar wire copies arriving in one
     * sync share a key).</li>
     * <li><b>Fallback canonical key</b>: otherwise the item's own normalized
     * title becomes the key. It may be "" (null/blank title): stored as-is and
     * neutralized at query time by {@code NULLIF(dedup_key, '')}, so such rows
     * fall back to link/sentinel grouping.</li>
     * </ol>
     * After the loop the batch is HEALED by link: every DISTINCT valid link with
     * a non-empty assigned key gets ONE
     * {@link RssDao#updateRssItemsDedupKeyForLink(String, String)} call, which
     * unconditionally overwrites differing old keys and fills NULLs, so all rows
     * sharing a link converge onto one key. Empty keys are never healed - those
     * rows keep NULL/empty and stay on the link/sentinel grouping tiers.
     * This method must be called on a background thread (inside persist's
     * transaction).
     *
     * @param parsedItems the freshly parsed feed items, in feed order, may be
     *                    null/empty (no-op)
     */
    private void assignDedupKeys(List<RssItem> parsedItems) {
        if (parsedItems == null || parsedItems.isEmpty()) {
            return;
        }
        long fuzzyWindowStart = System.currentTimeMillis()
                - TimeUnit.DAYS.toMillis(RssTitleNormalizer.DEDUP_WINDOW_DAYS);
        // memoized once per sync: every candidate's normalized title, token set
        // and key are computed HERE, never inside the per-item loop. The list
        // preserves the DAO result order so the fuzzy tie-break ("first
        // candidate") is deterministic.
        List<DedupCandidate> databaseCandidates = new ArrayList<>();
        List<RssItem> recentRows =
                mRssDao.findRecentRssItemsForDedupMatching(fuzzyWindowStart);
        if (recentRows != null) {
            for (RssItem recentRow : recentRows) {
                if (recentRow.id == null
                        || recentRow.dedupKey == null || recentRow.dedupKey.isEmpty()) {
                    continue;
                }
                databaseCandidates.add(new DedupCandidate(
                        recentRow.title, recentRow.dedupKey, mRssTitleNormalizer));
            }
        }
        List<DedupCandidate> batchCandidates = new ArrayList<>();
        Map<String, String> keyByLink = new HashMap<>();
        Map<String, String> keyByNormalizedTitle = new HashMap<>();
        for (RssItem parsedItem : parsedItems) {
            String normalizedTitle = mRssTitleNormalizer.normalize(parsedItem.title);
            boolean hasValidLink = parsedItem.link != null && !parsedItem.link.isEmpty();
            String key = null;
            // Rule 1: union by link - in-batch map first, then the (window-free)
            // indexed by-link lookup
            if (hasValidLink) {
                String batchKey = keyByLink.get(parsedItem.link);
                if (batchKey != null && !batchKey.isEmpty()) {
                    key = batchKey;
                } else {
                    List<RssItem> sameLinkRows = mRssDao.findRssItemsByLink(parsedItem.link);
                    if (sameLinkRows != null) {
                        for (RssItem sameLinkRow : sameLinkRows) {
                            if (sameLinkRow.dedupKey != null
                                    && !sameLinkRow.dedupKey.isEmpty()) {
                                key = sameLinkRow.dedupKey;
                                break;
                            }
                        }
                    }
                }
            }
            // Rule 2: exact normalized-title adoption within the batch
            if (key == null && !normalizedTitle.isEmpty()) {
                key = keyByNormalizedTitle.get(normalizedTitle);
            }
            // Rule 3: fuzzy adoption, bounded by the recency window and the
            // minimum token count
            if (key == null && !normalizedTitle.isEmpty()) {
                Set<String> itemTokens =
                        mRssTitleNormalizer.tokenSetOfNormalized(normalizedTitle);
                if (itemTokens.size() >= RssTitleNormalizer.MIN_TOKENS_FOR_FUZZY) {
                    DedupCandidate bestCandidate = findBestFuzzyCandidate(
                            itemTokens, databaseCandidates, batchCandidates);
                    if (bestCandidate != null) {
                        key = bestCandidate.dedupKey;
                    }
                }
            }
            // Rule 4: fallback canonical key - the item's own normalized title
            if (key == null) {
                key = normalizedTitle;
            }
            parsedItem.dedupKey = key;
            if (hasValidLink) {
                keyByLink.put(parsedItem.link, key);
            }
            if (!normalizedTitle.isEmpty()) {
                // first-come wins: the earliest copy of a title fixes the cluster
                keyByNormalizedTitle.putIfAbsent(normalizedTitle, key);
            }
            batchCandidates.add(new DedupCandidate(normalizedTitle, key, mRssTitleNormalizer));
        }
        // Heal by link: one unconditional write per DISTINCT valid link with a
        // non-empty key, converging every stored row sharing that link onto the
        // same key (overrides differing old keys, fills NULLs)
        for (Map.Entry<String, String> linkEntry : keyByLink.entrySet()) {
            if (!linkEntry.getValue().isEmpty()) {
                mRssDao.updateRssItemsDedupKeyForLink(
                        linkEntry.getValue(), linkEntry.getKey());
            }
        }
    }

    /**
     * Finds the best fuzzy candidate for the given token set: the first
     * candidate whose similarity is at or above
     * {@link RssTitleNormalizer#JACCARD_THRESHOLD}, replaced only by a STRICTLY
     * higher similarity later in the scan - so equal-similarity ties resolve to
     * the earliest candidate, and the scan order (database candidates in DAO
     * result order, then batch candidates in feed order) makes the choice fully
     * deterministic. Returns null when no candidate clears the threshold.
     *
     * @param itemTokens        the parsed item's normalized-title token set
     * @param databaseCandidates the window-bounded recent rows' memoized candidates
     * @param batchCandidates   the batch items already processed by the caller
     * @return the best candidate, or null when none is similar enough
     */
    private DedupCandidate findBestFuzzyCandidate(Set<String> itemTokens,
                                                  List<DedupCandidate> databaseCandidates,
                                                  List<DedupCandidate> batchCandidates) {
        DedupCandidate bestCandidate = null;
        double bestSimilarity = 0;
        for (DedupCandidate candidate : databaseCandidates) {
            double similarity = mRssTitleNormalizer.similarity(itemTokens, candidate.tokens);
            if (similarity >= RssTitleNormalizer.JACCARD_THRESHOLD
                    && similarity > bestSimilarity) {
                bestCandidate = candidate;
                bestSimilarity = similarity;
            }
        }
        // batch items processed earlier are candidates too: they are not yet in
        // the database during the assignment loop, so rule 3 would never see
        // them without this pool (two similar titles within one batch must
        // share a key)
        for (DedupCandidate candidate : batchCandidates) {
            double similarity = mRssTitleNormalizer.similarity(itemTokens, candidate.tokens);
            if (similarity >= RssTitleNormalizer.JACCARD_THRESHOLD
                    && similarity > bestSimilarity) {
                bestCandidate = candidate;
                bestSimilarity = similarity;
            }
        }
        return bestCandidate;
    }

    /**
     * Memoized fuzzy-match view of one candidate title: the title's token set
     * and the candidate's dedup cluster key, computed ONCE (per database
     * candidate before the assignment loop, per batch item as it is processed)
     * so the per-item fuzzy pass never re-normalizes or re-tokenizes anything.
     */
    private static final class DedupCandidate {
        private final Set<String> tokens;
        private final String dedupKey;

        /**
         * @param title    the candidate's raw or already-normalized title
         *                 (normalization is idempotent, both spellings tokenize
         *                 identically)
         * @param dedupKey the candidate's dedup cluster key
         * @param normalizer the shared normalizer that tokenizes the title
         */
        private DedupCandidate(String title, String dedupKey,
                               RssTitleNormalizer normalizer) {
            this.tokens = normalizer.tokenSetOfNormalized(normalizer.normalize(title));
            this.dedupKey = dedupKey;
        }
    }

    /**
     * Updates the isRead status of an RSS item in the database.
     * This method must be called on a background thread.
     *
     * @param rssItem the RSS item to update
     */
    public void updateRssItemIsRead(RssItem rssItem) {
        mRssDao.updateRssItemsIsReadByLink(rssItem.isRead, rssItem.link);
    }

    /**
     * Marks unread RSS items as read when their link matches one of the given links,
     * keeping cross-channel duplicates (same link) consistent.
     * This method must be called on a background thread.
     *
     * @param links the links of the RSS items to mark as read
     * @return the number of rows marked as read
     */
    public int markItemsReadByLinks(List<String> links) {
        return mRssDao.markItemsReadByLinks(links);
    }

    /**
     * Auto mark-read (unread retention window): marks unread, non-favorite RSS items
     * older than the given cutoff as read, keeping cross-channel duplicates
     * (same link) consistent. Favorites are never touched.
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are affected
     * @return the number of rows marked as read
     */
    public int markOldItemsRead(long cutoffMillis) {
        return mRssDao.markOldItemsRead(cutoffMillis);
    }

    /**
     * Keep-read-items retention window (issue #37): deletes read, non-favorite RSS
     * items whose last-seen-in-feed stamp (updated_date_time) is older than the
     * given cutoff. Unread items and favorites are never deleted; paused channels
     * are included (global opt-in window). Called at the end of a sync by
     * RssSyncWorker, right after the optional auto mark-read pass, so the two
     * windows compose (unread ages to read, then a later sync prunes it once aged).
     * This method must be called on a background thread.
     *
     * @param cutoffMillis epoch millis; items strictly older than this are deleted
     * @return the number of deleted rows
     */
    public int pruneOldReadItems(long cutoffMillis) {
        return mRssDao.deleteOldReadRssItems(cutoffMillis);
    }

    /**
     * Updates the isFavorite status of an RSS item in the database.
     * This method must be called on a background thread.
     *
     * @param rssItem the RSS item to update
     */
    public void updateRssItemIsFavorite(RssItem rssItem) {
        mRssDao.updateRssItemsIsFavoriteByLink(rssItem.isFavorite, rssItem.link);
    }

    /**
     * Finds all RSS items matching the given link, regardless of channel.
     * This method must be called on a background thread.
     *
     * @param link the link of the RSS items to find
     * @return list of RSS items matching the given link
     */
    public List<RssItem> findRssItemsByLink(String link) {
        return mRssDao.findRssItemsByLink(link);
    }

    /**
     * Finds all RSS items carrying the given dedup cluster key, regardless of
     * channel or link. Backs the duplicate-sources expansion of the collapsed
     * all-channels list for KEYED rows (issue #45 phase 2): near-duplicate copies
     * with DIFFERENT links are invisible to {@link #findRssItemsByLink(String)},
     * so the opaque cluster key stamped at sync time is the handle that
     * enumerates the whole story.
     * This method must be called on a background thread.
     *
     * @param dedupKey the dedup cluster key of the items to find
     * @return list of RSS items carrying the given key
     */
    public List<RssItem> findRssItemsByDedupKey(String dedupKey) {
        return mRssDao.findRssItemsByDedupKey(dedupKey);
    }

    /**
     * Finds the RSS channel with the given id. Backs the duplicate-story sources
     * expansion (issue #45), which maps the channel ids of an item's cross-feed
     * copies to displayable feed names.
     * This method must be called on a background thread.
     *
     * @param id the channel id to find
     * @return the channel, or null when no channel with this id exists
     */
    public RssChannel findRssChannelById(long id) {
        return mRssDao.findRssChannelById(id);
    }

    /**
     * Deletes an RSS channel from the database.
     * This method must be called on a background thread.
     *
     * @param rssChannel the RSS channel to delete
     */
    public void deleteRssChannel(RssChannel rssChannel) {
        mRssDao.deleteRssChannel(rssChannel);
    }

    /**
     * Retrieves a map of RSS channels to their unread item counts.
     * This method must be called on a background thread.
     *
     * @return a LinkedHashMap preserving channel order, with zero-filled counts for channels with no unread items
     */
    public Map<RssChannel, Integer> getChannelUnreadCountMap() {
        return buildUnreadCountMap(mRssDao.loadAllRssChannel(), mRssDao.countUnReadRssItemsByChannel());
    }

    /**
     * Package-visible helper for testing - builds a map of channels to their unread counts.
     * Merges the channel list with unread count data, preserving order and zero-filling missing counts.
     *
     * @param channels the list of RSS channels
     * @param unreadCounts the list of unread count data
     * @return a LinkedHashMap mapping channels to their unread counts
     */
    static Map<RssChannel, Integer> buildUnreadCountMap(List<RssChannel> channels, List<ChannelUnreadCount> unreadCounts) {
        Map<RssChannel, Integer> mapResult = new LinkedHashMap<>();
        
        if (channels == null || channels.isEmpty()) {
            return mapResult;
        }

        // Build a map of channel_id to count for quick lookup
        HashMap<Long, Integer> countMap = new HashMap<>();
        if (unreadCounts != null) {
            for (ChannelUnreadCount cuc : unreadCounts) {
                countMap.put(cuc.channel_id, cuc.cnt);
            }
        }

        // Merge channels with counts, preserving order
        for (RssChannel rssChannel : channels) {
            Integer count = countMap.get(rssChannel.id);
            mapResult.put(rssChannel, count != null ? count : 0);
        }

        return mapResult;
    }
}
