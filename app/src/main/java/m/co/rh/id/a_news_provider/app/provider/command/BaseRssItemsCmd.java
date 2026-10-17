package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemsProvider;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Base command for the rss item lists. Pagination is keyset (cursor) based:
 * each page continues strictly after the last loaded row in the list's order
 * (COALESCE(pub_date, created_date_time), id), so page boundaries stay stable
 * under concurrent inserts - unlike the previous LIMIT-doubling scheme which
 * re-ran the full growing query on every page.
 * <p>
 * The emission contract is unchanged: {@link #getAllRssItems()} and the
 * underlying {@code BehaviorSubject} always carry the whole accumulated list,
 * and every emission is a NEW ArrayList (never a mutated previously-emitted
 * instance, since late subscribers may receive the same instance).
 */
public abstract class BaseRssItemsCmd implements RssItemsProvider {
    public static final int FILTER_BY_NONE = 0;
    public static final int FILTER_BY_UNREAD = 1;
    public static final int FILTER_BY_READ = 2;
    public static final int FILTER_BY_FAVORITE = 3;
    public static final int SORT_ORDER_NEWEST = 0;
    public static final int SORT_ORDER_OLDEST = 1;

    /**
     * Number of rows fetched per keyset page (and per fresh load). Small enough
     * to keep first loads and page appends cheap, large enough that a user
     * rarely waits on a page boundary.
     */
    public static final int PAGE_SIZE = 100;

    /**
     * Distinguishes the two whole-list load modes. Both reset the cursor and
     * re-query from the top of the current context; they differ only in how many
     * rows the window keeps:
     * <ul>
     * <li>{@link #FRESH}: context changed - load exactly {@link #PAGE_SIZE}.</li>
     * <li>{@link #REFRESH}: same context, data changed - keep the accumulated
     * window size so a deep list does not collapse to one page (scroll jump).</li>
     * </ul>
     */
    protected enum LoadMode {
        FRESH, REFRESH
    }

    protected final ExecutorService mExecutorService;
    protected final RssDao mRssDao;
    protected final ILogger mLogger;
    protected final BehaviorSubject<ArrayList<RssItem>> mItemsSubject;
    protected final BehaviorSubject<Boolean> mIsLoadingSubject;
    protected final BehaviorSubject<Optional<Integer>> mFilterTypeSubject;
    protected final BehaviorSubject<Optional<Integer>> mSortOrderSubject;

    /**
     * COALESCE(pub_date, created_date_time) in epoch millis of the last loaded
     * row, null before the first page. Guarded by {@link #mLoadLock}.
     */
    private Long mLastSortKey;

    /**
     * id of the last loaded row - the unique tiebreaker of the list's order.
     * Guarded by {@link #mLoadLock}.
     */
    private Long mLastId;

    /**
     * True when the database may hold more matching rows beyond the cursor.
     * Guarded by {@link #mLoadLock}.
     */
    private boolean mHasMore;

    /**
     * True while a next-page task is queued or running. Checked-and-set before
     * dispatching (scroll listeners fire repeatedly while the list rests at the
     * bottom; without the guard each trigger would race another duplicate
     * cursor query onto the same page). Guarded by {@link #mLoadLock}.
     */
    private boolean mNextPageInFlight;

    /**
     * Bumped by every fresh/refresh dispatch. A next-page request captures the
     * value at dispatch time; if it no longer matches when the page result
     * arrives, a fresh/refresh load took over meanwhile and the stale page MUST
     * be discarded - appending it would put wrong-context rows behind a new
     * cursor.
     */
    private final AtomicLong mLoadGeneration;

    /**
     * Serializes cursor reads/writes and list emissions. The shared executor is
     * a multi-thread pool, so a next-page task and a fresh/refresh load can run
     * on different threads at the same time; all state transitions below happen
     * under this lock (the DAO query for fresh/refresh loads included, which
     * also caps the query concurrency to one list at a time).
     */
    private final Object mLoadLock;

    protected BaseRssItemsCmd(Provider provider, int initialFilterType, boolean initialIsLoading) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mLogger = provider.get(ILogger.class);
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(initialIsLoading);
        mFilterTypeSubject = BehaviorSubject.createDefault(Optional.of(initialFilterType));
        mSortOrderSubject = BehaviorSubject.createDefault(Optional.of(SORT_ORDER_NEWEST));
        mLoadGeneration = new AtomicLong();
        mLoadLock = new Object();
    }

    /**
     * Returns the tag used for logging in this command.
     *
     * @return the class name used as the log tag
     */
    protected String getTag() {
        return BaseRssItemsCmd.class.getName();
    }

    /**
     * Refreshes the list in the CURRENT context (refresh mode): re-queries from
     * the top of the list, keeping the currently accumulated window size (at
     * least one page) so a deep list does not collapse while the user has
     * scrolled far down.
     * <p>
     * Call-site classification (same context, changed data):
     * <ul>
     * <li>sync result in all-channels mode (PagedRssItemsCmd combineLatest)</li>
     * <li>mark-all-read and auto mark-read retention (notifier subscriptions in
     * RssItemListSV / SearchRssItemListSV)</li>
     * <li>favorite toggle while the favorites filter is active (same notifiers)</li>
     * <li>pull-to-refresh (HomePage.onRefresh - RssItemListSV.refresh)</li>
     * <li>list page (re-)entry (RssItemListSV.provideComponent)</li>
     * </ul>
     * For context changes (filter, sort order, search query, channel switch)
     * use {@link #reload()} instead - the previous context's cursor is invalid
     * for the new one.
     */
    public void load() {
        dispatchLoad(LoadMode.REFRESH);
    }

    /**
     * Reloads the list from the beginning (fresh mode): resets the keyset
     * cursor and loads the first {@link #PAGE_SIZE} rows of the current context.
     * <p>
     * Call-site classification (changed context):
     * <ul>
     * <li>filter change ({@link #setFilterType(Integer)})</li>
     * <li>sort order change ({@link #setSortOrder(Integer)})</li>
     * <li>search query change (SearchRssItemsCmd.setQuery)</li>
     * <li>channel switch / initial load (PagedRssItemsCmd combineLatest)</li>
     * </ul>
     */
    public void reload() {
        dispatchLoad(LoadMode.FRESH);
    }

    private void dispatchLoad(LoadMode mode) {
        final long generation = mLoadGeneration.incrementAndGet();
        mExecutorService.execute(() -> executeLoad(mode, generation));
    }

    /**
     * Runs a fresh/refresh load on the calling thread. For callers already on
     * an executor thread (PagedRssItemsCmd's combineLatest chain); the
     * generation is bumped here since this is a fresh/refresh dispatch.
     *
     * @param mode FRESH for a changed context, REFRESH for same-context data updates
     */
    protected void executeLoad(LoadMode mode) {
        executeLoad(mode, mLoadGeneration.incrementAndGet());
    }

    private void executeLoad(LoadMode mode, long generation) {
        // superseded by a newer dispatch while queued: skip the redundant work,
        // the newer load emits the authoritative window
        if (generation != mLoadGeneration.get()) {
            return;
        }
        mIsLoadingSubject.onNext(true);
        try {
            synchronized (mLoadLock) {
                if (generation != mLoadGeneration.get()) {
                    return;
                }
                mItemsSubject.onNext(loadWindow(mode));
            }
        } catch (Throwable throwable) {
            mLogger.e(getTag(), throwable.getMessage(), throwable);
        } finally {
            mIsLoadingSubject.onNext(false);
        }
    }

    /**
     * Queries one whole-list window from the top of the current context.
     * Must be called while holding {@link #mLoadLock}.
     *
     * @param mode FRESH limits the window to {@link #PAGE_SIZE}, REFRESH keeps
     *             the accumulated size (minimum one page) to avoid scroll jumps
     * @return a new list never shared with a previously-emitted one
     */
    private ArrayList<RssItem> loadWindow(LoadMode mode) {
        int limit;
        if (LoadMode.FRESH == mode) {
            limit = PAGE_SIZE;
        } else {
            int accumulatedSize = getAllRssItems().size();
            limit = Math.max(accumulatedSize, PAGE_SIZE);
        }
        List<RssItem> page = loadItems(null, null, limit);
        ArrayList<RssItem> window = toArrayList(page);
        updateCursor(window, limit);
        return window;
    }

    /**
     * Appends the next keyset page after the current cursor.
     * <p>
     * Guards, in order: {@code mHasMore} (list exhausted), a synchronous
     * in-flight flag (set BEFORE dispatching - scroll listeners fire
     * repeatedly and each duplicate would run a concurrent duplicate cursor
     * query and double-append rows), and staleness: the load generation and
     * the cursor are captured at dispatch and re-validated on the executor
     * thread. If a fresh/refresh load was dispatched (queued ahead) or
     * completed while this page was in flight, the generation bumped and/or
     * the cursor moved, and the stale page result is discarded instead of
     * being appended onto the new context's list behind a cursor that no
     * longer exists.
     */
    public void loadNextPage() {
        final Long cursorSortKey;
        final Long cursorId;
        final long generation;
        synchronized (mLoadLock) {
            if (!mHasMore || mNextPageInFlight) {
                return;
            }
            mNextPageInFlight = true;
            cursorSortKey = mLastSortKey;
            cursorId = mLastId;
            generation = mLoadGeneration.get();
        }
        mExecutorService.execute(() -> {
            // only close what was opened: the stale check below can return
            // before any loading=true was emitted, and an unconditional
            // false in the finally would prematurely clear the loading
            // indicator of a concurrent fresh/refresh load
            boolean isLoadingEmitted = false;
            try {
                // a fresh/refresh was queued ahead of this page or dispatched
                // meanwhile: the captured cursor belongs to the old context
                if (isStaleNextPage(generation, cursorSortKey, cursorId)) {
                    return;
                }
                mIsLoadingSubject.onNext(true);
                isLoadingEmitted = true;
                List<RssItem> page = loadItems(cursorSortKey, cursorId, PAGE_SIZE);
                synchronized (mLoadLock) {
                    // a fresh/refresh landed while this page was querying:
                    // discard, appending would corrupt the new context's list
                    if (isStaleNextPage(generation, cursorSortKey, cursorId)) {
                        return;
                    }
                    ArrayList<RssItem> combined = new ArrayList<>(getAllRssItems());
                    combined.addAll(toArrayList(page));
                    updateCursor(page, PAGE_SIZE);
                    mItemsSubject.onNext(combined);
                }
            } catch (Throwable throwable) {
                mLogger.e(getTag(), throwable.getMessage(), throwable);
            } finally {
                synchronized (mLoadLock) {
                    mNextPageInFlight = false;
                }
                if (isLoadingEmitted) {
                    mIsLoadingSubject.onNext(false);
                }
            }
        });
    }

    /**
     * Returns true when the captured next-page request no longer matches the
     * current load context: a fresh/refresh load was dispatched after the
     * capture (generation bumped), or the cursor has since been reset or moved
     * by a completed fresh/refresh load. Must be called while holding
     * {@link #mLoadLock} for the check to be meaningful against the live
     * cursor; the dispatch-side call runs before any competing load could
     * emit, so it only reacts to already-dispatched bumps.
     */
    private boolean isStaleNextPage(long generation, Long cursorSortKey, Long cursorId) {
        return generation != mLoadGeneration.get()
                || cursorSortKey == null
                || !cursorSortKey.equals(mLastSortKey)
                || !Objects.equals(cursorId, mLastId);
    }

    @Override
    public ArrayList<RssItem> getAllRssItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Sets the filter type of the list, then reloads it from the beginning.
     * The reload must be a FRESH load: the cursor of the previous filter
     * context is meaningless for the new one, and reusing it would silently
     * skip rows on every following page load.
     * Does nothing if the filter is unchanged from the current value
     * (mirrors {@link SearchRssItemsCmd#setQuery}'s no-change guard): the
     * filter Spinner re-fires its saved position programmatically on every
     * (re-)layout, and re-issuing the same filter context would reset the
     * keyset cursor and collapse the accumulated window.
     *
     * @param filterType the filter type to set, null to reset to no filtering
     */
    public void setFilterType(Integer filterType) {
        Integer newFilterType = filterType == null ? FILTER_BY_NONE : filterType;
        if (getFilterType().orElse(FILTER_BY_NONE).equals(newFilterType)) {
            return;
        }
        mFilterTypeSubject.onNext(Optional.of(newFilterType));
        reload();
    }

    public Optional<Integer> getFilterType() {
        return mFilterTypeSubject.getValue();
    }

    public Flowable<Optional<Integer>> getFilterTypeFlow() {
        return Flowable.fromObservable(mFilterTypeSubject, BackpressureStrategy.BUFFER);
    }

    protected Integer getFilterTypeValue() {
        Optional<Integer> filterTypeOpt = mFilterTypeSubject.getValue();
        return filterTypeOpt.orElse(null);
    }

    /**
     * Sets the sort order of the rss items, then reloads the list from the beginning.
     *
     * @param sortOrder the sort order to set, null to reset to newest first
     */
    public void setSortOrder(Integer sortOrder) {
        if (sortOrder == null) {
            mSortOrderSubject.onNext(Optional.of(SORT_ORDER_NEWEST));
        } else {
            mSortOrderSubject.onNext(Optional.of(sortOrder));
        }
        reload();
    }

    /**
     * Returns the current sort order.
     *
     * @return the current sort order, or null if none is set
     */
    public Integer getSortOrder() {
        Optional<Integer> sortOrderOpt = mSortOrderSubject.getValue();
        return sortOrderOpt.orElse(null);
    }

    /**
     * Provides a Flowable stream of sort order changes.
     *
     * @return Flowable that emits optional sort orders
     */
    public Flowable<Optional<Integer>> getSortOrderFlow() {
        return Flowable.fromObservable(mSortOrderSubject, BackpressureStrategy.BUFFER);
    }

    protected Integer getSortOrderValue() {
        Optional<Integer> sortOrderOpt = mSortOrderSubject.getValue();
        return sortOrderOpt.orElse(null);
    }

    /**
     * Updates the keyset cursor and the has-more flag from a loaded page.
     * Must be called while holding {@link #mLoadLock}.
     *
     * @param page           the loaded page, oldest-to-newest position irrelevant
     * @param requestedLimit the limit the page was queried with: a full page
     *                       means more rows may exist
     */
    private void updateCursor(List<RssItem> page, int requestedLimit) {
        if (page == null || page.isEmpty()) {
            // empty window: paging stops (mHasMore=false) and any stale
            // cursor values left behind are dead - every mHasMore=true path
            // rewrites the cursor atomically with the flag, so nothing can
            // read them
            mHasMore = false;
            return;
        }
        RssItem last = page.get(page.size() - 1);
        Long sortKey = effectiveSortKey(last);
        if (sortKey == null || last.id == null) {
            // rows without dates/id cannot exist through the DAO (insertRssItem
            // assigns createdDateTime, Room assigns the autoincrement id), but a
            // row without them has no keyset position - stop paging rather than
            // risk a cursor that skips or repeats rows
            mHasMore = false;
            return;
        }
        mLastSortKey = sortKey;
        mLastId = last.id;
        mHasMore = page.size() >= requestedLimit;
    }

    /**
     * Returns the item's effective sort key, the SQL
     * COALESCE(pub_date, created_date_time): pub_date when the feed provided
     * one, created_date_time otherwise, null when the item has no dates at all.
     *
     * @param rssItem the item to read the sort key from
     * @return the sort key in epoch millis, or null when the item has no dates
     */
    private static Long effectiveSortKey(RssItem rssItem) {
        Date date = rssItem.pubDate != null ? rssItem.pubDate : rssItem.createdDateTime;
        return date != null ? date.getTime() : null;
    }

    private static ArrayList<RssItem> toArrayList(List<RssItem> list) {
        ArrayList<RssItem> arrayList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            arrayList.addAll(list);
        }
        return arrayList;
    }

    /**
     * Loads one list window. A null cursor loads the first page of the current
     * context; otherwise the result must continue strictly after the cursor row
     * (COALESCE(pub_date, created_date_time), id) in the list's order - pass
     * both through to the matching keyset DAO query. Must be called on a
     * background thread.
     *
     * @param cursorSortKey COALESCE(pub_date, created_date_time) of the last
     *                      already-loaded row in epoch millis, null for the first page
     * @param cursorId      id of the last already-loaded row, ignored when cursorSortKey is null
     * @param limit         maximum number of items to return
     * @return the loaded window as a new list, never null
     */
    @NonNull
    protected abstract ArrayList<RssItem> loadItems(@Nullable Long cursorSortKey, @Nullable Long cursorId, int limit);

    /**
     * Maps a filter type to the is_read column filter used by the rss item queries.
     * Public so pages outside the command hierarchy (e.g. the item detail page's
     * neighbor navigation) derive the exact same query filters as loadItems().
     *
     * @param filterType the filter type, may be null
     * @return 0 for unread-only, 1 for read-only, null for no read-state filtering
     */
    public static Integer toIsRead(Integer filterType) {
        Integer isRead = null;
        if (filterType != null) {
            switch (filterType) {
                case FILTER_BY_UNREAD:
                    isRead = 0;
                    break;
                case FILTER_BY_READ:
                    isRead = 1;
                    break;
                default:
                    break; // FILTER_BY_NONE
            }
        }
        return isRead;
    }

    /**
     * Maps a filter type to the is_favorite column filter used by the rss item
     * queries. Public so pages outside the command hierarchy (e.g. the item detail
     * page's neighbor navigation) derive the exact same query filters as loadItems().
     *
     * @param filterType the filter type, may be null
     * @return 1 for favorites-only, null for no favorite filtering
     */
    public static Integer toIsFavorite(Integer filterType) {
        Integer isFavorite = null;
        if (filterType != null) {
            switch (filterType) {
                case FILTER_BY_FAVORITE:
                    isFavorite = 1;
                    break;
                default:
                    break; // FILTER_BY_NONE
            }
        }
        return isFavorite;
    }
}
