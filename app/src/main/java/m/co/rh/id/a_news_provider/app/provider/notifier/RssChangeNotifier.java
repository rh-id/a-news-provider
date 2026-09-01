package m.co.rh.id.a_news_provider.app.provider.notifier;


import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;

/**
 * A hub for RSS change events. Emits events when RSS models are added, synced, or updated,
 * and when RSS items are marked as read.
 */
public class RssChangeNotifier {
    private final PublishSubject<Optional<RssModel>> mAddedRssModelPublishSubject;
    private final PublishSubject<Optional<RssChannel>> mUpdatedRssChannelPublishSubject;
    private final PublishSubject<List<RssModel>> mSyncedRssModelPublishSubject;
    private final PublishSubject<RssItem> mUpdatedRssItemSubject;
    private final PublishSubject<Optional<RssChannel>> mDeletedRssChannelPublishSubject;
    private final PublishSubject<Optional<Long>> mItemsMarkedReadSubject;

    public RssChangeNotifier() {
        mAddedRssModelPublishSubject = PublishSubject.create();
        mUpdatedRssChannelPublishSubject = PublishSubject.create();
        mSyncedRssModelPublishSubject = PublishSubject.create();
        mUpdatedRssItemSubject = PublishSubject.create();
        mDeletedRssChannelPublishSubject = PublishSubject.create();
        mItemsMarkedReadSubject = PublishSubject.create();
    }

    /**
     * Emits a new RSS model that was successfully added.
     *
     * @param rssModel the new RSS model
     */
    public void liveNewRssModel(RssModel rssModel) {
        mAddedRssModelPublishSubject.onNext(Optional.ofNullable(rssModel));
    }

    /**
     * Emits an error when adding a new RSS model fails.
     *
     * @param throwable the error
     */
    public void newRssModelError(Throwable throwable) {
        mAddedRssModelPublishSubject.onNext(Optional.empty());
    }

    /**
     * Emits RSS models that were synced.
     *
     * @param rssModels the synced RSS models
     */
    public void liveSyncedRssModel(List<RssModel> rssModels) {
        mSyncedRssModelPublishSubject.onNext(rssModels);
    }

    /**
     * Emits an RSS channel update event.
     *
     * @param rssChannel the updated channel
     */
    public void updatedRssChannel(RssChannel rssChannel) {
        mUpdatedRssChannelPublishSubject.onNext(Optional.ofNullable(rssChannel));
    }

    /**
     * Emits an RSS item update event.
     *
     * @param rssItem the updated item
     */
    public void updatedRssItem(RssItem rssItem) {
        mUpdatedRssItemSubject.onNext(rssItem);
    }

    /**
     * Emits an RSS channel deletion event.
     *
     * @param rssChannel the deleted channel
     */
    public void deletedRssChannel(RssChannel rssChannel) {
        mDeletedRssChannelPublishSubject.onNext(Optional.ofNullable(rssChannel));
    }

    /**
     * Emits an items marked as read event.
     *
     * @param channelId the channel id of the marked items, null for all channels
     */
    public void itemsMarkedRead(Long channelId) {
        mItemsMarkedReadSubject.onNext(Optional.ofNullable(channelId));
    }

    /**
     * Provides a Flowable stream of new RSS model events.
     *
     * @return Flowable that emits optional RSS models
     */
    public Flowable<Optional<RssModel>> liveNewRssModel() {
        return Flowable.fromObservable(mAddedRssModelPublishSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of updated RSS channel events.
     *
     * @return Flowable that emits optional updated RSS channels
     */
    public Flowable<Optional<RssChannel>> updatedRssChannel() {
        return Flowable.fromObservable(mUpdatedRssChannelPublishSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of synced RSS model events.
     *
     * @return Flowable that emits lists of synced RSS models
     */
    public Flowable<List<RssModel>> liveSyncedRssModel() {
        return Flowable.fromObservable(mSyncedRssModelPublishSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of updated RSS item events.
     *
     * @return Flowable that emits updated RSS items
     */
    public Flowable<RssItem> getUpdatedRssItem() {
        return Flowable.fromObservable(mUpdatedRssItemSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of deleted RSS channel events.
     *
     * @return Flowable that emits optional deleted RSS channels
     */
    public Flowable<Optional<RssChannel>> deletedRssChannel() {
        return Flowable.fromObservable(mDeletedRssChannelPublishSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of items marked as read events.
     *
     * @return Flowable that emits the optional channel id of the marked items, empty for all channels
     */
    public Flowable<Optional<Long>> getItemsMarkedRead() {
        return Flowable.fromObservable(mItemsMarkedReadSubject, BackpressureStrategy.BUFFER);
    }
}
