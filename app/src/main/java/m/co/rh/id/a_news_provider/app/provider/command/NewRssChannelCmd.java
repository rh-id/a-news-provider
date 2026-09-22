package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.util.Patterns;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.app.workmanager.ConstantsKey;
import m.co.rh.id.a_news_provider.app.workmanager.NewRssWorker;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

public class NewRssChannelCmd {
    private final Context mAppContext;
    private final WorkManager mWorkManager;
    private final RssChangeNotifier mRssChangeNotifier;
    private final RssDao mRssDao;
    private final UrlNormalizer mUrlNormalizer;
    private final RedirectDuplicateChecker mRedirectDuplicateChecker;
    private final ExecutorService mExecutorService;
    private final BehaviorSubject<RssModel> mRssModelBehaviorSubject;
    private final BehaviorSubject<String> mUrlValidationBehaviorSubject;

    public NewRssChannelCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mWorkManager = provider.get(WorkManager.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mRssDao = provider.get(RssDao.class);
        mUrlNormalizer = provider.get(UrlNormalizer.class);
        mRedirectDuplicateChecker = provider.get(RedirectDuplicateChecker.class);
        mExecutorService = provider.get(ExecutorService.class);
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

    /**
     * Prepends "https://" when the scheme is missing. Unlike {@link #buildRequestUrl(String)}
     * the result is NOT normalized, so the caller's original spelling is preserved.
     *
     * @param url the raw feed URL input
     * @return the scheme-prepended URL, or null when the input is null
     */
    public static String prependScheme(String url) {
        if (url == null) {
            return null;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    /**
     * Builds the actual request URL for the given feed URL input: prepends
     * "https://" when the scheme is missing and normalizes the result.
     *
     * @param url the raw feed URL input
     * @return the normalized request URL
     */
    public String buildRequestUrl(String url) {
        if (url == null) {
            return null;
        }
        return mUrlNormalizer.normalizeUrl(prependScheme(url));
    }

    /**
     * Validates the URL, checks for an already added duplicate channel, probes whether
     * the feed redirects to an already added channel - reported inline as a duplicate
     * error - and enqueues the fetch worker when the feed is new.
     * <p>
     * The redirect probe is best-effort: {@link RedirectDuplicateChecker} failures
     * fail open and the add proceeds to the normal fetch.
     *
     * @param url the raw feed URL input
     * @return Single that emits the normalized request URL after the worker is enqueued,
     * or errors with the validation/duplicate/redirect message (also pushed to
     * getUrlValidation() for inline display), or errors when the existing-channel lookup
     * fails - a failed check never enqueues the worker
     */
    public Single<String> execute(final String url) {
        return Single.fromCallable(() -> {
            String requestUrl = buildRequestUrl(url);
            if (!validUrl(requestUrl)) {
                throw new RuntimeException(getValidationError());
            }
            RssChannel existing;
            try {
                // first param keeps the raw requested spelling so legacy un-normalized
                // rows still match on identical re-add, second param covers normalized
                // spellings of the same feed
                existing = mRssDao.findRssChannelByUrlVariants(prependScheme(url), requestUrl);
            } catch (Exception e) {
                // duplicate check failed - fail closed rather than risk a duplicate add
                String message = mAppContext.getString(R.string.error_feed_add);
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message, e);
            }
            if (existing != null) {
                String message = mAppContext.getString(R.string.feed_already_added_as,
                        displayName(existing));
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message);
            }
            if (mRedirectDuplicateChecker.isRedirectToExistingChannel(requestUrl)) {
                String message = mAppContext.getString(R.string.error_feed_duplicate_redirect);
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message);
            }
            enqueueWorker(requestUrl);
            return requestUrl;
        }).subscribeOn(Schedulers.from(mExecutorService));
    }

    private static String displayName(RssChannel rssChannel) {
        String displayName = rssChannel.feedName;
        if (displayName == null || displayName.isEmpty()) {
            displayName = rssChannel.title;
        }
        if (displayName == null || displayName.isEmpty()) {
            displayName = rssChannel.url;
        }
        if (displayName == null) {
            displayName = "";
        }
        return displayName;
    }

    private void enqueueWorker(String requestUrl) {
        OneTimeWorkRequest oneTimeWorkRequest = new OneTimeWorkRequest.Builder(NewRssWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(new Data.Builder()
                        .putString(ConstantsKey.KEY_STRING_URL, requestUrl)
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
