package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class RssQueryCmd {
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;

    public RssQueryCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
    }

    public Single<RssChannel> getRssChannelById(long id) {
        return Single.fromCallable(() ->
                mRssDao
                        .findRssChannelById(id))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<RssItem> getRssItemById(long id) {
        return Single.fromCallable(() ->
                mRssDao
                        .findRssItemById(id))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Finds the rss item directly older than the given anchor item, applying the
     * same optional channel/read/favorite filters as the home list (see
     * {@link PagedRssItemsCmd#loadItems(Long, Long, int)}). The anchor position is
     * the item's effective sort date plus its unique id - exactly the list's two
     * sort keys, so navigation walks the visible list order one row at a time.
     * Absence is a normal outcome at the edge of the list, hence the Optional
     * instead of a nullable emission.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorItem the item to walk from, its effective date and id are the anchor
     * @return Single emitting the closest older rss item, or empty when the anchor
     * is the oldest matching item
     */
    public Single<Optional<RssItem>> findOlderRssItem(Long channelId, Integer isRead,
                                                      Integer isFavorite, RssItem anchorItem) {
        return Single.fromCallable(() ->
                findNeighbor(mRssDao.findOlderRssItem(channelId, isRead, isFavorite,
                        anchorEffectiveDate(anchorItem), anchorId(anchorItem))))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Finds the rss item directly newer than the given anchor item, applying the
     * same optional channel/read/favorite filters as the home list (see
     * {@link PagedRssItemsCmd#loadItems(Long, Long, int)}). The anchor position is
     * the item's effective sort date plus its unique id - exactly the list's two
     * sort keys, so navigation walks the visible list order one row at a time.
     * Absence is a normal outcome at the edge of the list, hence the Optional
     * instead of a nullable emission.
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorItem the item to walk from, its effective date and id are the anchor
     * @return Single emitting the closest newer rss item, or empty when the anchor
     * is the newest matching item
     */
    public Single<Optional<RssItem>> findNewerRssItem(Long channelId, Integer isRead,
                                                      Integer isFavorite, RssItem anchorItem) {
        return Single.fromCallable(() ->
                findNeighbor(mRssDao.findNewerRssItem(channelId, isRead, isFavorite,
                        anchorEffectiveDate(anchorItem), anchorId(anchorItem))))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Finds the link-GROUP directly older than the given anchor item - the grouped
     * counterpart of {@link #findOlderRssItem(Long, Integer, Integer, RssItem)}
     * (issue #45). Used when the home list shows the collapsed all-channels view
     * (collapse setting on, no channel selected): its GROUP BY expression,
     * printf-anchored deterministic representative and copy-level filters are
     * EXACTLY the ones of the grouped page query
     * {@code RssDao.findRssItemsPageGroupedByLinkDesc}, so next/previous navigation
     * walks exactly the visible collapsed list order - each step lands on the
     * neighboring group's representative, never on a duplicate copy the list
     * hides. The anchor must be a row the collapsed list displayed (its own group
     * representative).
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorItem the item to walk from, its effective date and id are the anchor
     * @return Single emitting the representative of the closest older link group,
     * or empty when the anchor is the oldest visible group
     */
    public Single<Optional<RssItem>> findOlderRssItemGroupedByLink(Long channelId, Integer isRead,
                                                                   Integer isFavorite, RssItem anchorItem) {
        return Single.fromCallable(() ->
                findNeighbor(mRssDao.findOlderRssItemGroupedByLink(channelId, isRead, isFavorite,
                        anchorEffectiveDate(anchorItem), anchorId(anchorItem))))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Finds the link-GROUP directly newer than the given anchor item - the grouped
     * counterpart of {@link #findNewerRssItem(Long, Integer, Integer, RssItem)}
     * (issue #45), ASC mirror of
     * {@link #findOlderRssItemGroupedByLink(Long, Integer, Integer, RssItem)}.
     * Walks exactly the visible collapsed list order of the all-channels view; the
     * anchor must be a row the collapsed list displayed (its own group
     * representative).
     *
     * @param channelId  optional channel id filter, null to include all channels
     * @param isRead     optional read-state filter, null to include read and unread items
     * @param isFavorite optional favorite filter, null to include favorite and non-favorite items
     * @param anchorItem the item to walk from, its effective date and id are the anchor
     * @return Single emitting the representative of the closest newer link group,
     * or empty when the anchor is the newest visible group
     */
    public Single<Optional<RssItem>> findNewerRssItemGroupedByLink(Long channelId, Integer isRead,
                                                                   Integer isFavorite, RssItem anchorItem) {
        return Single.fromCallable(() ->
                findNeighbor(mRssDao.findNewerRssItemGroupedByLink(channelId, isRead, isFavorite,
                        anchorEffectiveDate(anchorItem), anchorId(anchorItem))))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<Integer> countRssItem() {
        return Single.fromCallable(mRssDao::countRssItem)
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    /**
     * Wraps a nullable DAO lookup result in an Optional so "no neighbor" stays a
     * normal value instead of becoming an RxJava null-emission error.
     *
     * @param neighbor the DAO lookup result, may be null
     * @return Optional of the neighbor, empty when null
     */
    private static Optional<RssItem> findNeighbor(RssItem neighbor) {
        return Optional.ofNullable(neighbor);
    }

    /**
     * Returns the anchor item's effective date used by the list ordering:
     * pub_date, falling back to created_date_time when the feed provided none.
     *
     * @param anchorItem the item to take the effective date from
     * @return the effective date in epoch millis, or 0 when the item has no dates at all
     */
    private static long anchorEffectiveDate(RssItem anchorItem) {
        Date date = anchorItem.pubDate != null ?
                anchorItem.pubDate : anchorItem.createdDateTime;
        return date != null ? date.getTime() : 0L;
    }

    /**
     * Returns the anchor item's id, the list order's unique tiebreaker. The old
     * created_date_time tiebreaker was not unique (same-millisecond inserts when
     * a sync batch is persisted), which could skip a same-millisecond row during
     * neighbor navigation; id is the unique autoincrement primary key assigned in
     * insert order, and the list queries' secondary ORDER BY uses the same key.
     *
     * @param anchorItem the item to take the id from
     * @return the item's id, or 0 when unset (rows inserted through the DAO always
     * carry one; 0 keeps the query total)
     */
    private static long anchorId(RssItem anchorItem) {
        return anchorItem.id != null ? anchorItem.id : 0L;
    }
}
