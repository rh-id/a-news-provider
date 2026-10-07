package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class PauseRssChannelCmd {
    private static final String TAG = PauseRssChannelCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;

    public PauseRssChannelCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    public void execute(final long channelId, final boolean isPaused) {
        mExecutorService.execute(() -> {
            try {
                RssChannel rssChannel = mRssDao.findRssChannelById(channelId);
                if (rssChannel != null) {
                    mRssDao.updateRssChannelIsPaused(channelId, isPaused);
                    rssChannel.isPaused = isPaused;
                    mRssChangeNotifier.updatedRssChannel(rssChannel);
                } else {
                    mLogger.e(TAG, mAppContext.getString(R.string.record_not_found));
                }
            } catch (Throwable t) {
                mLogger.e(TAG, t.getMessage(), t);
            }
        });
    }
}
