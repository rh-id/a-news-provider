package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
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
        // Rows the just-finished sync touched (updated_date_time >= syncStart) are the
        // only ones allowed to drive notifications: with upsert retention (issue #37)
        // unread items that fell out of a feed are no longer deleted, so counting all
        // unread rows would re-notify forever with a growing count (autoMarkReadDays
        // defaults OFF). Fallback 0 keeps the old include-everything behavior for
        // in-flight work enqueued before this key existed.
        long syncStartMillis =
                getInputData().getLong(ConstantsKey.KEY_LONG_SYNC_START_MILLIS, 0L);
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
            List<RssItem> rssItems =
                    rssDao.findUnreadRssItemsByChannelIdUpdatedSince(channelId, syncStartMillis);
            ArrayList<RssItem> rssItemArrayList = new ArrayList<>();
            rssItemArrayList.addAll(rssItems);
            rssModels.add(new RssModel(rssChannel, rssItemArrayList));
        }
        appNotificationHandler.postRssSyncNotification(rssModels);

        Data outputData = new Data.Builder()
                .putLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS, channelIds)
                .putLong(ConstantsKey.KEY_LONG_SYNC_START_MILLIS, syncStartMillis)
                .build();
        return Result.success(outputData);
    }
}
