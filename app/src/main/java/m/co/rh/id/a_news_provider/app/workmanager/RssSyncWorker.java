package m.co.rh.id.a_news_provider.app.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.RequestFuture;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.repository.PermanentRedirectGuard;
import m.co.rh.id.a_news_provider.app.provider.component.repository.RssRepository;
import m.co.rh.id.a_news_provider.app.util.SyncBackoffPolicy;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.provider.component.dao.ArticleContentDao;
import m.co.rh.id.a_news_provider.base.provider.component.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.base.model.SyncResult;
import m.co.rh.id.a_news_provider.component.network.RssRequest;
import m.co.rh.id.a_news_provider.component.network.RssRequestFactory;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Syncs every non-paused, non-backing-off RSS channel: enqueues one Volley
 * request per channel, awaits the futures sequentially, persists each parsed
 * model and records the per-channel sync outcome (issue #50) in the channel
 * row - the attempt stamp before the fetch, the success reset or the failure
 * state afterwards. At the end of the run the outcomes are summarized into a
 * {@link SyncResult} and emitted via RssChangeNotifier so the UI can surface
 * failures instead of the former unconditional "Feed sync success" toast. The
 * worker still returns {@link Result#success()} carrying the synced channel
 * ids + sync start millis, so the chained notification/notifier workers keep
 * working unchanged.
 * <p>
 * The per-feed backoff (issue #50): a channel whose persisted
 * failure counter puts it inside its {@link SyncBackoffPolicy} window is
 * SKIPPED before the request is even enqueued (and before the attempt stamp -
 * see the enqueue loop), so a chronically failing feed stops hammering its
 * server on every sync run while staying due again once the quiet period
 * elapses. When a failure response carries a Retry-After header whose hint
 * exceeds what the exponential ladder will produce, the hint is encoded into
 * the persisted counter at failure time (see
 * {@link #applyRetryAfterEncoding(RssDao, RssChannel, SyncResult.FailedChannel, long)})
 * - after which the skip decision needs no header state at all. The skipped
 * channels flow into {@link SyncResult#getSkipped()} so the summary shows the
 * real attempt denominator.
 * <p>
 * Targeted per-channel run (issue #42 feed-health dashboard): when the input
 * carries {@link ConstantsKey#KEY_LONG_CHANNEL_ID} (set by SyncChannelCmd for
 * the dashboard's "Sync now" button), the loaded channel list is filtered to
 * that one id and the backoff skip is BYPASSED for the run - an explicit user
 * retry is one deliberate request (re-anchoring the quiet window via the
 * attempt stamp below), not the sync schedule hammering a struggling server.
 * Paused channels stay skipped even on a targeted run: the user silenced that
 * feed and a dashboard button must not override the choice. Everything
 * downstream (await loop, per-channel bookkeeping, Retry-After encoding,
 * summary emission, output data) is unchanged.
 * <p>
 * Cancellation is deliberately not failure: when the await loop is interrupted
 * or the worker is stopped, no failure state is written for the current or
 * remaining channels (WorkManager tore the run down - writing failures would
 * punish the feed for our own cancellation and wrongly trigger its backoff),
 * the loop just ends and the run completes with whatever was persisted so far.
 * <p>
 * The permanent-redirect rewrite (issue #50): when a fetch ends
 * on a 301/308 hop at a different address, the stored feed url is updated to
 * that final address in the same transaction as the ordinary sync write
 * (see {@link PermanentRedirectGuard#applyGuard(RssChannel, RssModel)}) -
 * the network layer (RedirectRecordingHurlStack + RssRequest) only transports
 * the outcome on the parsed model, the SHARED guard owns the decision so the
 * sync and add persist call sites can never drift apart on the
 * colliding-with-another-feed check. Temporary final hops (302/303/307) are
 * never rewritten: the server may point elsewhere again on the next request.
 */
public class RssSyncWorker extends Worker {
    private static final String TAG = RssSyncWorker.class.getName();
    // column-width budget of rss_channel.last_error / SyncResult error message
    // (issue #50): server HTML error bodies must not bloat the row
    private static final int ERROR_MESSAGE_MAX_LENGTH = 200;

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
        PermanentRedirectGuard permanentRedirectGuard = provider.get(PermanentRedirectGuard.class);
        List<RssChannel> rssChannelList = rssDao.loadAllRssChannel();
        // targeted per-channel run (issue #42): SyncChannelCmd puts the tapped
        // feed's id in the input; filter the loaded list to that ONE channel
        // (default 0 = app-wide run, list unchanged). A targeted run reuses the
        // whole machinery below - same bookkeeping, same summary, same output.
        long targetChannelId = getInputData().getLong(ConstantsKey.KEY_LONG_CHANNEL_ID, 0);
        if (targetChannelId > 0) {
            List<RssChannel> targetedList = new ArrayList<>(1);
            for (RssChannel rssChannel : rssChannelList) {
                if (rssChannel.id != null && rssChannel.id == targetChannelId) {
                    targetedList.add(rssChannel);
                }
            }
            rssChannelList = targetedList;
        }
        // channel + future are PAIRED so the await loop can attribute each
        // result (and each failure) back to its channel - the bare future list
        // of the pre-#50 code made per-channel failure bookkeeping impossible
        List<ChannelRequest> channelRequestList = new ArrayList<>();
        int skippedCount = 0;
        for (RssChannel rssChannel : rssChannelList) {
            if (rssChannel.isPaused) {
                continue;
            }
            // backoff skip (issue #50), BEFORE the attempt stamp: a
            // skipped channel must NOT refresh last_sync_attempt_date_time -
            // the skip window is measured FROM the last attempt, so refreshing
            // the stamp on every skipped run would self-extend the window
            // forever and a failing feed would never become due again. The
            // decision is pure (SyncBackoffPolicy) over the state loaded at
            // sync start; nothing here writes to the channel row.
            // BYPASSED on a targeted run (issue #42): the request came from an
            // explicit user tap on the feed-health dashboard ("Sync now"), not
            // from the schedule - one deliberate retry is exactly what the
            // backoff exists to allow, and the attempt stamp below re-anchors
            // the quiet window from this very request. Paused channels above
            // are still skipped: silence is a user decision, not a backoff.
            if (targetChannelId <= 0
                    && SyncBackoffPolicy.shouldSkip(rssChannel, syncStartMillis)) {
                skippedCount++;
                continue;
            }
            RequestFuture<RssModel> requestFuture = RequestFuture.newFuture();
            RssRequest rssRequest = rssRequestFactory.
                    newRssRequest(Request.Method.GET, rssChannel.url, requestFuture, requestFuture);
            // stamp the attempt BEFORE the request is enqueued: only channels
            // that are actually attempted get the stamp (the backoff skip
            // above already continue'd), keeping the skip window anchored to
            // the last real fetch instead of the last bookkeeping run
            rssDao.markRssChannelSyncAttempted(rssChannel.id, new Date());
            requestQueue.add(rssRequest);
            channelRequestList.add(new ChannelRequest(rssChannel, requestFuture));
        }

        List<RssModel> rssModels = new ArrayList<>();
        List<SyncResult.FailedChannel> failedChannels = new ArrayList<>();
        for (ChannelRequest channelRequest : channelRequestList) {
            RssChannel rssChannel = channelRequest.rssChannel;
            try {
                RssModel rssModel = channelRequest.requestFuture.get(15, TimeUnit.SECONDS);
                // permanent-redirect duplicate guard (issue #50), BEFORE
                // persist so the url change lands in persist's single upsert
                // transaction instead of a second write after it; the shared
                // guard is the same decision the add path uses, so the two
                // persist call sites can never drift apart
                permanentRedirectGuard.applyGuard(rssChannel, rssModel);
                RssModel persisted = rssRepository.persist(rssModel);
                // success reset deliberately AFTER persist(): the upsert's
                // copyPreservedFields re-writes the stored health values, so
                // this targeted reset must run last to stay the authoritative
                // writer of the sync-health columns (issue #50)
                rssDao.markRssChannelSyncSuccess(rssChannel.id, new Date());
                rssModels.add(persisted);
            } catch (Throwable throwable) {
                // cancellation hardening (issue #50): an interrupted
                // await means WorkManager STOPPED this worker (cancel or
                // constraint loss), and a stop can also surface as an ordinary
                // failure of the in-flight request. Recording failure state
                // for THIS or any REMAINING channel would punish feeds for our
                // own cancellation - the counter would rise and wrongly start
                // a backoff - so the loop ends here, attempt stamps stay
                // as-is, and whatever was persisted before the stop stands.
                // Logged at debug like an ordinary failure; the run continues
                // with the summary/aging/prune passes so the output contract
                // of doWork() is unchanged.
                if (throwable instanceof InterruptedException || isStopped()) {
                    provider.get(ILogger.class)
                            .d(TAG, "Sync worker stopped: remaining channels not awaited",
                                    throwable);
                    break;
                }
                SyncResult.FailedChannel failedChannel =
                        buildFailedChannel(rssChannel, throwable);
                // the failure bookkeeping is wrapped in its own try-catch so a
                // DB error can never escape the catch block and fail the whole
                // worker (hardening): a failed write must
                // downgrade to a log line, the run summary still includes the
                // channel, and the next sync simply re-attempts it (no state
                // was persisted, so no backoff was recorded either)
                try {
                    rssDao.markRssChannelSyncFailure(failedChannel.getChannelId(),
                            failedChannel.getHttpStatus(),
                            failedChannel.getErrorMessage(), new Date());
                    applyRetryAfterEncoding(rssDao, rssChannel, failedChannel,
                            System.currentTimeMillis());
                } catch (Throwable dbError) {
                    provider.get(ILogger.class)
                            .e(TAG, getApplicationContext()
                                            .getString(R.string.error_feed_sync_failed),
                                    dbError);
                }
                failedChannels.add(failedChannel);
                provider.get(ILogger.class)
                        .d(TAG, "Failed to sync some RSS", throwable);
            }
        }

        int size = rssModels.size();
        long[] channelIds = new long[size];
        for (int i = 0; i < size; i++) {
            channelIds[i] = rssModels.get(i).getRssChannel().id;
        }

        autoMarkOldItemsRead(provider, rssRepository);
        pruneOldItems(provider, rssRepository);

        // per-run summary for the UI (issue #50). The backoff fills the skipped
        // bucket with the real backoff-skip count of the enqueue loop; note the
        // buckets are not guaranteed to sum to total after a mid-run
        // cancellation (stopped worker) - the unawaited remainder is simply
        // unaccounted, the run is over anyway.
        SyncResult syncResult = new SyncResult(channelRequestList.size(),
                rssModels.size(), failedChannels.size(), skippedCount,
                syncStartMillis, failedChannels);
        // total == 0 (all channels paused / empty database) emits NOTHING:
        // "Synced 0 of 0" is noise, not information
        if (syncResult.getTotal() > 0) {
            provider.get(RssChangeNotifier.class).syncSummary(syncResult);
        }

        Data outputData = new Data.Builder()
                .putLongArray(ConstantsKey.KEY_LONG_CHANNEL_IDS, channelIds)
                .putLong(ConstantsKey.KEY_LONG_SYNC_START_MILLIS, syncStartMillis)
                .build();
        return Result.success(outputData);
    }

    /**
     * Builds the per-channel failure record for the sync summary (issue #50).
     * The HTTP status is extracted GENERICALLY from the Volley
     * {@code networkResponse} instead of enumerating error subclasses: a 429
     * arrives as ClientError, 401/403 as AuthFailureError, 5xx as ServerError -
     * ALL of them carry a networkResponse - while TimeoutError/NetworkError/
     * NoConnectionError carry none and ParseError may carry one. One
     * null-checked field read covers every case, so a new Volley error type
     * can never fall through the cracks. The same read picks up the RAW
     * Retry-After header when the failure response carried one (Volley's
     * header map is case-insensitive); the worker hands both to
     * {@link SyncBackoffPolicy#retryAfterOverrideMillis(Integer, String, long)}
     * via {@link #applyRetryAfterEncoding} while the response is still in hand.
     */
    private SyncResult.FailedChannel buildFailedChannel(RssChannel rssChannel,
                                                        Throwable throwable) {
        Integer httpStatus = null;
        String retryAfterHeader = null;
        if (throwable instanceof VolleyError) {
            NetworkResponse networkResponse = ((VolleyError) throwable).networkResponse;
            if (networkResponse != null) {
                httpStatus = networkResponse.statusCode;
                if (networkResponse.headers != null) {
                    retryAfterHeader = networkResponse.headers.get("Retry-After");
                }
            }
        }
        return new SyncResult.FailedChannel(rssChannel.id, rssChannel.feedName,
                rssChannel.url, httpStatus, truncateErrorMessage(throwable),
                retryAfterHeader);
    }

    /**
     * Retry-After counter-encoding in the failure path (issue #50).
     * A Retry-After hint from the just-failed response is transient - only
     * this method still holds it - so instead of persisting the header itself,
     * the hint is folded into the channel's already-persisted failure counter:
     * {@link SyncBackoffPolicy#extraIncrementsForRetryAfter(int, long)} answers
     * how many EXTRA SQL-side increments
     * ({@link RssDao#incrementRssChannelSyncFailure(long)}) must follow the
     * ordinary single increment of
     * {@link RssDao#markRssChannelSyncFailure(long, Integer, String, Date)}
     * (which already ran) for the counter's exponential window to cover the
     * hint. After this, every future skip decision is pure persisted-state
     * logic - the header never has to outlive the response.
     * <p>
     * Design intent (see SyncBackoffPolicy's class javadoc for the full
     * rationale): the hint can only ever LENGTHEN the quiet period, the
     * encoded schedule is the tightest ladder rung satisfying the server, and
     * the observability columns (last_http_status, last_error) still show the
     * real failure because the extra writes touch ONLY the counter. Runs
     * inside the caller's DB-write try-catch: an encoding failure downgrades
     * to a log line like the ordinary failure write, and the next sync merely
     * re-attempts the channel without the hint applied.
     *
     * @param rssDao        the DAO used for the extra counter increments
     * @param rssChannel    the failed channel, carrying its pre-failure counter
     *                      value as loaded at sync start
     * @param failedChannel the built failure record with the raw HTTP status
     *                      and Retry-After header of this failure
     * @param failureMillis the failure completion time in epoch millis (used
     *                      to resolve an HTTP-date hint into a duration)
     */
    private void applyRetryAfterEncoding(RssDao rssDao, RssChannel rssChannel,
                                         SyncResult.FailedChannel failedChannel,
                                         long failureMillis) {
        long retryAfterOverride = SyncBackoffPolicy.retryAfterOverrideMillis(
                failedChannel.getHttpStatus(),
                failedChannel.getRetryAfterHeader(), failureMillis);
        int extraIncrements = SyncBackoffPolicy.extraIncrementsForRetryAfter(
                rssChannel.consecutiveFailures, retryAfterOverride);
        for (int i = 0; i < extraIncrements; i++) {
            rssDao.incrementRssChannelSyncFailure(rssChannel.id);
        }
    }

    /**
     * The throwable message truncated to {@link #ERROR_MESSAGE_MAX_LENGTH}
     * chars (the budget of rss_channel.last_error and the SyncResult failure
     * detail, issue #50). When the throwable has no message at all (e.g. a
     * bare TimeoutError), the exception class name is used instead -
     * "TimeoutError" tells the reader more than an empty string.
     */
    private static String truncateErrorMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isEmpty()) {
            message = throwable.getClass().getSimpleName();
        }
        if (message.length() > ERROR_MESSAGE_MAX_LENGTH) {
            message = message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
        }
        return message;
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
     * <p>
     * At the END of the prune step, the article-content cache is also swept for
     * orphans (issue #28): rows whose link no longer exists on any rss_item can
     * never be opened again. The sweep runs on EVERY sync, regardless of the
     * keep-read-items setting - item rows can also disappear through channel
     * deletion (whose own cleanup is catch-all-logged too), so the sweep doubles
     * as a self-heal for anything that path missed. Deleting cached content never
     * changes unread counts; nothing needs reloading - a fetched article is only
     * visible on the detail page that requested it.
     *
     * @param provider      the app provider
     * @param rssRepository the repository used to run the prune update
     */
    private void pruneOldItems(Provider provider, RssRepository rssRepository) {
        try {
            AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
            int keepReadItemsDays = appSharedPreferences.getKeepReadItemsDays();
            if (keepReadItemsDays > 0) {
                long cutoffMillis = System.currentTimeMillis()
                        - TimeUnit.DAYS.toMillis(keepReadItemsDays);
                int rowsDeleted = rssRepository.pruneOldReadItems(cutoffMillis);
                if (rowsDeleted > 0) {
                    provider.get(RssChangeNotifier.class).itemsPrunedAuto(null);
                }
            }
            // article-content orphan sweep: last step of the prune, never fails the sync
            provider.get(ArticleContentDao.class).deleteOrphanArticleContent();
        } catch (Throwable throwable) {
            provider.get(ILogger.class)
                    .e(TAG, getApplicationContext()
                                    .getString(R.string.error_prune_old_items_failed),
                            throwable);
        }
    }

    /**
     * Pairs a channel with its in-flight request future: the enqueue loop and
     * the await loop stay structurally separate (all requests enqueued first,
     * then awaited sequentially) but each awaited result needs its channel
     * back for the per-channel success/failure bookkeeping (issue #50).
     */
    private static final class ChannelRequest {
        private final RssChannel rssChannel;
        private final RequestFuture<RssModel> requestFuture;

        private ChannelRequest(RssChannel rssChannel, RequestFuture<RssModel> requestFuture) {
            this.rssChannel = rssChannel;
            this.requestFuture = requestFuture;
        }
    }
}
