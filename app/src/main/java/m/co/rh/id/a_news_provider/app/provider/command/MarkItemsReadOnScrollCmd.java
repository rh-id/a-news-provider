package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Command to mark the RSS items that were scrolled past as read.
 * Sets the flags synchronously then persists the change in the background.
 * Deliberately emits NOTHING on RssChangeNotifier: a list reload here would reset
 * the scroll position mid-scroll. The {@code is_read = 0} guard in the update makes
 * repeated batches idempotent.
 */
public class MarkItemsReadOnScrollCmd {
    private static final String TAG = MarkItemsReadOnScrollCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssRepository mRssRepository;
    private final RssChannelStateNotifier mRssChannelStateNotifier;
    private final ILogger mLogger;

    public MarkItemsReadOnScrollCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssRepository = provider.get(RssRepository.class);
        mRssChannelStateNotifier = provider.get(RssChannelStateNotifier.class);
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Marks the given RSS items as read.
     * Items with a valid link get the flag set synchronously on the calling thread
     * (the in-memory flip doubles as the dedupe for repeated scroll batches), then the
     * change is persisted in the background by link so cross-channel duplicates stay
     * consistent. Items with a null or empty link are left untouched because they can
     * never match the link-based update - flipping them in memory only would leave a
     * ghost state that reverts on reload.
     * Emits NOTHING on RssChangeNotifier - a list reload here would jump the scroll
     * position while the user is scrolling.
     *
     * @param items the RSS items that were scrolled past
     */
    public void execute(List<RssItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        ArrayList<String> links = new ArrayList<>();
        for (RssItem rssItem : items) {
            if (rssItem.link != null && !rssItem.link.isEmpty()) {
                rssItem.isRead = true;
                links.add(rssItem.link);
            }
        }
        if (links.isEmpty()) {
            return;
        }
        mExecutorService.execute(() -> {
            try {
                mRssRepository.markItemsReadByLinks(links);
                mRssChannelStateNotifier.refreshUnreadCount();
            } catch (Throwable t) {
                mLogger.e(TAG, mAppContext.getString(R.string.error_message, t.getMessage()), t);
            }
        });
    }
}
