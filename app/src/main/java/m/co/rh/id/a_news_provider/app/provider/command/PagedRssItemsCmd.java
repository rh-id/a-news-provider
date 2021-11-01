package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class PagedRssItemsCmd {
    public static final int FILTER_BY_NONE = 0;
    public static final int FILTER_BY_UNREAD = 1;

    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssDao> mRssDao;
    private final BehaviorSubject<ArrayList<RssItem>> mRssItemsSubject;
    private Optional<RssChannel> mSelectedRssChannel;
    private final Flowable<ArrayList<RssItem>> mRssItems;
    private int mLimit;
    private BehaviorSubject<Optional<Integer>> mFilterTypeSubject;

    public PagedRssItemsCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssDao = provider.lazyGet(RssDao.class);
        mRssItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mSelectedRssChannel = Optional.empty();
        mFilterTypeSubject = BehaviorSubject.createDefault(Optional.of(FILTER_BY_UNREAD));
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        mRssItems =
                Flowable.combineLatest(
                        rssChangeNotifier.liveNewRssModel()
                                .startWithItem(Optional.empty())
                                .observeOn(Schedulers.from(mExecutorService.get())),
                        rssChangeNotifier.selectedRssChannel()
                                .observeOn(Schedulers.from(mExecutorService.get())),
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
                                try {
                                    mRssItemsSubject.onNext(loadRssItems());
                                } catch (Throwable throwable) {
                                    mRssItemsSubject.onError(throwable);
                                }
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
        mExecutorService.get().execute(() -> {
            try {
                mRssItemsSubject.onNext(
                        loadRssItems());
            } catch (Throwable throwable) {
                mRssItemsSubject.onError(throwable);
            }
        });
    }

    @NonNull
    private ArrayList<RssItem> loadRssItems() {
        Supplier<List<RssItem>> listSupplier;
        Integer filterType = getFilterTypeValue();
        if (!mSelectedRssChannel.isPresent()) {
            listSupplier = () -> mRssDao.get()
                    .loadRssItemsWithLimit(mLimit);
            if (filterType != null) {
                switch (filterType) {
                    case FILTER_BY_UNREAD:
                        listSupplier = () -> mRssDao.get()
                                .findRssItemsByIsReadWithLimit(0, mLimit);
                        break;
                }
            }
        } else {
            listSupplier = () -> mRssDao.get()
                    .findRssItemsByChannelIdWithLimit(mSelectedRssChannel.get().id, mLimit);
            if (filterType != null) {
                switch (filterType) {
                    case FILTER_BY_UNREAD:
                        listSupplier = () -> mRssDao.get()
                                .findRssItemsByChannelIdAndIsReadWithLimit(
                                        mSelectedRssChannel.get().id, 0, mLimit);
                        break;
                }
            }
        }
        List<RssItem> rssItemList = listSupplier.get();
        ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
        if (rssItemList != null && !rssItemList.isEmpty()) {
            rssItemArrayList.addAll(rssItemList);
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

    private Integer getFilterTypeValue() {
        Optional<Integer> filterTypeOpt = mFilterTypeSubject.getValue();
        if (filterTypeOpt == null) {
            return null;
        }
        return filterTypeOpt.orElse(null);
    }

    private void resetPage() {
        mLimit = 20;
    }
}
