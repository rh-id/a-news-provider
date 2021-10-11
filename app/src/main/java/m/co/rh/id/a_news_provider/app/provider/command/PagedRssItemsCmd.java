package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

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
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssDao> mRssDao;
    private final BehaviorSubject<ArrayList<RssItem>> mRssItemsSubject;
    private Optional<RssChannel> mSelectedRssChannel;
    private final Flowable<ArrayList<RssItem>> mRssItems;
    private int mLimit;

    public PagedRssItemsCmd(Provider provider) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssDao = provider.lazyGet(RssDao.class);
        mRssItemsSubject = BehaviorSubject.createDefault(new ArrayList<>());
        mSelectedRssChannel = Optional.empty();
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

    public Serializable getProperties() {
        HashMap<String, Object> properties = new HashMap<>();
        properties.put("limit", mLimit);
        return properties;
    }

    public void restoreProperties(Serializable serializable) {
        HashMap<String, Object> properties = (HashMap<String, Object>) serializable;
        mLimit = (int) properties.get("limit");
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
        List<RssItem> rssItemList = null;
        if (!mSelectedRssChannel.isPresent()) {
            rssItemList = mRssDao.get()
                    .loadRssItemsWithLimit(mLimit);
        } else {
            rssItemList = mRssDao.get()
                    .findRssItemsByChannelIdWithLimit(mSelectedRssChannel.get().id, mLimit);
        }
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

    private void resetPage() {
        mLimit = 20;
    }
}
