package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.service.RssService;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

/**
 * UI-facing command for adding a new RSS channel. Thin adapter over the non-UI
 * {@link RssService}: runs the service add on a background executor and maps its
 * typed {@link RssService.AddFeedResult} to user-facing strings for the add dialog.
 */
public class NewRssChannelCmd {
    private final Context mAppContext;
    private final RssChangeNotifier mRssChangeNotifier;
    private final RssService mRssService;
    private final ExecutorService mExecutorService;
    private final BehaviorSubject<RssModel> mRssModelBehaviorSubject;
    private final BehaviorSubject<String> mUrlValidationBehaviorSubject;

    public NewRssChannelCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mRssService = provider.get(RssService.class);
        mExecutorService = provider.get(ExecutorService.class);
        mRssModelBehaviorSubject = BehaviorSubject.create();
        mUrlValidationBehaviorSubject = BehaviorSubject.create();
    }

    /**
     * Validates the given feed URL for the add dialog's keystroke validation and
     * pushes the validation message (empty string when valid) to getUrlValidation().
     *
     * @param url the raw feed URL input
     * @return true when the URL is valid, false otherwise
     */
    public boolean validUrl(String url) {
        RssService.UrlError urlError = mRssService.checkUrl(url);
        if (urlError == null) {
            mUrlValidationBehaviorSubject.onNext("");
            return true;
        }
        mUrlValidationBehaviorSubject.onNext(getUrlErrorMessage(urlError));
        return false;
    }

    /**
     * UI adapter for {@link RssService#addNewFeed(String)}: runs the add on the
     * background executor and maps the typed result to the user-facing messages
     * (also pushed to getUrlValidation() for inline display).
     * <p>
     * The redirect probe is best-effort: {@link RedirectDuplicateChecker} failures
     * fail open and the add proceeds to the normal fetch.
     *
     * @param url the raw feed URL input
     * @return Single that emits the normalized request URL after the worker is enqueued,
     * or errors with the validation/duplicate/redirect message, or errors when the
     * existing-channel lookup fails - a failed check never enqueues the worker
     */
    public Single<String> execute(final String url) {
        return Single.fromCallable(() -> mapResult(mRssService.addNewFeed(url)))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    private String mapResult(RssService.AddFeedResult result) {
        switch (result.kind) {
            case ADDED:
                mUrlValidationBehaviorSubject.onNext("");
                return result.requestUrl;
            case INVALID: {
                mUrlValidationBehaviorSubject.onNext(getUrlErrorMessage(result.urlError));
                throw new RuntimeException(getValidationError());
            }
            case DUPLICATE: {
                String message = mAppContext.getString(R.string.feed_already_added_as,
                        RssService.displayName(result.existing));
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message);
            }
            case DUPLICATE_REDIRECT: {
                String message = mAppContext.getString(R.string.error_feed_duplicate_redirect);
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message);
            }
            case DB_ERROR:
            case DUPLICATE_IN_FILE: // never produced by addNewFeed, handled as a generic add error
            default: {
                String message = mAppContext.getString(R.string.error_feed_add);
                mUrlValidationBehaviorSubject.onNext(message);
                throw new RuntimeException(message, result.cause);
            }
        }
    }

    private String getUrlErrorMessage(RssService.UrlError urlError) {
        switch (urlError) {
            case EMPTY:
                return mAppContext.getString(R.string.url_is_required);
            case INVALID:
                return mAppContext.getString(R.string.invalid_url);
            case HTTP_NOT_ALLOWED:
            default:
                return mAppContext.getString(R.string.http_not_allowed);
        }
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
