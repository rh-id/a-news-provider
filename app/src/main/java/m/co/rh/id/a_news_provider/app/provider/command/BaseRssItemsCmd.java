package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemsProvider;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public abstract class BaseRssItemsCmd implements RssItemsProvider {
    public static final int FILTER_BY_NONE = 0;
    public static final int FILTER_BY_UNREAD = 1;
    public static final int FILTER_BY_READ = 2;
    public static final int FILTER_BY_FAVORITE = 3;
    public static final int SORT_ORDER_NEWEST = 0;
    public static final int SORT_ORDER_OLDEST = 1;

    protected final ExecutorService mExecutorService;
    protected final RssDao mRssDao;
    protected final ILogger mLogger;
    protected final BehaviorSubject<ArrayList<RssItem>> mItemsSubject;
    protected final BehaviorSubject<Boolean> mIsLoadingSubject;
    protected final BehaviorSubject<Optional<Integer>> mFilterTypeSubject;
    protected final BehaviorSubject<Optional<Integer>> mSortOrderSubject;
    protected int mLimit;

    protected BaseRssItemsCmd(Provider provider, int initialFilterType, boolean initialIsLoading) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mLogger = provider.get(ILogger.class);
        mItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mIsLoadingSubject = BehaviorSubject.createDefault(initialIsLoading);
        mFilterTypeSubject = BehaviorSubject.createDefault(Optional.of(initialFilterType));
        mSortOrderSubject = BehaviorSubject.createDefault(Optional.of(SORT_ORDER_NEWEST));
        resetPage();
    }

    /**
     * Returns the tag used for logging in this command.
     *
     * @return the class name used as the log tag
     */
    protected String getTag() {
        return BaseRssItemsCmd.class.getName();
    }

    public void load() {
        mExecutorService.execute(this::executeLoad);
    }

    protected void executeLoad() {
        mIsLoadingSubject.onNext(true);
        try {
            mItemsSubject.onNext(
                    loadItems());
        } catch (Throwable throwable) {
            mLogger.e(getTag(), throwable.getMessage(), throwable);
        } finally {
            mIsLoadingSubject.onNext(false);
        }
    }

    public void reload() {
        resetPage();
        load();
    }

    public void loadNextPage() {
        if (getAllRssItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    @Override
    public ArrayList<RssItem> getAllRssItems() {
        return mItemsSubject.getValue();
    }

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    public void setFilterType(Integer filterType) {
        if (filterType == null) {
            mFilterTypeSubject.onNext(Optional.of(FILTER_BY_NONE));
        } else {
            mFilterTypeSubject.onNext(Optional.of(filterType));
        }
        load();
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

    private void resetPage() {
        mLimit = 1000;
    }

    protected static Integer toIsRead(Integer filterType) {
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

    protected static Integer toIsFavorite(Integer filterType) {
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

    @NonNull
    protected abstract ArrayList<RssItem> loadItems();
}
