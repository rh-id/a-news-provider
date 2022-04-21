package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;
import android.util.Patterns;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class EditRssLinkCmd {
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final RssChangeNotifier mRssChangeNotifier;
    private final BehaviorSubject<String> mUrlValidationBehaviorSubject;

    public EditRssLinkCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
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

    public Single<String> execute(long rssItemId, final String url) {
        return Single.fromFuture(mExecutorService.submit(() -> {
                    final StringBuilder requestUrl = new StringBuilder(url);
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        requestUrl.insert(0, "https://");
                    }
                    if (validUrl(requestUrl.toString())) {
                        RssItem rssItem = mRssDao.findRssItemById(rssItemId);
                        rssItem.link = url;
                        mRssDao.updateRssItem(rssItem);
                        mRssChangeNotifier.updatedRssItem(rssItem);
                        return url;
                    } else {
                        throw new RuntimeException(getValidationError());
                    }
                })
        );
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
