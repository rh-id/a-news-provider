package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.RequestFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.component.network.RssRequest;
import m.co.rh.id.a_news_provider.component.network.RssRequestFactory;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class RssSyncWorker extends Worker {
    private static final String TAG = RssSyncWorker.class.getName();

    public RssSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Captured before any network request so chained workers can filter on
        // "touched by THIS sync": upsert stamps synced rows with updated_date_time
        // >= this value, retained rows keep their older stamp (issue #37).
        long syncStartMillis = System.currentTimeMillis();
        Provider provider = BaseApplication.of(getApplicationContext()).getProvider();
        RssDao rssDao = provider.get(RssDao.class);
        RssRepository rssRepository = provider.get(RssRepository.class);
        RssRequestFactory rssRequestFactory = provider.get(RssRequestFactory.class);
        RequestQueue requestQueue = provider.get(RequestQueue.class);
        List<RssChannel> rssChannelList = rssDao.loadAllRssChannel();
        List<RequestFuture<RssModel>> requestFutureList = new ArrayList<>();
        for (RssChannel rssChannel : rssChannelList) {
            if (rssChannel.isPaused) {
                continue;
            }
            RequestFuture<RssModel> requestFuture = RequestFuture.newFuture();
            RssRequest rssRequest = rssRequestFactory.
                    newRssRequest(Request.Method.GET, rssChannel.url, requestFuture, requestFuture);
            requestQueue.add(rssRequest);
            requestFutureList.add(requestFuture);
        }

        List<RssModel> rssModels = new ArrayList<>();
        for (RequestFuture<RssModel> requestFuture : requestFutureList) {
            try {
                RssModel rssModel = requestFuture.get(15, TimeUnit.SECONDS);
                RssModel persisted = rssRepository.persist(rssModel);
                rssModels.add(persisted);
            } catch (Throwable throwable) {
                provider.get(ILogger.class)
                        .d(TAG, getApplicationContext()
                                        .getString(R.string.error_failed_to_sync_some_rss),
                                throwable);
            }
        }

        int size = rssModels.size();
        long[] channelIds = new long[size];
        for (int i = 0; i < size; i++) {
            channelIds[i] = rssModels.get(i).getRssChannel().id;
        }

        autoMarkOldItemsRead(provider, rssRepository);
        pruneOldItems(provider, rssRepository);

        Data outputData = new Data.Builder()
                .putLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS, channelIds)
                .putLong(ConstantsKey.KEY_LONG_SYNC_START_MILLIS, syncStartMillis)
                .build();
        return Result.success(outputData);
    }

    /**
     * Auto mark-read (unread retention window): at the end of the sync, unread items
     * older than the configured number of days are marked as read. Runs BEFORE the
     * output data is built so chained workers observe post-aging unread counts.
     * This path self-refreshes its consumers (unread counts + list reload events)
     * because downstream workers can be skipped entirely (they return
     * {@code Result.failure()} when no channel was synced, e.g. all channels paused).
     * A failure here must never fail the sync, hence the catch-all.
     *
     * @param provider      the app provider
     * @param rssRepository the repository used to apply the aging update
     */
    private void autoMarkOldItemsRead(Provider provider, RssRepository rssRepository) {
        try {
            AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
            int autoMarkReadDays = appSharedPreferences.getAutoMarkReadDays();
            if (autoMarkReadDays <= 0) {
                return;
            }
            long cutoffMillis = System.currentTimeMillis()
                    - TimeUnit.DAYS.toMillis(autoMarkReadDays);
            int rowsUpdated = rssRepository.markOldItemsRead(cutoffMillis);
            if (rowsUpdated > 0) {
                provider.get(RssChannelStateNotifier.class).refreshUnreadCount();
                provider.get(RssChangeNotifier.class).itemsMarkedReadAuto(null);
            }
        } catch (Throwable throwable) {
            provider.get(ILogger.class)
                    .e(TAG, getApplicationContext()
                                    .getString(R.string.error_auto_mark_read_failed),
                            throwable);
        }
    }

    /**
     * Keep-read-items retention window (issue #37 phase 2): at the end of the sync,
     * read non-favorite items that no longer appear in their feed are deleted once
     * their last-seen-in-feed stamp (updated_date_time) is older than the configured
     * number of days. Runs AFTER {@link #autoMarkOldItemsRead(Provider, RssRepository)}
     * so the two windows compose (unread ages to read, a later sync prunes it once
     * aged) and inside this worker like that pass - no chained worker, before the
     * output data is built. Items still present in a feed are re-stamped by the
     * upsert every sync and never pruned. Deleting read rows cannot change unread
     * counts, so no unread-count refresh runs; only open list windows showing read
     * items must reload, which the {@code itemsPrunedAuto} event triggers. A failure
     * here must never fail the sync, hence the catch-all. Paused channels are
     * included by design (the setting is a global opt-in).
     *
     * @param provider      the app provider
     * @param rssRepository the repository used to run the prune update
     */
    private void pruneOldItems(Provider provider, RssRepository rssRepository) {
        try {
            AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
            int keepReadItemsDays = appSharedPreferences.getKeepReadItemsDays();
            if (keepReadItemsDays <= 0) {
                return;
            }
            long cutoffMillis = System.currentTimeMillis()
                    - TimeUnit.DAYS.toMillis(keepReadItemsDays);
            int rowsDeleted = rssRepository.pruneOldReadItems(cutoffMillis);
            if (rowsDeleted > 0) {
                provider.get(RssChangeNotifier.class).itemsPrunedAuto(null);
            }
        } catch (Throwable throwable) {
            provider.get(ILogger.class)
                    .e(TAG, getApplicationContext()
                                    .getString(R.string.error_prune_old_items_failed),
                            throwable);
        }
    }
}
