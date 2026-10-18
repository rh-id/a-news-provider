package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.LinkCount;
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
    private final AppSharedPreferences mAppSharedPreferences;
    private Optional<RssChannel> mSelectedRssChannel;
    private final Flowable<ArrayList<RssItem>> mRssItems;

    public PagedRssItemsCmd(Provider provider) {
        super(provider, FILTER_BY_UNREAD, true);
        mSelectedRssChannel = Optional.empty();
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        mRssChannelStateNotifier = provider.get(RssChannelStateNotifier.class);
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
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
     * <p>
     * Duplicate-story collapsing (issue #45): when the list shows ALL channels
     * (no channel selected) AND the collapse setting is on, the link-grouped DAO
     * queries are used instead of the flat ones, so rows sharing a link collapse
     * into their newest copy, and {@link RssItem#duplicateCount} is filled per row
     * with the link's ABSOLUTE copy count from
     * {@code countRssItemsByLinks} (all copies regardless of the active
     * read/favorite filter, so the badge wording "Also in N feeds" stays true even
     * when the visible row is filtered). The setting is deliberately RE-READ on
     * every load rather than observed: the grouped and flat queries produce
     * different keyset cursors, so a mode switch is only valid on a fresh window,
     * which the next list load provides. Sync-driven REFRESH emissions re-run this
     * method, so the mode may flip mid-session - the whole window is rebuilt on
     * that load, keeping it self-consistent. Grouping only ever happens in the
     * all-channels view: within a single channel a link is already unique (legacy
     * duplicate channels were merged by MIGRATION_8_9).
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
        boolean collapseDuplicates = channelId == null
                && mAppSharedPreferences.isCollapseDuplicateStories();
        List<RssItem> list;
        if (collapseDuplicates) {
            list = asc
                    ? mRssDao.findRssItemsPageGroupedByLinkAsc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit)
                    : mRssDao.findRssItemsPageGroupedByLinkDesc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit);
            fillDuplicateCounts(list);
        } else {
            list = asc
                    ? mRssDao.findRssItemsPageAsc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit)
                    : mRssDao.findRssItemsPageDesc(channelId, isRead, isFavorite, cursorSortKey, cursorId, limit);
        }
        ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            rssItemArrayList.addAll(list);
        }
        return rssItemArrayList;
    }

    /**
     * Fills {@link RssItem#duplicateCount} for every representative row of a
     * grouped page from the DAO's per-link copy counts. Runs on the same executor
     * thread as the page query (inside {@link #loadItems(Long, Long, int)}), never
     * on the main thread. Rows without a count row (null/empty links, which
     * {@code countRssItemsByLinks} deliberately ignores) default to 1: they are
     * singleton groups, and the badge only renders for counts &gt; 1.
     *
     * @param representatives the grouped page's representative rows, may be null/empty
     */
    private void fillDuplicateCounts(List<RssItem> representatives) {
        if (representatives == null || representatives.isEmpty()) {
            return;
        }
        ArrayList<String> links = new ArrayList<>(representatives.size());
        for (RssItem rssItem : representatives) {
            links.add(rssItem.link);
        }
        List<LinkCount> counts = mRssDao.countRssItemsByLinks(links);
        Map<String, Integer> countByLink = new HashMap<>();
        if (counts != null) {
            for (LinkCount linkCount : counts) {
                countByLink.put(linkCount.link, linkCount.cnt);
            }
        }
        for (RssItem rssItem : representatives) {
            Integer count = rssItem.link == null ? null : countByLink.get(rssItem.link);
            rssItem.duplicateCount = count != null ? count : 1;
        }
    }
}
