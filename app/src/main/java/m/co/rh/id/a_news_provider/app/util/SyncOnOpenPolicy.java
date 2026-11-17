package m.co.rh.id.a_news_provider.app.util;

import java.util.concurrent.TimeUnit;

/**
 * Pure-java decision policy for the "Sync when app opens" debounce gate
 * (issue #50). Contains no android imports and reads no clock: every
 * time-dependent input ({@code nowMillis}) is passed in as a plain {@code long},
 * so the whole policy runs on the JVM for unit tests - the same pure-decision
 * precedent as {@link SyncBackoffPolicy}, {@link NotificationPermissionPolicy}
 * and {@link OpenLinkPolicy}, whose callers (HomePage) stay thin shells around a
 * fully tested decision.
 * <p>
 * <b>Why a debounce at all.</b> "Sync on open" without a gate would fire a sync
 * request on every resume of the home page (screen on/off, app switches,
 * returning from the detail page all resume the activity), turning the feature
 * into near-continuous syncing. The 15-minute window puts the auto-trigger in
 * the same cadence family as the rest of issue #50 (the per-feed backoff base
 * is also 15 minutes): fresh enough that opening the app twice a day always
 * shows new content, yet bounded so heavy app-switchers generate at most one
 * auto-request per quarter hour. The gate is an UPPER bound on frequency, not a
 * scheduler: no wakeup is ever armed, only requests are suppressed - the
 * periodic background sync keeps its own hour-grained schedule untouched.
 * <p>
 * <b>Why "last request" and not "last successful sync".</b> The stamp is
 * written by SyncRssCmd.execute() at REQUEST time (even when WorkManager's KEEP
 * policy dedupes the enqueue - a documented trade-off). Using the request stamp
 * keeps the gate a pure in-memory/pref read with no dependency on sync outcomes,
 * and a deduped request still means a sync is imminent or running, so treating
 * it as "content was refreshed" is exactly the intent of the window.
 * <p>
 * Boundary semantics are strict: {@code nowMillis - lastRequestMillis >=
 * SYNC_ON_OPEN_DEBOUNCE_MILLIS} fires the sync, a request strictly fresher than
 * the window is suppressed - the window is a quiet period, not a scheduled
 * instant (mirrors the boundary of {@link SyncBackoffPolicy#shouldSkip}).
 */
public final class SyncOnOpenPolicy {

    /**
     * Minimum age of the last sync request before an app open may enqueue
     * another automatic sync: 15 minutes (see the class javadoc for the
     * cadence rationale).
     */
    public static final long SYNC_ON_OPEN_DEBOUNCE_MILLIS = TimeUnit.MINUTES.toMillis(15);

    private SyncOnOpenPolicy() {
    }

    /**
     * Decides whether returning to the home page should enqueue the manual
     * sync. Pure - the caller passes the decision time and reads the pref
     * values; this class never touches storage.
     *
     * @param enabled           the "Sync when app opens" setting
     * @param lastRequestMillis the last sync request stamp (epoch millis,
     *                          0 = never requested, which always lets the sync
     *                          through so a fresh install syncs on first open)
     * @param nowMillis         the decision time in epoch millis
     * @return true when the app-open sync must be enqueued
     */
    public static boolean shouldSyncOnOpen(boolean enabled, long lastRequestMillis,
                                           long nowMillis) {
        if (!enabled) {
            return false;
        }
        return nowMillis - lastRequestMillis >= SYNC_ON_OPEN_DEBOUNCE_MILLIS;
    }
}
