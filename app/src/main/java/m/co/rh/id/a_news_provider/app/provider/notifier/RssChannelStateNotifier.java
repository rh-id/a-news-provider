package m.co.rh.id.a_news_provider.app.provider.notifier;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

/**
 * Manages RSS channel selection state and unread count state.
 * Provides RxJava streams for UI components to observe channel selection changes and unread count updates.
 *
 * This class automatically reacts to RssChangeNotifier events:
 * - New RSS models trigger unread count refresh
 * - Synced RSS models trigger unread count refresh
 * - Updated RSS channels trigger unread count refresh and re-selection if the updated channel is currently selected
 * - Deleted RSS channels trigger unread count refresh
 *
 * All internal event handlers run on the notifier's executor scheduler (Scheduler.from(mExecutorService))
 * to ensure sequential processing and thread safety. Public Flowable accessors remain unconfigured
 * for threading, allowing UI consumers to manage their own observeOn preferences.
 *
 * Implements ProviderDisposable to properly clean up subscriptions when the provider is disposed.
 */
public class RssChannelStateNotifier implements ProviderDisposable {
    private static final String TAG = RssChannelStateNotifier.class.getName();

    private final AtomicBoolean mDisposed = new AtomicBoolean();
    private final ExecutorService mExecutorService;
    private final Scheduler mScheduler;
    private final RssRepository mRssRepository;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;
    private final BehaviorSubject<Optional<RssChannel>> mSelectedRssChannelBehaviourSubject;
    private final BehaviorSubject<Map<RssChannel, Integer>> mRssChannelUnReadCountMapBehaviourSubject;
    private final CompositeDisposable mCompositeDisposable;

