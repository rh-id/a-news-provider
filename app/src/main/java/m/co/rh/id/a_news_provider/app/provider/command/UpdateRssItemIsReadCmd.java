package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
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
        rssItem.isRead = isRead;
        mExecutorService.execute(() -> {
            try {
                mRssRepository.updateRssItemIsRead(rssItem);
                mRssChannelStateNotifier.refreshUnreadCount();
            } catch (Throwable t) {
                mLogger.e(TAG, mAppContext.getString(R.string.error_rss_read, rssItem.title), t);
            }
        });
    }
}
