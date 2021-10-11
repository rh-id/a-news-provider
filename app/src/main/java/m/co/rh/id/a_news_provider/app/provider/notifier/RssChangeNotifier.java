package m.co.rh.id.a_news_provider.app.provider.notifier;


import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * A hub to handle RSS selection, updates, deletion, and changes and to notify accordingly
 */
public class RssChangeNotifier {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<RssDao> mRssDao;
    private final PublishSubject<Optional<RssModel>> mAddedRssModelPublishSubject;
    private final PublishSubject<Optional<RssChannel>> mUpdatedRssChannelPublishSubject;
    private final BehaviorSubject<Optional<RssChannel>> mSelectedRssChannelBehaviourSubject;
    private final BehaviorSubject<Map<RssChannel, Integer>> mRssChannelUnReadCountMapBehaviourSubject;
    private final PublishSubject<RssItem> mReadRssItemPublishSubject;
    private final PublishSubject<List<RssModel>> mSyncedRssModelPublishSubject;

    public RssChangeNotifier(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mRssDao = provider.lazyGet(RssDao.class);
        mAddedRssModelPublishSubject = PublishSubject.create();
        mUpdatedRssChannelPublishSubject = PublishSubject.create();
        mSelectedRssChannelBehaviourSubject = BehaviorSubject.createDefault(Optional.empty());
        mRssChannelUnReadCountMapBehaviourSubject = BehaviorSubject.createDefault(new HashMap<>());
        mReadRssItemPublishSubject = PublishSubject.create();
        mSyncedRssModelPublishSubject = PublishSubject.create();
        refreshRssChannelCount();
    }

    private void refreshRssChannelCount() {
        mExecutorService.get().execute(() -> {
            try {
                Map<RssChannel, Integer> mapResult = new HashMap<>();
                List<RssChannel> rssChannelList = mRssDao.get().loadAllRssChannel();
                if (rssChannelList != null && !rssChannelList.isEmpty()) {
                    for (RssChannel rssChannel : rssChannelList) {
                        mapResult.put(rssChannel, mRssDao.get().countUnReadRssItems(rssChannel.id));
                    }
                }
                mRssChannelUnReadCountMapBehaviourSubject.onNext(mapResult);
            } catch (Throwable t) {
                mRssChannelUnReadCountMapBehaviourSubject.onError(t);
            }
        });
    }

    // notify that these RSS have been synced
    public void liveSyncedRssModel(List<RssModel> rssModels) {
        mSyncedRssModelPublishSubject.onNext(rssModels);
    }

    public void liveNewRssModel(RssModel rssModel) {
        mAddedRssModelPublishSubject.onNext(Optional.ofNullable(rssModel));
        if (!mAddedRssModelPublishSubject.hasObservers()) {
            mHandler.get().post(() -> Toast.makeText(mAppContext,
                    mAppContext.getString(R.string.feed_added, rssModel
                            .getRssChannel().feedName),
                    Toast.LENGTH_LONG).show());
        }
        refreshRssChannelCount();
    }

    public void newRssModelError(Throwable throwable) {
        mHandler.get().post(() -> Toast.makeText(mAppContext,
                mAppContext.getString(R.string.feed_add_error, throwable.getMessage()),
                Toast.LENGTH_LONG).show());
        mAddedRssModelPublishSubject.onErrorReturnItem(Optional.empty());
    }

    public void readRssItem(RssItem rssItem) {
        rssItem.isRead = true;
        mExecutorService.get().execute(() -> {
            try {
                mRssDao.get().updateRssItem(rssItem);
                mReadRssItemPublishSubject.onNext(rssItem);
                refreshRssChannelCount();
            } catch (Throwable throwable) {
                mHandler.get().post(() -> Toast.makeText(mAppContext,
                        mAppContext.getString(R.string.rss_read_error, rssItem.title,
                                throwable.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    public void selectRssChannel(RssChannel rssChannel) {
        mSelectedRssChannelBehaviourSubject.onNext(Optional.ofNullable(rssChannel));
    }

    public void deleteRssChannel(RssChannel rssChannel) {
        mExecutorService.get().execute(() -> {
            mRssDao.get().deleteRssChannel(rssChannel);
            int totalRssItems = mRssDao.get().countRssItems();
            if (totalRssItems == 0 ||
                    mSelectedRssChannelBehaviourSubject.getValue().get().id.equals(rssChannel.id)) {
                mSelectedRssChannelBehaviourSubject.onNext(Optional.empty());
            }
            refreshRssChannelCount();
        });
    }


    public void updatedRssChannel(RssChannel rssChannel) {
        mExecutorService.get().execute(() -> {
            try {
                mUpdatedRssChannelPublishSubject.onNext(Optional.ofNullable(rssChannel));
                refreshRssChannelCount();

                // if selected channel updated, re-push the selected rss channel to update
                Optional<RssChannel> selectedChannel = mSelectedRssChannelBehaviourSubject.getValue();
                if (selectedChannel.isPresent()) {
                    if (selectedChannel.get().id.equals(rssChannel.id)) {
                        selectRssChannel(rssChannel);
                    }
                }
            } catch (Throwable throwable) {
                mHandler.get().post(() -> Toast.makeText(mAppContext,
                        mAppContext.getString(R.string.rss_channel_update_error,
                                rssChannel.feedName,
                                throwable.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    public Flowable<Optional<RssModel>> liveNewRssModel() {
        return Flowable.fromObservable(mAddedRssModelPublishSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Optional<RssChannel>> selectedRssChannel() {
        return Flowable.fromObservable(mSelectedRssChannelBehaviourSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<Map<RssChannel, Integer>> rssChannelUnReadCount() {
        return Flowable.fromObservable(mRssChannelUnReadCountMapBehaviourSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<List<RssModel>> liveSyncedRssModel() {
        return Flowable.fromObservable(mSyncedRssModelPublishSubject, BackpressureStrategy.BUFFER);
    }
}