    public RssChannelStateNotifier(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mScheduler = Schedulers.from(mExecutorService);
        mRssRepository = provider.get(RssRepository.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
        mSelectedRssChannelBehaviourSubject = BehaviorSubject.createDefault(Optional.empty());
        mRssChannelUnReadCountMapBehaviourSubject = BehaviorSubject.createDefault(new HashMap<>());
        mCompositeDisposable = new CompositeDisposable();

        // Subscribe to RssChangeNotifier events BEFORE initial refresh
        subscribeToRssChangeNotifier();

        // Initial state load
        refreshUnreadCount();
    }

    /**
     * Subscribes to RssChangeNotifier events to make this class reactive.
     * Subscriptions are added to CompositeDisposable for proper lifecycle management.
     */
    private void subscribeToRssChangeNotifier() {
        // Subscribe to liveNewRssModel - refresh only on successful add (Optional.isPresent)
        mCompositeDisposable.add(
            mRssChangeNotifier.liveNewRssModel()
                .filter(Optional::isPresent)
                .observeOn(mScheduler)
                .subscribe(ignored -> refreshUnreadCount(),
                    throwable -> mLogger.e(TAG, "Error in liveNewRssModel subscription", throwable))
        );

        // Subscribe to liveSyncedRssModel - refresh on every emission
        mCompositeDisposable.add(
            mRssChangeNotifier.liveSyncedRssModel()
                .observeOn(mScheduler)
                .subscribe(ignored -> refreshUnreadCount(),
                    throwable -> mLogger.e(TAG, "Error in liveSyncedRssModel subscription", throwable))
        );

        // Subscribe to updatedRssChannel - refresh and handle selection updates
        mCompositeDisposable.add(
            mRssChangeNotifier.updatedRssChannel()
                .observeOn(mScheduler)
                .subscribe(updatedChannelOptional -> {
                    refreshUnreadCount();
                    // If the updated channel is currently selected, re-push the selection
                    if (updatedChannelOptional.isPresent()) {
                        RssChannel updatedChannel = updatedChannelOptional.get();
                        Optional<RssChannel> selectedChannel = mSelectedRssChannelBehaviourSubject.getValue();
                        if (selectedChannel.isPresent() && selectedChannel.get().id.equals(updatedChannel.id)) {
                            selectRssChannel(updatedChannel);
                        }
                    }
                }, throwable -> mLogger.e(TAG, "Error in updatedRssChannel subscription", throwable))
        );

        // Subscribe to deletedRssChannel - refresh on deletion
        mCompositeDisposable.add(
            mRssChangeNotifier.deletedRssChannel()
                .observeOn(mScheduler)
                .subscribe(ignored -> refreshUnreadCount(),
                    throwable -> mLogger.e(TAG, "Error in deletedRssChannel subscription", throwable))
        );
    }

    /**
     * Selects an RSS channel as the current selection.
     *
     * @param rssChannel the channel to select, or null to clear selection
     */
    public void selectRssChannel(RssChannel rssChannel) {
        mSelectedRssChannelBehaviourSubject.onNext(Optional.ofNullable(rssChannel));
    }

    /**
     * Gets the currently selected RSS channel.
     *
     * @return Optional containing the selected channel, or empty if none selected
     */
    public Optional<RssChannel> getSelectedRssChannel() {
        return mSelectedRssChannelBehaviourSubject.getValue();
    }

    /**
     * Provides a Flowable stream of selected channel changes.
     *
     * @return Flowable that emits the selected channel state
     */
    public Flowable<Optional<RssChannel>> selectedRssChannel() {
        return Flowable.fromObservable(mSelectedRssChannelBehaviourSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Provides a Flowable stream of unread count updates.
     *
     * @return Flowable that emits the map of channels to their unread counts
     */
    public Flowable<Map<RssChannel, Integer>> rssChannelUnReadCount() {
        return Flowable.fromObservable(mRssChannelUnReadCountMapBehaviourSubject, BackpressureStrategy.BUFFER);
    }

    /**
     * Refreshes the unread count map from the database.
     * Enforces the invariant that the selected channel must exist in the current channel list.
     * Uses direct executor submission for sequential processing; mDisposed flag guards execution.
     */
    public void refreshUnreadCount() {
        mExecutorService.execute(this::doRefreshUnreadCount);
    }

    /**
     * Internal refresh implementation - checks disposal flag before execution.
     * Guarded by mDisposed AtomicBoolean to support cancellation after dispose().
     */
    private void doRefreshUnreadCount() {
        if (mDisposed.get()) {
            return;
        }
        try {
            Map<RssChannel, Integer> result = mRssRepository.getChannelUnreadCountMap();

            // Enforce invariant: selected channel must still exist
            Optional<RssChannel> selectedRssChannel = mSelectedRssChannelBehaviourSubject.getValue();
            boolean selectedRssStillExist = false;

            if (!result.isEmpty()) {
                for (RssChannel rssChannel : result.keySet()) {
                    if (!selectedRssStillExist) {
                        if (selectedRssChannel != null && selectedRssChannel.isPresent()) {
                            if (rssChannel.id.equals(selectedRssChannel.get().id)) {
                                selectedRssStillExist = true;
                            }
                        }
                    }
                }
            }

            if (!selectedRssStillExist) {
                mSelectedRssChannelBehaviourSubject.onNext(Optional.empty());
            }

            mRssChannelUnReadCountMapBehaviourSubject.onNext(result);
        } catch (Throwable t) {
            // BUG FIX: never call onError on the count subject - log instead so the subject survives
            mLogger.e(TAG, t.getMessage(), t);
        }
    }

    /**
     * Safely completes a BehaviorSubject during teardown.
     * Catches Throwable to prevent dispose() from crashing due to unexpected synchronous throws.
     * Note: RejectedExecutionException from executor shutdown is caught by RxJava and routed
     * to the global error handler (installed in MainApplication/TestApplication), not thrown here.
     * This is defense-in-depth for any other unexpected errors during subject completion.
     */
    private <T> void safeComplete(BehaviorSubject<T> subject) {
        try {
            subject.onComplete();
        } catch (Throwable t) {
            // Log but don't throw - dispose is terminal lifecycle, must complete cleanly
            try {
                mLogger.w(TAG, "Failed to complete subject during dispose", t);
            } catch (Throwable ignored) {
                // Logging might fail if already partially disposed; ignore and continue
            }
        }
    }

    @Override
    public void dispose(Context context) {
        // Terminal lifecycle - set flag first
        mDisposed.set(true);
        // Dispose all subscriptions (late adds auto-disposed)
        mCompositeDisposable.dispose();
        // Complete the subjects to signal completion to any observers
        // Only attempt completion if executor is still alive; otherwise skip to avoid RejectedExecutionException
        // which RxJava would route to the global error handler
        if (!mExecutorService.isShutdown()) {
            safeComplete(mSelectedRssChannelBehaviourSubject);
            safeComplete(mRssChannelUnReadCountMapBehaviourSubject);
        }
    }
}
