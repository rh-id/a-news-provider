package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PagedRssItemsCmd {
    private static final String TAG = PagedRssItemsCmd.class.getName();
    public static final int FILTER_BY_NONE = 0;
    public static final int FILTER_BY_UNREAD = 1;
    public static final int FILTER_BY_READ = 2;
    public static final int FILTER_BY_FAVORITE = 3;
    public static final int SORT_ORDER_NEWEST = 0;
    public static final int SORT_ORDER_OLDEST = 1;

    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final ILogger mLogger;
    private final BehaviorSubject<ArrayList<RssItem>> mRssItemsSubject;
    private final BehaviorSubject<Boolean> mIsLoadingSubject;
    private final RssChannelStateNotifier mRssChannelStateNotifier;
    private Optional<RssChannel> mSelectedRssChannel;
    private final Flowable<ArrayList<RssItem>> mRssItems;
    private int mLimit;
    private BehaviorSubject<Optional<Integer>> mFilterTypeSubject;
    private final BehaviorSubject<Optional<Integer>> mSortOrderSubject;

    public PagedRssItemsCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mLogger = provider.get(ILogger.class);
        mRssItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mSelectedRssChannel = Optional.empty();
        mFilterTypeSubject = BehaviorSubject.createDefault(Optional.of(FILTER_BY_UNREAD));
        mSortOrderSubject = BehaviorSubject.createDefault(Optional.of(SORT_ORDER_NEWEST));
        mIsLoadingSubject = BehaviorSubject.createDefault(true);
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        mRssChannelStateNotifier = provider.get(RssChannelStateNotifier.class);
        mRssItems =
                Flowable.combineLatest(
                        rssChangeNotifier.liveNewRssModel()
                                .startWithItem(Optional.empty())
                                .observeOn(Schedulers.from(mExecutorService)),
                        mRssChannelStateNotifier.selectedRssChannel()
                                .observeOn(Schedulers.from(mExecutorService)),
                        (rssModel, rssChannelOptional) -> {
                            if (rssChannelOptional.isPresent()) {
                                if (mSelectedRssChannel.isPresent()) {
                                    if (mSelectedRssChannel.get().id.equals(rssChannelOptional.get().id)) {
                                        // no need to load if same
                                        mSelectedRssChannel = rssChannelOptional;
                                        return false;
                                    }
                                }
                                mSelectedRssChannel = rssChannelOptional;
                                return true;
                            }
                            mSelectedRssChannel = rssChannelOptional;
                            return true;
                        }
                )
                        .doOnNext(aBoolean -> {
                            if (aBoolean) {
                                mIsLoadingSubject.onNext(true);
                                try {
                                    mRssItemsSubject.onNext(loadRssItems());
                                } catch (Throwable throwable) {
                                    mLogger.e(TAG, throwable.getMessage(), throwable);
                                } finally {
                                    mIsLoadingSubject.onNext(false);
                                }
                            } else {
                                mIsLoadingSubject.onNext(false);
                            }
                        }).flatMap(aBoolean ->
                        Flowable.fromObservable(mRssItemsSubject, BackpressureStrategy.BUFFER));
        resetPage();
    }

    public Flowable<ArrayList<RssItem>> getRssItems() {
        return mRssItems;
    }

    public ArrayList<RssItem> getAllRssItems() {
        return mRssItemsSubject.getValue();
    }

    public void loadNextPage() {
        if (getAllRssItems().size() < mLimit) {
            return;
        }
        mLimit += mLimit;
        load();
    }

    public void load() {
        mExecutorService.execute(() -> {
            mIsLoadingSubject.onNext(true);
            try {
                mRssItemsSubject.onNext(
                        loadRssItems());
            } catch (Throwable throwable) {
                mLogger.e(TAG, throwable.getMessage(), throwable);
            } finally {
                mIsLoadingSubject.onNext(false);
            }
        });
    }

    @NonNull
    private ArrayList<RssItem> loadRssItems() {
        Long channelId = mSelectedRssChannel.isPresent() ? mSelectedRssChannel.get().id : null;
        Integer isRead = null;
        Integer isFavorite = null;
        Integer filterType = getFilterTypeValue();
        if (filterType != null) {
            switch (filterType) {
                case FILTER_BY_UNREAD:
                    isRead = 0;
                    break;
                case FILTER_BY_READ:
                    isRead = 1;
                    break;
                case FILTER_BY_FAVORITE:
                    isFavorite = 1;
                    break;
                default:
                    break; // FILTER_BY_NONE
            }
        }
        Integer sortOrder = getSortOrderValue();
        boolean asc = sortOrder != null && sortOrder == SORT_ORDER_OLDEST;
        List<RssItem> list = asc
                ? mRssDao.findRssItemsWithLimitAsc(channelId, isRead, isFavorite, mLimit)
                : mRssDao.findRssItemsWithLimit(channelId, isRead, isFavorite, mLimit);
        ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            rssItemArrayList.addAll(list);
        }
        return rssItemArrayList;
    }

    public void reload() {
        resetPage();
        load();
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

    public Flowable<Boolean> getLoadingFlow() {
        return Flowable.fromObservable(mIsLoadingSubject, BackpressureStrategy.BUFFER);
    }

    private Integer getFilterTypeValue() {
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

    private Integer getSortOrderValue() {
        Optional<Integer> sortOrderOpt = mSortOrderSubject.getValue();
        return sortOrderOpt.orElse(null);
    }

    private void resetPage() {
        mLimit = 1000;
    }
}
