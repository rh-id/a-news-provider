package m.co.rh.id.a_news_provider.app.provider.command;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to delete an RSS channel.
 * Deletes the channel in the background and emits deletion event.
 */
public class DeleteRssChannelCmd {
    private static final String TAG = DeleteRssChannelCmd.class.getName();
    private final ExecutorService mExecutorService;
    private final RssRepository mRssRepository;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;

    public DeleteRssChannelCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssRepository = provider.get(RssRepository.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Deletes an RSS channel.
     *
     * @param rssChannel the RSS channel to delete
     */
    public void execute(RssChannel rssChannel) {
        mExecutorService.execute(() -> {
            try {
                mRssRepository.deleteRssChannel(rssChannel);
                mRssChangeNotifier.deletedRssChannel(rssChannel);
            } catch (Throwable t) {
                mLogger.e(TAG, t.getMessage(), t);
            }
        });
    }
}
