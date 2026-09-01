package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.List;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to update the isFavorite status of an RSS item.
 * Sets the flag synchronously then persists the change in the background.
 */
public class UpdateRssItemIsFavoriteCmd {
    private static final String TAG = UpdateRssItemIsFavoriteCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssRepository mRssRepository;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;

    public UpdateRssItemIsFavoriteCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssRepository = provider.get(RssRepository.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Updates the isFavorite status of an RSS item.
     * Sets the flag synchronously on the calling thread, then persists the change in the background.
     * Emits an updated event for every item matching the item's link,
     * so items of other channels with the same link are refreshed too.
     *
     * @param rssItem    the RSS item to update
     * @param isFavorite the new isFavorite status
     */
    public void execute(RssItem rssItem, boolean isFavorite) {
        rssItem.isFavorite = isFavorite;
        mExecutorService.execute(() -> {
            try {
                mRssRepository.updateRssItemIsFavorite(rssItem);
                List<RssItem> rssItems = mRssRepository.findRssItemsByLink(rssItem.link);
                for (RssItem updatedRssItem : rssItems) {
                    mRssChangeNotifier.updatedRssItem(updatedRssItem);
                }
            } catch (Throwable t) {
                mLogger.e(TAG, mAppContext.getString(R.string.error_message, t.getMessage()), t);
            }
        });
    }
}
