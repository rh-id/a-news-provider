package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.util.Patterns;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.NewRssWorker;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelCmd {
    private final Context mAppContext;
    private final WorkManager mWorkManager;
    private final RssChangeNotifier mRssChangeNotifier;
    private final BehaviorSubject<RssModel> mRssModelBehaviorSubject;
    private final BehaviorSubject<String> mUrlValidationBehaviorSubject;

    public NewRssChannelCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mWorkManager = provider.get(WorkManager.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
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
        final StringBuilder requestUrl = new StringBuilder(url);
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            requestUrl.insert(0, "https://");
        }
        if (!validUrl(requestUrl.toString())) {
            return;
        }
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(NewRssWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(new Data.Builder()
                        .putString(ConstantsKey.KEY_STRING_URL, requestUrl.toString())
                        .build()
                ).build();
        mWorkManager.enqueue(oneTimeWorkRequest);
    }

    public Flowable<RssModel> getRssModel() {
        return mRssChangeNotifier.liveNewRssModel()
                .doOnNext(rssModelOptional -> rssModelOptional.ifPresent(mRssModelBehaviorSubject::onNext))
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
