package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class PagedRssItemsCmd extends BaseRssItemsCmd {
    /**
     * Returns the tag used for logging in this command.
     *
     * @return the class name used as the log tag
     */
    @Override
    protected String getTag() {
        return PagedRssItemsCmd.class.getName();
    }

    private final RssChannelStateNotifier mRssChannelStateNotifier;
    private Optional<RssChannel> mSelectedRssChannel;
    private final Flowable<ArrayList<RssItem>> mRssItems;

    public PagedRssItemsCmd(Provider provider) {
        super(provider, FILTER_BY_UNREAD, true);
        mSelectedRssChannel = Optional.empty();
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
                                executeLoad();
                            } else {
                                mIsLoadingSubject.onNext(false);
                            }
                        }).flatMap(aBoolean ->
                        Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER));
    }

    public Flowable<ArrayList<RssItem>> getRssItems() {
        return mRssItems;
    }

    @NonNull
    @Override
    protected ArrayList<RssItem> loadItems() {
        Long channelId = mSelectedRssChannel.isPresent() ? mSelectedRssChannel.get().id : null;
        Integer filterType = getFilterTypeValue();
        Integer isRead = toIsRead(filterType);
        Integer isFavorite = toIsFavorite(filterType);
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
}
