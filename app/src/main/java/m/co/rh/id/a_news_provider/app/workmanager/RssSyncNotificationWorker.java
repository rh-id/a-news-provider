package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class RssSyncNotificationWorker extends Worker {
    private static final String TAG = RssSyncNotificationWorker.class.getName();

    public RssSyncNotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        long[] channelIds =
                getInputData().getLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS);
        if (channelIds == null || channelIds.length <= 0) {
            return Result.failure();
        }
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        RssDao rssDao = provider.get(RssDao.class);

        int size = channelIds.length;
        ArrayList<RssModel> rssModels = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            long channelId = channelIds[i];
            RssChannel rssChannel = rssDao.findRssChannelById(channelId);
            if (rssChannel == null) {
                continue;
            }
            List<RssItem> rssItems = rssDao.findRssItemsByChannelId(channelId);
            ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
            rssItemArrayList.addAll(rssItems);
            rssModels.add(new RssModel(rssChannel, rssItemArrayList));
        }
        appNotificationHandler.postRssSyncNotification(rssModels);

        Data outputData = new Data.Builder()
                .putLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS, channelIds)
                .build();
        return Result.success(outputData);
    }
}
