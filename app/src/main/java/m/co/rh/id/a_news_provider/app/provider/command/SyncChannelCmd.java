package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsWork;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncChangeNotifierWorker;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncWorker;
import m.co.rh.id.a_news_provider.base.provider.component.notifier.DeviceStatusNotifier;
import m.co.rh.id.aprovider.Provider;

/**
 * Targeted per-channel sync for the feed-health dashboard (issue #42):
 * "Sync now" on ONE feed enqueues a single-channel run of {@link RssSyncWorker}
 * instead of the app-wide {@link SyncRssCmd} run.
 * <p>
 * <b>Why a per-channel unique work name.</b> The request is enqueued as unique
 * work named {@link ConstantsWork#UNIQUE_RSS_SYNC_CHANNEL_PREFIX} + channelId
 * with {@link ExistingWorkPolicy#REPLACE} (a second "Sync now" on the same feed
 * replaces its still-pending request instead of queueing a duplicate). The
 * name MUST be per-channel: under the app-wide single name, the REPLACE (or
 * even the KEEP of the global policy) would let a "Sync now" on feed B silently
 * cancel feed A's pending sync - per-channel names keep the requests
 * independent, and they coexist with a concurrent app-wide sync run on purpose
 * (the accepted worst case is one benign double-fetch of the same feed, the
 * upsert makes the outcome identical).
 * <p>
 * <b>Why the notifier is chained with NO input.</b>
 * {@link RssSyncChangeNotifierWorker} reads the synced channel ids from its
 * INPUT data, and a {@code .then()} chain merges the parent's OUTPUT (the ids
 * RssSyncWorker actually synced) into it. Building the notifier with no
 * explicit input lets exactly those real ids flow through. Hardcoding
 * {@code [channelId]} instead would emit a phantom "synced" event when the
 * fetch fails or the worker skips the channel (paused/stop) - the dashboard
 * would refresh against a success that never happened.
 * <p>
 * <b>Why lastSyncRequestMillis is NOT stamped.</b> The sync-on-open debounce
 * gate (issue #50) reads that stamp to decide "content was refreshed recently,
 * skip the auto sync". A targeted retry of one failing feed must not re-arm
 * that gate - only user-visible app-wide sync requests re-arm it (see
 * SyncRssCmd, the deliberate contrast with this class).
 * <p>
 * Offline behaves like the app-wide sync: the work still enqueues (the CONNECTED
 * constraint holds it) and the same pending-online toast informs the user.
 */
public class SyncChannelCmd {
    private final Context mAppContext;
    private final Handler mHandler;
    private final WorkManager mWorkManager;
    private final ExecutorService mExecutorService;
    private final DeviceStatusNotifier mDeviceStatusNotifier;

    public SyncChannelCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mHandler = provider.get(Handler.class);
        mWorkManager = provider.get(WorkManager.class);
        mExecutorService = provider.get(ExecutorService.class);
        mDeviceStatusNotifier = provider.get(DeviceStatusNotifier.class);
    }

    /**
     * Enqueues the targeted sync of one channel (issue #42) as per-channel
     * unique work, chaining the change notifier with no explicit input so the
     * worker's real output drives the event (see the class javadoc).
     *
     * @param channelId the id of the channel to sync
     */
    public void execute(long channelId) {
        mExecutorService.execute(() -> {
            OneTimeWorkRequest.Builder rssSyncBuilder =
                    new OneTimeWorkRequest.Builder(RssSyncWorker.class);
            rssSyncBuilder.setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build());
            rssSyncBuilder.setInputData(new Data.Builder()
                    .putLong(ConstantsKey.KEY_LONG_CHANNEL_ID, channelId)
                    .build());
            OneTimeWorkRequest.Builder rssSyncNotifierBuilder =
                    new OneTimeWorkRequest.Builder(RssSyncChangeNotifierWorker.class);
            // deliberately NO input data: RssSyncWorker's OUTPUT (the ids it
            // really synced) merges into the notifier's input via the .then()
            // chain - a hardcoded [channelId] would announce a phantom success
            // when the fetch failed or the channel was skipped (issue #42)
            OneTimeWorkRequest rssSyncWorkRequest = rssSyncBuilder.build();
            OneTimeWorkRequest rssSyncNotifierWorkRequest = rssSyncNotifierBuilder
                    .build();
            mWorkManager.beginUniqueWork(
                    ConstantsWork.UNIQUE_RSS_SYNC_CHANNEL_PREFIX + channelId,
                    ExistingWorkPolicy.REPLACE, rssSyncWorkRequest)
                    .then(rssSyncNotifierWorkRequest)
                    .enqueue();
            if (!mDeviceStatusNotifier.isOnline()) {
                mHandler.post(() ->
                        Toast.makeText(mAppContext, R.string.feed_sync_pending_online
                                , Toast.LENGTH_LONG).show());
            }
        });
    }
}
