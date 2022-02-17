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
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsWork;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncChangeNotifierWorker;
import m.co.rh.id.a_news_provider.app.workmanager.RssSyncWorker;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class SyncRssCmd {
    private final Context mAppContext;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<WorkManager> mWorkManager;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssChangeNotifier> mRssChangeNotifier;
    private final ProviderValue<DeviceStatusNotifier> mDeviceStatusNotifier;

    public SyncRssCmd(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mHandler = provider.lazyGet(Handler.class);
        mWorkManager = provider.lazyGet(WorkManager.class);
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssChangeNotifier = provider.lazyGet(RssChangeNotifier.class);
        mDeviceStatusNotifier = provider.lazyGet(DeviceStatusNotifier.class);
    }

    public void execute() {
        mExecutorService.get()
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
                    mWorkManager.get().beginUniqueWork(ConstantsWork.UNIQUE_RSS_SYNC,
                            ExistingWorkPolicy.KEEP, rssSyncWorkRequest)
                            .then(rssSyncNotifierWorkRequest)
                            .enqueue();
                    if (!mDeviceStatusNotifier.get().isOnline()) {
                        mHandler.get().post(() ->
                                Toast.makeText(mAppContext, R.string.feed_sync_pending_online
                                        , Toast.LENGTH_LONG).show());
                    }
                });
    }

    public Flowable<List<RssModel>> syncedRss() {
        return mRssChangeNotifier.get().liveSyncedRssModel();
    }
}
