package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to update the isRead status of an RSS item.
 * Sets the flag synchronously then persists the change in the background.
 */
public class UpdateRssItemIsReadCmd {
    private static final String TAG = UpdateRssItemIsReadCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssRepository mRssRepository;
    private final RssChannelStateNotifier mRssChannelStateNotifier;
    private final ILogger mLogger;

    public UpdateRssItemIsReadCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssRepository = provider.get(RssRepository.class);
        mRssChannelStateNotifier = provider.get(RssChannelStateNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Updates the isRead status of an RSS item.
     * Sets the flag synchronously on the calling thread, then persists the change in the background.
     *
     * @param rssItem the RSS item to update
     * @param isRead the new isRead status
     */
    public void execute(RssItem rssItem, boolean isRead) {
        execute(rssItem, isRead, null);
    }

    /**
     * Updates the isRead status of an RSS item, with an optional post-write hook.
     * Sets the flag synchronously on the calling thread, then persists the change in the background.
     * <p>
     * {@code onWriteCompleted} (when non-null) runs on the executor thread after the
     * by-link write commits, so follow-up work (e.g. an undo action reloading a paged
     * list window) is enqueued INSIDE this same executor task and cannot race the
     * write: enqueuing the reload as a separate task would give it no happens-before
     * against the write on the shared multi-threaded pool, and its query could read
     * the PRE-write SQLite snapshot (Room/WAL) and emit stale data. This is the same
     * write-then-notify ordering {@link UpdateRssItemIsFavoriteCmd} uses for its
     * notifier emissions. The hook runs last, after this command's own unread-count
     * refresh, so a throwing hook cannot skip the notifier duty. The hook may call
     * {@code BaseRssItemsCmd#load()} from this background thread - it only enqueues
     * its window query onto the same executor. The hook is NOT invoked when the write
     * fails (the catch path skips it). The 2-arg {@link #execute(RssItem, boolean)}
     * delegates here with a null hook, so existing callers are unchanged.
     *
     * @param rssItem          the RSS item to update
     * @param isRead           the new isRead status
     * @param onWriteCompleted optional hook invoked on the executor thread after the
     *                         write commits, may be null
     */
    public void execute(RssItem rssItem, boolean isRead, Runnable onWriteCompleted) {
        rssItem.isRead = isRead;
        mExecutorService.execute(() -> {
            try {
                mRssRepository.updateRssItemIsRead(rssItem);
                mRssChannelStateNotifier.refreshUnreadCount();
                if (onWriteCompleted != null) {
                    onWriteCompleted.run();
                }
            } catch (Throwable t) {
                mLogger.e(TAG, mAppContext.getString(R.string.error_rss_read, rssItem.title), t);
            }
        });
    }
}
