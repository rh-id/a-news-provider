package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.app.network.RssRequest;
import m.co.rh.id.a_news_provider.app.network.RssRequestFactory;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.aprovider.Provider;

public class RssSyncWorker extends Worker {
    private static final String TAG = RssSyncWorker.class.getName();

    public RssSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        RssDao rssDao = provider.get(RssDao.class);
        RssRequestFactory rssRequestFactory = provider.get(RssRequestFactory.class);
        RequestQueue requestQueue = provider.get(RequestQueue.class);
        List<RssChannel> rssChannelList = rssDao.loadAllRssChannel();
        List<RequestFuture<RssModel>> requestFutureList = new ArrayList<>();
        for (RssChannel rssChannel : rssChannelList) {
            RequestFuture<RssModel> requestFuture = RequestFuture.newFuture();
            RssRequest rssRequest = rssRequestFactory.
                    newRssRequest(Request.Method.GET, rssChannel.url, requestFuture, requestFuture);
            requestQueue.add(rssRequest);
            requestFutureList.add(requestFuture);
        }

        List<RssModel> rssModels = new ArrayList<>();
        for (RequestFuture<RssModel> requestFuture : requestFutureList) {
            try {
                rssModels.add(requestFuture.get());
            } catch (Throwable throwable) {
                Log.e(TAG, "Failed to sync some RSS", throwable);
            }
        }

        int size = rssModels.size();
        long[] channelIds = new long[size];
        for (int i = 0; i < size; i++) {
            channelIds[i] = rssModels.get(i).getRssChannel().id;
        }
        Data outputData = new Data.Builder()
                .putLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS, channelIds)
                .build();
        return Result.success(outputData);
    }
}
