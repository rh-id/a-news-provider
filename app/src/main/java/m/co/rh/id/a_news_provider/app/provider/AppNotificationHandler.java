package m.co.rh.id.a_news_provider.app.provider;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.receiver.NotificationDeleteReceiver;
import m.co.rh.id.a_news_provider.base.dao.AndroidNotificationDao;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.AndroidNotification;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppNotificationHandler {
    public static final String KEY_INT_REQUEST_ID = "KEY_INT_REQUEST_ID";

    private static final String CHANNEL_ID_RSS_SYNC = "CHANNEL_ID_RSS_SYNC";
    private static final String GROUP_KEY_RSS_SYNC = "GROUP_KEY_RSS_SYNC";
    private static final int GROUP_SUMMARY_ID_RSS_SYNC = 0;

    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssChangeNotifier> mRssChangeNotifier;
    private final ProviderValue<AndroidNotificationDao> mAndroidNotificationDao;
    private final ProviderValue<RssDao> mRssDao;
    private final ProviderValue<Handler> mHandler;
    private final ProviderValue<ImageLoader> mImageLoader;

    public AppNotificationHandler(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssChangeNotifier = provider.lazyGet(RssChangeNotifier.class);
        mAndroidNotificationDao = provider.lazyGet(AndroidNotificationDao.class);
        mRssDao = provider.lazyGet(RssDao.class);
        mHandler = provider.lazyGet(Handler.class);
        mImageLoader = provider.lazyGet(ImageLoader.class);
    }

    public void postRssSyncNotification(List<RssModel> rssModels) {
        createRssSyncNotificationChannel();
        int totalNotification = 0;
        if (rssModels != null && !rssModels.isEmpty()) {
            for (RssModel rssModel :
                    rssModels) {
                RssChannel rssChannel = rssModel.getRssChannel();
                List<RssItem> rssItemList = rssModel.getRssItems();
                int totalUnread = 0;
                if (rssItemList != null && !rssItemList.isEmpty()) {
                    for (RssItem rssItem : rssItemList) {
                        if (!rssItem.isRead) {
                            totalUnread++;
                        }
                    }
                }
                if (totalUnread == 0) {
                    continue;
                }
                totalNotification++;
                AndroidNotification androidNotification = new AndroidNotification();
                androidNotification.groupKey = GROUP_KEY_RSS_SYNC;
                androidNotification.refId = rssModel.getRssChannel().id;
                mAndroidNotificationDao.get().insertNotification(androidNotification);
                Intent receiverIntent = new Intent(mAppContext, MainActivity.class);
                receiverIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
                int intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    intentFlag = PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(mAppContext, androidNotification.requestId, receiverIntent,
                        intentFlag);
                Intent deleteIntent = new Intent(mAppContext, NotificationDeleteReceiver.class);
                deleteIntent.putExtra(KEY_INT_REQUEST_ID, (Integer) androidNotification.requestId);
                PendingIntent deletePendingIntent = PendingIntent.getBroadcast(mAppContext, androidNotification.requestId, deleteIntent,
                        intentFlag);
                String title = mAppContext.getString(R.string.notification_rss_sync_title, rssModel.getRssChannel().feedName);
                String content = mAppContext.getString(R.string.notification_rss_sync_content, totalUnread);
                NotificationCompat.Builder builder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_RSS_SYNC)
                        .setSmallIcon(R.drawable.ic_notification_launcher)
                        .setColorized(true)
                        .setColor(mAppContext.getResources().getColor(R.color.orange_600))
                        .setContentTitle(title)
                        .setContentText(content)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setDeleteIntent(deletePendingIntent)
                        .setGroup(GROUP_KEY_RSS_SYNC)
                        .setAutoCancel(true);
                NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
                Consumer<NotificationCompat.Builder> postNotification = builder1 ->
                        notificationManagerCompat.notify(GROUP_KEY_RSS_SYNC,
                                androidNotification.requestId,
                                builder1.build());
                if (rssChannel.imageUrl != null && !rssChannel.imageUrl.isEmpty()) {
                    mHandler.get().post(() ->
                            mImageLoader.get().get(rssChannel.imageUrl, new ImageLoader.ImageListener() {
                                @Override
                                public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                                    builder.setLargeIcon(response.getBitmap());
                                    postNotification.accept(builder);
                                }

                                @Override
                                public void onErrorResponse(VolleyError error) {
                                    postNotification.accept(builder);
                                }
                            }));
                } else {
                    postNotification.accept(builder);
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (totalNotification > 0) {
                    NotificationCompat.Builder summaryBuilder = new NotificationCompat.Builder(mAppContext, CHANNEL_ID_RSS_SYNC)
                            .setSmallIcon(R.drawable.ic_notification_launcher)
                            .setColorized(true)
                            .setColor(mAppContext.getColor(R.color.orange_600))
                            .setContentText(mAppContext.getString(R.string.notification_rss_sync_content_summary, rssModels.size()))
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setGroup(GROUP_KEY_RSS_SYNC)
                            .setGroupSummary(true);
                    NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(mAppContext);
                    notificationManagerCompat.notify(GROUP_KEY_RSS_SYNC, GROUP_SUMMARY_ID_RSS_SYNC, summaryBuilder.build());
                }
            }
        }
    }

    private void createRssSyncNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = mAppContext.getString(R.string.notification_rss_sync_channel_name);
            String description = mAppContext.getString(R.string.notification_rss_sync_channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID_RSS_SYNC,
                    name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = mAppContext.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void removeNotification(Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() ->
                    mAndroidNotificationDao.get().deleteByRequestId((Integer) serializable));
        }
    }

    public void processNotification(@NonNull Intent intent) {
        Serializable serializable = intent.getSerializableExtra(KEY_INT_REQUEST_ID);
        if (serializable instanceof Integer) {
            mExecutorService.get().execute(() -> {
                AndroidNotification androidNotification =
                        mAndroidNotificationDao.get().findByRequestId((int) serializable);
                if (androidNotification != null && androidNotification.groupKey.equals(GROUP_KEY_RSS_SYNC)) {
                    RssChannel rssChannel = mRssDao.get().findRssChannelById(androidNotification.refId);
                    if (rssChannel != null) {
                        mRssChangeNotifier.get().selectRssChannel(rssChannel);
                    }
                }
            });
        }
    }
}
