package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsWork;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncChangeNotifierWorker;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncWorker;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.base.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.aprovider.Provider;

public class SyncRssCmd {
    private final Context mAppContext;
    private final Handler mHandler;
    private final WorkManager mWorkManager;
    private final ExecutorService mExecutorService;
    private final RssChangeNotifier mRssChangeNotifier;
    private final DeviceStatusNotifier mDeviceStatusNotifier;

    public SyncRssCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mHandler = provider.get(Handler.class);
        mWorkManager = provider.get(WorkManager.class);
        mExecutorService = provider.get(ExecutorService.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mDeviceStatusNotifier = provider.get(DeviceStatusNotifier.class);
    }

    public void execute() {
        mExecutorService
                .execute(() -> {
                    OneTimeWorkRequest.Builder rssSyncBuilder =
                            new OneTimeWorkRequest.Builder(RssSyncWorker.class);
                    rssSyncBuilder.setConstraints(new Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build());
                    OneTimeWorkRequest.Builder rssSyncNotifierBuilder =
                            new OneTimeWorkRequest.Builder(RssSyncChangeNotifierWorker.class);

                    OneTimeWorkRequest rssSyncWorkRequest = rssSyncBuilder.build();
                    OneTimeWorkRequest rssSyncNotifierWorkRequest = rssSyncNotifierBuilder
                            .build();
                    mWorkManager.beginUniqueWork(ConstantsWork.UNIQUE_RSS_SYNC,
                            ExistingWorkPolicy.KEEP, rssSyncWorkRequest)
                            .then(rssSyncNotifierWorkRequest)
                            .enqueue();
                    if (!mDeviceStatusNotifier.isOnline()) {
                        mHandler.post(() ->
                                Toast.makeText(mAppContext, R.string.feed_sync_pending_online
                                        , Toast.LENGTH_LONG).show());
                    }
                });
    }

    public Flowable<List<RssModel>> syncedRss() {
        return mRssChangeNotifier.liveSyncedRssModel();
    }
}
