package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class PeriodicRssSyncWorker extends Worker {

    public PeriodicRssSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        WorkManager workManager = provider.get(WorkManager.class);
        OneTimeWorkRequest rssSyncWorkRequest =
                new OneTimeWorkRequest.Builder(RssSyncWorker.class)
                        .build();
        OneTimeWorkRequest rssSyncShowNotificationWorkRequest =
                new OneTimeWorkRequest.Builder(RssSyncNotificationWorker.class)
                        .build();
        OneTimeWorkRequest rssSyncNotifyRequest =
                new OneTimeWorkRequest.Builder(RssSyncChangeNotifierWorker.class)
                        .build();
        workManager.beginWith(rssSyncWorkRequest)
                .then(rssSyncShowNotificationWorkRequest)
                .then(rssSyncNotifyRequest)
                .enqueue();
        return Result.success();
    }
}
