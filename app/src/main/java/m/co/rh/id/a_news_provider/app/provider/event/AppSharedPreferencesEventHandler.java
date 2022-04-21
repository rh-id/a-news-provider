package m.co.rh.id.a_news_provider.app.provider.event;

import android.content.Context;
import android.os.Handler;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsWork;
import m.co.rh.id.a_news_provider.app.workmanager.PeriodicRssSyncWorker;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

public class AppSharedPreferencesEventHandler implements ProviderDisposable {
    private ProviderValue<Handler> mHandler;
    private ExecutorService mExecutorService;
    private ProviderValue<WorkManager> mWorkManager;
    private ProviderValue<INavigator> mNavigator;
    private CompositeDisposable mCompositeDisposable;
    private AppSharedPreferences mAppSharedPreferences;

    public AppSharedPreferencesEventHandler(Provider provider) {
        mHandler = provider.lazyGet(Handler.class);
        mExecutorService = provider.get(ExecutorService.class);
        mWorkManager = provider.lazyGet(WorkManager.class);
        mNavigator = provider.lazyGet(INavigator.class);
        mCompositeDisposable = new CompositeDisposable();
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
        handle();
    }

    private void handle() {
        mCompositeDisposable.add(mAppSharedPreferences.isPeriodicSyncInitFlow()
                .take(1)
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(aBoolean -> {
                    if (!aBoolean) {
                        initPeriodicSync();
                        mAppSharedPreferences.setPeriodicSyncInit(true);
                    }
                })
        );
        mCompositeDisposable.add(mAppSharedPreferences.getPeriodicSyncRssHourFlow()
                .skip(1) // ignore first value, as the first value is initialization value
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(integer -> initPeriodicSync())
        );
        mCompositeDisposable.add(mAppSharedPreferences.getIsEnablePeriodicSyncFlow()
                .skip(1) // ignore first value, as the first value is initialization value
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(aBoolean -> initPeriodicSync())
        );
        mCompositeDisposable.add(mAppSharedPreferences.getSelectedThemeFlow()
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(integer -> mHandler.get().post(() ->
                        AppCompatDelegate.setDefaultNightMode(integer))));
        mCompositeDisposable.add(mAppSharedPreferences.getIsOneHandModeFlow()
                .skip(1)
                .observeOn(Schedulers.from(mExecutorService))
                .subscribe(aBoolean -> mHandler.get().post(() -> {
                    // refresh all route after one hand mode changed
                    mNavigator.get().reBuildAllRoute();
                }))
        );
    }

    private void initPeriodicSync() {
        if (mAppSharedPreferences.getPeriodicSyncRssHour() > 0 && mAppSharedPreferences.isEnablePeriodicSync()) {
            PeriodicWorkRequest.Builder rssSyncBuilder = new PeriodicWorkRequest.Builder(PeriodicRssSyncWorker.class,
                    mAppSharedPreferences.getPeriodicSyncRssHour(), TimeUnit.HOURS);
            rssSyncBuilder.setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build());
            PeriodicWorkRequest periodicWorkRequest = rssSyncBuilder.build();
            mWorkManager.get().enqueueUniquePeriodicWork(ConstantsWork.UNIQUE_PERIODIC_RSS_SYNC,
                    ExistingPeriodicWorkPolicy.REPLACE
                    , periodicWorkRequest);
        } else {
            mWorkManager.get().cancelUniqueWork(ConstantsWork.UNIQUE_PERIODIC_RSS_SYNC);
        }
    }

    @Override
    public void dispose(Context context) {
        mCompositeDisposable.dispose();
    }
}
