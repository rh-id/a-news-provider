package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;

import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.repository.PermanentRedirectGuard;
import m.co.rh.id.a_news_provider.app.provider.component.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.component.network.RssRequest;
import m.co.rh.id.a_news_provider.component.network.RssRequestFactory;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class NewRssWorker extends Worker {
    private static final String TAG = NewRssWorker.class.getName();

    public NewRssWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String url = getInputData().getString(ConstantsKey.KEY_STRING_URL);
        Context appContext = getApplicationContext();
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        RssRepository rssRepository = provider.get(RssRepository.class);
        RequestQueue requestQueue = provider.get(RequestQueue.class);
        ILogger logger = provider.get(ILogger.class);

        RequestFuture<RssModel> requestFuture = RequestFuture.newFuture();
        RssRequest rssRequest = provider.
                get(RssRequestFactory.class).
                newRssRequest(Request.Method.GET, url, requestFuture, requestFuture);
        requestQueue.add(rssRequest);
        try {
            RssModel rssModel = requestFuture.get(15, TimeUnit.SECONDS);
            // permanent-redirect duplicate guard (issue #50), BEFORE
            // persist: the add path has no existing row yet, so ANY channel
            // already owning the final url is a collision and the outcome is
            // cleared - the feed keeps its original url. This covers the two
            // holes the add-time redirect probe cannot: the probe fails OPEN
            // on transient errors, and OPML bulk import can enqueue both the
            // redirector and its target. A guard failure (DAO error) fails
            // closed through the catch below like any other fetch failure.
            provider.get(PermanentRedirectGuard.class).applyGuard(null, rssModel);
            RssModel persisted = rssRepository.persist(rssModel);
            rssChangeNotifier.liveNewRssModel(persisted);

            // User-facing log (now unconditional)
            logger.i(TAG, appContext.getString(R.string.feed_added, persisted.getRssChannel().feedName));
        } catch (Throwable t) {
            String userMessage;
            if (t.getCause() instanceof ParseError) {
                userMessage = appContext.getString(R.string.error_parse_data_from, url);
            } else {
                userMessage = appContext.getString(R.string.error_message, t.getMessage());
            }
            logger.e(TAG, userMessage, t);
        }

        return Result.success();
    }
}
