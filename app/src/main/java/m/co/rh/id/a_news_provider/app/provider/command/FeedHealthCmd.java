package m.co.rh.id.a_news_provider.app.provider.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.PublishProcessor;
import m.co.rh.id.a_news_provider.app.model.FeedHealthItem;
import m.co.rh.id.a_news_provider.base.model.ChannelItemCount;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Page-scoped command backing the feed-health dashboard (issue #42): loads
 * every channel, joins it with its per-channel item counts
 * ({@link RssDao#countRssItemsByChannel()}) and emits the sorted
 * {@link FeedHealthItem} list for the dashboard page. Resolved from the page's
 * StatefulViewProvider (one instance per FeedHealthPage), so its dispose()
 * is called from the page's dispose like any other page-scoped component.
 * <p>
 * <b>Channel-driven merge with zero-fill.</b> The merge iterates
 * {@link RssDao#loadAllRssChannel()} and looks each channel up in a map built
 * from the count query - NOT the other way around. The count query is a GROUP
 * BY over rss_item, so a channel without any item row is OMITTED from its
 * result; walking only the count rows would make empty feeds vanish from the
 * dashboard exactly when the dashboard should show them. Defaulting missing
 * entries to cnt=0 / unread=0 (the zero-fill pattern of
 * RssRepository.buildUnreadCountMap) keeps every subscribed feed visible.
 * <p>
 * <b>Non-replaying emission.</b> Results are emitted on a
 * {@link PublishProcessor}: no replay. The StatefulViewProvider that caches
 * this command is pooled and a replaying subject (BehaviorSubject with a
 * cached value) would hand a STALE list to a future page instead of letting it
 * load fresh state - the page therefore subscribes BEFORE its first refresh()
 * so nothing is missed either.
 * <p>
 * The refresh runs on the shared executor with a disposed-guard mirroring
 * RssChannelStateNotifier.refreshUnreadCount(): a page popped mid-refresh must
 * not deliver into a dead processor, and a DB failure downgrades to a log line
 * so the subject (and the dashboard) survive to the next refresh.
 */
public class FeedHealthCmd {
    private static final String TAG = FeedHealthCmd.class.getName();

    private final AtomicBoolean mDisposed = new AtomicBoolean();
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final ILogger mLogger;
    // NO replay (see class javadoc): the pooled StatefulViewProvider recycles
    // this command's scope, a cached emission would surface stale dashboard
    // data to a future page
    private final PublishProcessor<List<FeedHealthItem>> mItemsPublishProcessor;

    public FeedHealthCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mLogger = provider.get(ILogger.class);
        mItemsPublishProcessor = PublishProcessor.create();
    }

    /**
     * Reloads the dashboard (issue #42) on the shared executor. Safe to call
     * repeatedly (event-driven reloads + the page's initial load); emissions
     * are skipped entirely once this command is disposed.
     */
    public void refresh() {
        mExecutorService.execute(this::doRefresh);
    }

    /**
     * Internal refresh implementation - checks the disposal flag before and
     * after the DB work (RssChannelStateNotifier pattern): the executor task
     * may still be queued when the page pops, and an emission into the
     * completed processor of a disposed command would be an undeliverable
     * error. All failures downgrade to a log line, never an onError.
     */
    private void doRefresh() {
        if (mDisposed.get()) {
            return;
        }
        try {
            List<RssChannel> rssChannelList = mRssDao.loadAllRssChannel();
            List<ChannelItemCount> itemCountList = mRssDao.countRssItemsByChannel();
            // count rows are GROUP BY results: zero-row channels are absent and
            // must be zero-filled below so empty feeds never vanish (issue #42)
            Map<Long, ChannelItemCount> countMap = new HashMap<>();
            if (itemCountList != null) {
                for (ChannelItemCount channelItemCount : itemCountList) {
                    countMap.put(channelItemCount.channel_id, channelItemCount);
                }
            }
            List<FeedHealthItem> feedHealthItemList =
                    new ArrayList<>(rssChannelList.size());
            for (RssChannel rssChannel : rssChannelList) {
                ChannelItemCount channelItemCount = countMap.get(rssChannel.id);
                int totalItems = channelItemCount != null ? channelItemCount.cnt : 0;
                int unreadItems = channelItemCount != null ? channelItemCount.unread : 0;
                feedHealthItemList.add(new FeedHealthItem(rssChannel, totalItems, unreadItems));
            }
            // errors-first ordering happens HERE so the adapter stays a dumb
            // renderer (see FeedHealthItem.ERRORS_FIRST_COMPARATOR)
            Collections.sort(feedHealthItemList, FeedHealthItem.ERRORS_FIRST_COMPARATOR);
            if (!mDisposed.get()) {
                mItemsPublishProcessor.onNext(feedHealthItemList);
            }
        } catch (Throwable throwable) {
            // never call onError on the processor - log instead so the dashboard
            // survives to the next refresh (RssChannelStateNotifier precedent)
            mLogger.e(TAG, throwable.getMessage(), throwable);
        }
    }

    /**
     * The dashboard rows (issue #42): emits one sorted list per refresh(). No
     * replay - the page subscribes before its first refresh().
     *
     * @return Flowable of the feed-health items, errors-first ordered
     */
    public Flowable<List<FeedHealthItem>> items() {
        return mItemsPublishProcessor;
    }

    /**
     * Marks this command dead and completes the processor. Called from the
     * page's dispose; an in-flight refresh observes the flag and drops its
     * emission.
     */
    public void dispose() {
        mDisposed.set(true);
        mItemsPublishProcessor.onComplete();
    }
}
