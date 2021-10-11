package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.app.network.RssRequest;
import m.co.rh.id.a_news_provider.app.network.RssRequestFactory;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.BaseApplication;
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
        RequestQueue requestQueue = provider.get(RequestQueue.class);
        RequestFuture<RssModel> requestFuture = RequestFuture.newFuture();
        RssRequest rssRequest = provider.
                get(RssRequestFactory.class).
                newRssRequest(Request.Method.GET, url, requestFuture, requestFuture);
        requestQueue.add(rssRequest);
        try {
            RssModel rssModel = requestFuture.get();
            rssChangeNotifier.liveNewRssModel(rssModel);
        } catch (Throwable t) {
            if (t.getCause() instanceof ParseError) {
                rssChangeNotifier.newRssModelError(new RuntimeException(
                        appContext.getString(R.string.error_parse_data_from,
                                url)));
            } else {
                rssChangeNotifier.newRssModelError(new RuntimeException(
                        appContext.getString(R.string.error_message,
                                t.getMessage())));
            }

        }

        return Result.success();
    }
}
