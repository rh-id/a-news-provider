package m.co.rh.id.a_news_provider.app.provider.command;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.dao.ArticleContentDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to delete an RSS channel.
 * Deletes the channel in the background, sweeps the article-content cache for
 * links the deletion orphaned (issue #28), and emits deletion event.
 */
public class DeleteRssChannelCmd {
    private static final String TAG = DeleteRssChannelCmd.class.getName();
    private final ExecutorService mExecutorService;
    private final RssRepository mRssRepository;
    private final ArticleContentDao mArticleContentDao;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;

    public DeleteRssChannelCmd(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mRssRepository = provider.get(RssRepository.class);
        mArticleContentDao = provider.get(ArticleContentDao.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Deletes an RSS channel.
     * After the channel's item rows are gone, the article-content cache (link-keyed,
     * issue #28) is swept for rows whose link no longer exists on ANY rss_item -
     * links still carried by another channel keep their cached content. The sweep
     * runs in its OWN catch-all so a failure there can never skip the deletion
     * event (the channel is already gone by then); the next sync's prune-step sweep
     * self-heals anything missed here.
     *
     * @param rssChannel the RSS channel to delete
     */
    public void execute(RssChannel rssChannel) {
        mExecutorService.execute(() -> {
            try {
                mRssRepository.deleteRssChannel(rssChannel);
            } catch (Throwable t) {
                mLogger.e(TAG, t.getMessage(), t);
                return;
            }
            try {
                mArticleContentDao.deleteOrphanArticleContent();
            } catch (Throwable t) {
                mLogger.e(TAG, t.getMessage(), t);
            }
            mRssChangeNotifier.deletedRssChannel(rssChannel);
        });
    }
}
