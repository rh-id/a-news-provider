package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.os.Handler;
import android.util.Patterns;
import android.widget.Toast;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.NewRssWorker;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class NewRssChannelCmd {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<WorkManager> mWorkManager;
    private final ProviderValue<RssChangeNotifier> mRssChangeNotifier;
    private final ProviderValue<DeviceStatusNotifier> mDeviceStatusNotifier;
    private final BehaviorSubject<RssModel> mRssModelBehaviorSubject;
    private final BehaviorSubject<String> mUrlValidationBehaviorSubject;

    public NewRssChannelCmd(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mWorkManager = provider.lazyGet(WorkManager.class);
        mRssChangeNotifier = provider.lazyGet(RssChangeNotifier.class);
        mDeviceStatusNotifier = provider.lazyGet(DeviceStatusNotifier.class);
        mRssModelBehaviorSubject = BehaviorSubject.create();
        mUrlValidationBehaviorSubject = BehaviorSubject.create();
    }

    public boolean validUrl(String url) {
        boolean valid = true;
        if (url == null || url.isEmpty()) {
            valid = false;
            mUrlValidationBehaviorSubject.onNext(mAppContext.getString(R.string.url_is_required));
        } else if (!Patterns.WEB_URL.matcher(url).matches()) {
            valid = false;
            mUrlValidationBehaviorSubject.onNext(mAppContext.getString(R.string.invalid_url));
        } else if (url.startsWith("http://")) {
            valid = false;
            mUrlValidationBehaviorSubject.onNext(mAppContext.getString(R.string.http_not_allowed));
        } else {
            mUrlValidationBehaviorSubject.onNext("");
        }
        return valid;
    }

    public void execute(final String url) {
        mExecutorService.get().execute(() -> {
            final StringBuilder requestUrl = new StringBuilder(url);
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                requestUrl.insert(0, "https://");
            }
            if (!validUrl(requestUrl.toString())) {
                mRssModelBehaviorSubject.onError(new RuntimeException(mAppContext.getString(R.string.invalid_url)));
            } else {
                OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(NewRssWorker.class)
                        .setConstraints(new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build())
                        .setInputData(new Data.Builder()
                                .putString(ConstantsKey.KEY_STRING_URL, requestUrl.toString())
                                .build()
                        ).build();
                mWorkManager.get().enqueue(oneTimeWorkRequest);
                if (!mDeviceStatusNotifier.get().isOnline()) {
                    mHandler.get().post(() ->
                            Toast.makeText(mAppContext, R.string.feed_new_pending_online
                                    , Toast.LENGTH_LONG).show());
                }
            }
        });
    }

    public Flowable<RssModel> getRssModel() {
        return mRssChangeNotifier.get().liveNewRssModel()
                .doOnNext(rssModelOptional -> {
                    if (rssModelOptional.isPresent()) {
                        mRssModelBehaviorSubject.onNext(rssModelOptional.get());
                    }
                })
                .doOnError(mRssModelBehaviorSubject::onError)
                .concatMap(rssModelOptional ->
                        Flowable.fromObservable(mRssModelBehaviorSubject, BackpressureStrategy.BUFFER));
    }

    // validation message
    public Flowable<String> getUrlValidation() {
        return Flowable.fromObservable(mUrlValidationBehaviorSubject, BackpressureStrategy.BUFFER);
    }

    public String getValidationError() {
        String validation = mUrlValidationBehaviorSubject.getValue();
        if (validation == null) {
            validation = "";
        }
        return validation;
    }
}
