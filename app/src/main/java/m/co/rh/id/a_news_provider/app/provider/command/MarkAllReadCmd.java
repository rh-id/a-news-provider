package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to mark RSS items as read.
 * Marks all items as read, or only the items of a given channel, then notifies the UI.
 */
public class MarkAllReadCmd {
    private static final String TAG = MarkAllReadCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final RssChannelStateNotifier mRssChannelStateNotifier;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;

    public MarkAllReadCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mRssChannelStateNotifier = provider.get(RssChannelStateNotifier.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Marks RSS items as read in the background.
     * If a channel id is provided, only the items of that channel are marked as read,
     * otherwise all items are marked as read.
     * Refreshes the unread count and emits an items marked as read event afterwards.
     *
     * @param channelId the channel id of the items to mark as read, null for all channels
     */
    public void execute(Long channelId) {
        mExecutorService.execute(() -> {
            try {
                if (channelId == null) {
                    mRssDao.markAllRssItemsRead();
                } else {
                    mRssDao.markRssItemsReadByChannelId(channelId);
                }
                mRssChannelStateNotifier.refreshUnreadCount();
                mRssChangeNotifier.itemsMarkedRead(channelId);
            } catch (Throwable t) {
                mLogger.e(TAG, mAppContext.getString(R.string.error_message, t.getMessage()), t);
            }
        });
    }
}
