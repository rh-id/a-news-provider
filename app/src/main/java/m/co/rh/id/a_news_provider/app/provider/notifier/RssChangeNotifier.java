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
 * and when RSS items are marked as read (user-initiated via
 * {@link #itemsMarkedRead(Long)} or auto-aged by the unread retention window via
 * {@link #itemsMarkedReadAuto(Long)}).
 */
public class RssChangeNotifier {
    private final PublishSubject<Optional<RssModel>> mAddedRssModelPublishSubject;
    private final PublishSubject<Optional<RssChannel>> mUpdatedRssChannelPublishSubject;
    private final PublishSubject<List<RssModel>> mSyncedRssModelPublishSubject;
    private final PublishSubject<RssItem> mUpdatedRssItemSubject;
    private final PublishSubject<Optional<RssChannel>> mDeletedRssChannelPublishSubject;
    private final PublishSubject<Optional<Long>> mItemsMarkedReadSubject;
    private final PublishSubject<Optional<Long>> mItemsMarkedReadAutoSubject;

    public RssChangeNotifier() {
        mAddedRssModelPublishSubject = PublishSubject.create();
        mUpdatedRssChannelPublishSubject = PublishSubject.create();
        mSyncedRssModelPublishSubject = PublishSubject.create();
        mUpdatedRssItemSubject = PublishSubject.create();
        mDeletedRssChannelPublishSubject = PublishSubject.create();
        mItemsMarkedReadSubject = PublishSubject.create();
        mItemsMarkedReadAutoSubject = PublishSubject.create();
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
     * Emits a user-initiated items marked as read event (e.g. "mark all read").
     * Consumers that show user-facing feedback (HomePage toast) subscribe to this
     * event only; auto-aging emits on {@link #itemsMarkedReadAuto(Long)} instead.
     *
     * @param channelId the channel id of the marked items, null for all channels
     */
    public void itemsMarkedRead(Long channelId) {
        mItemsMarkedReadSubject.onNext(Optional.ofNullable(channelId));
    }

    /**
     * Emits an auto mark-read event raised by the unread retention window
     * (unread items older than the configured days aged to read at the end of a sync).
     * Kept separate from the user-initiated {@link #itemsMarkedRead(Long)} so user-facing
     * feedback (HomePage toast) is never triggered by background aging, while list-refresh
     * consumers subscribe to both events.
     *
     * @param channelId the channel id of the auto-marked items, null for all channels
     */
    public void itemsMarkedReadAuto(Long channelId) {
        mItemsMarkedReadAutoSubject.onNext(Optional.ofNullable(channelId));
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

    /**
     * Provides a Flowable stream of auto mark-read events (unread retention window).
     *
     * @return Flowable that emits the optional channel id of the auto-marked items, empty for all channels
     */
    public Flowable<Optional<Long>> getItemsMarkedReadAuto() {
        return Flowable.fromObservable(mItemsMarkedReadAutoSubject, BackpressureStrategy.BUFFER);
    }
}
