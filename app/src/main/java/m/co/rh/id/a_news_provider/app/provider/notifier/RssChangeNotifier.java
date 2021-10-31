package m.co.rh.id.a_news_provider.app.provider.notifier;


import android.content.Context;

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
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * A hub to handle RSS selection, updates, deletion, and changes and to notify accordingly
 */
public class RssChangeNotifier {
    private static final String TAG = RssChangeNotifier.class.getName();
    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssDao> mRssDao;
    private final PublishSubject<Optional<RssModel>> mAddedRssModelPublishSubject;
    private final PublishSubject<Optional<RssChannel>> mUpdatedRssChannelPublishSubject;
    private final BehaviorSubject<Optional<RssChannel>> mSelectedRssChannelBehaviourSubject;
    private final BehaviorSubject<Map<RssChannel, Integer>> mRssChannelUnReadCountMapBehaviourSubject;
    private final PublishSubject<RssItem> mReadRssItemPublishSubject;
    private final PublishSubject<List<RssModel>> mSyncedRssModelPublishSubject;

    public RssChangeNotifier(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
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
                    Optional<RssChannel> selectedRssChannel = mSelectedRssChannelBehaviourSubject.getValue();
                    boolean selectedRssStillExist = false;
                    for (RssChannel rssChannel : rssChannelList) {
                        mapResult.put(rssChannel, mRssDao.get().countUnReadRssItems(rssChannel.id));
                        if (!selectedRssStillExist) {
                            if (selectedRssChannel != null && selectedRssChannel.isPresent()) {
                                if (rssChannel.id.equals(selectedRssChannel.get().id)) {
                                    selectedRssStillExist = true;
                                }
                            }
                        }
                    }
                    if (!selectedRssStillExist) {
                        mSelectedRssChannelBehaviourSubject.onNext(Optional.empty());
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
        refreshRssChannelCount();
    }

    public void liveNewRssModel(RssModel rssModel) {
        mAddedRssModelPublishSubject.onNext(Optional.ofNullable(rssModel));
        if (!mAddedRssModelPublishSubject.hasObservers()) {
            mLogger.get().i(TAG, mAppContext.getString(R.string.feed_added, rssModel
                    .getRssChannel().feedName));
        }
        refreshRssChannelCount();
    }

    public void newRssModelError(Throwable throwable) {
        mLogger.get().e(TAG, mAppContext.getString(R.string.error_feed_add),
                throwable);
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
                mLogger.get().e(TAG,
                        mAppContext.getString(R.string.error_rss_read, rssItem.title
                        ), throwable);
            }
        });
    }

    public void selectRssChannel(RssChannel rssChannel) {
        mSelectedRssChannelBehaviourSubject.onNext(Optional.ofNullable(rssChannel));
    }

    public void deleteRssChannel(RssChannel rssChannel) {
        mExecutorService.get().execute(() -> {
            mRssDao.get().deleteRssChannel(rssChannel);
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
                if (selectedChannel != null && selectedChannel.isPresent()) {
                    if (selectedChannel.get().id.equals(rssChannel.id)) {
                        selectRssChannel(rssChannel);
                    }
                }
            } catch (Throwable throwable) {
                mLogger.get().e(TAG, mAppContext.getString(R.string.error_rss_channel_update,
                        rssChannel.feedName), throwable);
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
