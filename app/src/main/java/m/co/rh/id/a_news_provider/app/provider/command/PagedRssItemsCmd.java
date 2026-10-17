package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
                            // Maps each combineLatest emission to the load mode it
                            // requires (null = no load at all):
                            // - a channel switch (or first selection) changes the query
                            //   context: FRESH, the old keyset cursor must be dropped
                            // - a sync-driven re-emission while ALL channels are shown
                            //   keeps the context: REFRESH, preserving the accumulated
                            //   window the user may have scrolled deep into
                            // - a re-emission of the SAME selected channel needs no
                            //   reload (the all-channels path above is what reacts to
                            //   sync results)
                            if (rssChannelOptional.isPresent()) {
                                if (mSelectedRssChannel.isPresent()
                                        && mSelectedRssChannel.get().id.equals(rssChannelOptional.get().id)) {
                                    // no need to load if same
                                    mSelectedRssChannel = rssChannelOptional;
                                    return null;
                                }
                                mSelectedRssChannel = rssChannelOptional;
                                return LoadMode.FRESH;
                            }
                            mSelectedRssChannel = rssChannelOptional;
                            return LoadMode.REFRESH;
                        }
                )
                        .doOnNext(loadMode -> {
                            if (loadMode != null) {
                                executeLoad(loadMode);
                            } else {
                                mIsLoadingSubject.onNext(false);
                            }
                        }).flatMap(loadMode ->
                        Flowable.fromObservable(mItemsSubject, BackpressureStrategy.BUFFER));
    }

    public Flowable<ArrayList<RssItem>> getRssItems() {
        return mRssItems;
    }

    /**
     * Returns the id of the currently selected rss channel, or null when the list
     * shows all channels (no selection). Mirrors what loadItems() passes to the
     * DAO as the channel filter, so callers outside the command hierarchy (e.g.
     * the item detail page's neighbor navigation) walk exactly the current list.
     *
     * @return the selected channel id, or null in all-channels mode
     */
    public Long getSelectedChannelId() {
        return mSelectedRssChannel.isPresent() ? mSelectedRssChannel.get().id : null;
    }

    /**
     * Loads one keyset page of the current context. A null cursor loads the
     * first page; the keyset cursor (effective sort date + unique id) is passed
     * straight through to the DAO so page boundaries stay stable.
     */
    @NonNull
    @Override
    protected ArrayList<RssItem> loadItems(@Nullable Long cursorSortKey, @Nullable Long cursorId, int limit) {
        Long channelId = mSelectedRssChannel.isPresent() ? mSelectedRssChannel.get().id : null;
        Integer filterType = getFilterTypeValue();
        Integer isRead = toIsRead(filterType);
        Integer isFavorite = toIsFavorite(filterType);
        Integer sortOrder = getSortOrderValue();
        boolean asc = sortOrder != null && sortOrder == SORT_ORDER_OLDEST;
        List<RssItem> list = asc
                ? mRssDao.findRssItemsPageAsc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit)
                : mRssDao.findRssItemsPageDesc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit);
        ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            rssItemArrayList.addAll(list);
        }
        return rssItemArrayList;
    }
}
