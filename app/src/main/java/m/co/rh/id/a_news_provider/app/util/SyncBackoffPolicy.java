package m.co.rh.id.a_news_provider.app.util;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;

/**
 * Pure-java decision policy for the per-feed sync backoff (issue #50).
 * Contains no android imports and reads no clock: every time-dependent input
 * ({@code nowMillis}) is passed in as a plain {@code long}, so the whole policy
 * runs on the JVM for unit tests - the same pure-decision precedent as
 * {@link NotificationPermissionPolicy} and {@link OpenLinkPolicy}, whose
 * callers (RssSyncWorker) stay thin shells around a fully tested decision.
 * <p>
 * <b>State model - why only persisted columns.</b> The skip decision answers
 * "should this sync leave this channel alone?" and runs BEFORE any network
 * request, using ONLY what {@code rss_channel} persisted from previous syncs:
 * {@code consecutive_failures} (SQL-side increment, reset on success) and
 * {@code last_sync_attempt_date_time} (stamped before each fetch, NOT stamped
 * on skipped runs - refreshing it would self-extend the skip window forever
 * and a failing feed would never become due again). The exponential window
 * {@link #effectiveBackoffWindow(RssChannel)} is therefore a pure function of
 * the counter, and {@link #shouldSkip(RssChannel, long)} needs nothing else.
 * <p>
 * <b>The Retry-After design decision - encoding the server hint into the
 * counter.</b> A failure response may carry a {@code Retry-After} header
 * telling the server's own idea of when to come back. That header is
 * transient (it exists only on the failed NetworkResponse) and is deliberately
 * NOT persisted: it belongs to one response, and a column for it would need a
 * new schema surface plus reset/clear rules on success. Instead, the WORKER
 * folds the hint into the already-persisted counter at failure time
 * ("counter-encoding"): it parses the header via
 * {@link #retryAfterOverrideMillis(Integer, String, long)}, compares it with
 * the exponential window the incremented counter will produce, and - only when
 * the hint EXCEEDS that window - issues extra
 * {@code incrementRssChannelSyncFailure} writes until the counter's window
 * covers the hint (see {@link #extraIncrementsForRetryAfter(int, long)}).
 * After that, every later skip decision is trivially pure: the counter alone
 * encodes "at least this long between attempts". Two consequences are
 * accepted and intentional:
 * <ul>
 * <li>the counter stops meaning "literal consecutive HTTP failures" and means
 * "effective skip rungs" - the observability columns (last_http_status,
 * last_error) still show the REAL failure, so the counter is a schedule, not a
 * judge;</li>
 * <li>a server hint can only ever LENGTHEN the quiet period (max is the same
 * 24h cap), never shorten it - a "Retry-After: 1" on an already-backing-off
 * channel is ignored, because the hint is advice about load, and the
 * exponential window exists precisely because repeated failures must quiet
 * down.</li>
 * </ul>
 * <p>
 * <b>Why exponential.</b> Consecutive failures mean either a dead server or an
 * aggressive rate limiter; retrying every sync hammers the former and turns a
 * 429 into a ban for the latter. Doubling gives a fast first retry (15 min -
 * a transient blip is invisible at the user cadence) while scaling the quiet
 * period with how LONG the failure persisted. The 15-minute base matches the
 * sync-trigger cadence family: the sync-on-open path is user-paced (an app
 * open or a pull-to-refresh fires a sync run) and periodic sync is
 * hour-grained, so 15 minutes is the smallest rung that still gives a
 * struggling server breathing room while never hiding a healthy-again feed for
 * more than one periodic run.
 * <p>
 * <b>Why the 24h cap.</b> Without a cap, a long-failing feed's window would
 * grow into weeks and the counter-encoding would need an unbounded number of
 * extra increments. Capping at 24h guarantees every channel is still attempted
 * about once a day (a recovered feed is re-discovered within a day no matter
 * how large its counter grew) and bounds the encoding arithmetic to at most 8
 * rungs (15 min doubled 7 times already exceeds 24h) - comfortably inside the
 * small bounded extra-increment budget the worker allows.
 * <p>
 * Boundary semantics are strict: {@code nowMillis - lastAttempt < window}
 * means a channel is skipped, a channel exactly at (or past) its window is
 * attempted again - the window is a quiet period, not a scheduled instant.
 */
public final class SyncBackoffPolicy {

    /**
     * First rung of the backoff ladder: the quiet period after ONE consecutive
     * failure. 15 minutes (see class javadoc for the cadence rationale).
     */
    public static final long BACKOFF_BASE_MILLIS = TimeUnit.MINUTES.toMillis(15);

    /**
     * Ceiling of every window - exponential rungs and Retry-After hints alike
     * (see class javadoc for the once-a-day rationale).
     */
    public static final long BACKOFF_MAX_MILLIS = TimeUnit.HOURS.toMillis(24);

    /**
     * Fixed doubling factor of the ladder: failure n sits at
     * {@code BASE << (n - 1)}, i.e. 1x, 2x, 4x ... base.
     */
    private static final long BACKOFF_MULTIPLIER = 2L;

    /**
     * Statuses whose Retry-After header is honored as a window override. 429
     * (Too Many Requests) and 503 (Service Unavailable) are the RFC-documented
     * carriers of Retry-After (RFC 6585 / RFC 7231); 403 is added per issue #50
     * because several CDNs answer hard rate limiting with a bare 403 plus the
     * header. Every other status keeps the pure exponential window: a 500's
     * "come back later" header would describe an outage, not a quota.
     */
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVICE_UNAVAILABLE = 503;
    private static final int HTTP_FORBIDDEN = 403;

    private SyncBackoffPolicy() {
    }

    /**
     * Decides whether the sync should leave this channel alone this run: true
     * only when the channel has failed at least once, carries a persisted
     * attempt stamp, and that stamp is still inside the channel's effective
     * backoff window. Pure - the caller passes the decision time.
     * <p>
     * Uses ONLY persisted state (see the class javadoc): a Retry-After hint
     * from the current sync has already been encoded into the failure counter
     * by the worker at failure time, so there is nothing header-shaped to read
     * here. A null attempt stamp with a positive counter (should not happen -
     * the stamp is written before every attempt) is deliberately permissive:
     * without a stamp the elapsed time is unknowable, and skipping forever on
     * unknowable state would be the worse failure mode.
     *
     * @param channel   the channel as loaded at sync start
     * @param nowMillis the decision time in epoch millis
     * @return true when the channel must be skipped by this sync
     */
    public static boolean shouldSkip(RssChannel channel, long nowMillis) {
        if (channel == null || channel.consecutiveFailures <= 0) {
            return false;
        }
        if (channel.lastSyncAttemptDateTime == null) {
            return false;
        }
        long elapsedMillis = nowMillis - channel.lastSyncAttemptDateTime.getTime();
        return elapsedMillis < effectiveBackoffWindow(channel);
    }

    /**
     * The exponential backoff window derived from the persisted failure
     * counter: {@code min(BACKOFF_BASE_MILLIS << (failures - 1),
     * BACKOFF_MAX_MILLIS)}. One failure means one base window, two mean double,
     * three mean quadruple and so on until the 24h cap (reached from the 8th
     * failure on). Zero failures means zero window - a healthy channel is never
     * skipped.
     * <p>
     * The exponent is guarded by doubling toward the cap instead of a raw
     * {@code <<} shift: {@code consecutiveFailures} is a plain int column and a
     * feed failing every sync for months can carry a counter in the hundreds,
     * where {@code BASE << (failures - 1)} silently overflows a long (wrap can
     * even produce a small positive). The doubling stops within ~8 iterations
     * regardless of the counter, returning the cap - overflow-proof by
     * construction because the loop only ever doubles a value already proven
     * below the cap.
     *
     * @param channel the channel as loaded at sync start
     * @return the quiet-period window in millis, 0 when the channel has no failures
     */
    public static long effectiveBackoffWindow(RssChannel channel) {
        if (channel == null) {
            return 0;
        }
        return windowForCounter(channel.consecutiveFailures);
    }

    /**
     * The effective skip window of a channel combining its exponential window
     * with a Retry-After override when the given failure carries one - the
     * single definition of "how long this failure says to stay quiet"
     * (max of the two, each capped at {@link #BACKOFF_MAX_MILLIS}).
     * <p>
     * Note (issue #50): this combination is evaluated at FAILURE time
     * by the worker to decide whether the hint dominates the ladder and must be
     * encoded into the counter ({@link #extraIncrementsForRetryAfter(int, long)}).
     * The SKIP decision itself never calls this with a live header, because the
     * header is not persisted - {@link #shouldSkip(RssChannel, long)} evaluates
     * the persisted-only sibling {@link #effectiveBackoffWindow(RssChannel)}.
     * Keeping the max() here (and in tests) documents the one rule both paths
     * obey: the quiet period is whichever of server-hint and ladder is LONGER.
     *
     * @param channel          the channel as loaded at sync start
     * @param lastHttpStatus   the observed HTTP status of the failure, null when none
     * @param retryAfterHeader the raw Retry-After header of the failure, null when absent
     * @param nowMillis        the failure/decision time in epoch millis (used to
     *                         resolve an HTTP-date header into a duration)
     * @return the combined quiet-period window in millis
     */
    public static long effectiveWindowMillis(RssChannel channel, Integer lastHttpStatus,
                                             String retryAfterHeader, long nowMillis) {
        long exponentialWindow = effectiveBackoffWindow(channel);
        long retryAfterOverride = retryAfterOverrideMillis(lastHttpStatus,
                retryAfterHeader, nowMillis);
        return Math.max(exponentialWindow, retryAfterOverride);
    }

    /**
     * Parses the {@code Retry-After} header of a failure response into a window
     * override in millis, or 0 when no usable override exists. An override of 0
     * means "the exponential ladder alone decides" - callers must treat it as
     * no-op, never as "skip for zero millis".
     * <p>
     * The header is honored ONLY for {@value #HTTP_TOO_MANY_REQUESTS},
     * {@value #HTTP_SERVICE_UNAVAILABLE} and {@value #HTTP_FORBIDDEN} (see the
     * constant's javadoc for why these three); other statuses return 0 even
     * when a header is present. Parsing follows RFC 7231 &sect;7.1.3 in its two
     * allowed forms:
     * <ol>
     * <li><b>delta-seconds</b> - the trimmed header parsed as a long; a
     * negative number is malformed (delta-seconds is {@code 1*DIGIT}) and
     * yields 0;</li>
     * <li><b>HTTP-date</b> (fallback) - parsed with
     * {@link DateTimeFormatter#RFC_1123_DATE_TIME}, pinned to {@link Locale#US}
     * because the day/month names of that format are locale-sensitive and a
     * device in, say, Germany would otherwise fail to parse "Oct" (Okt) -
     * turning a valid server hint into silence; the parsed instant minus
     * {@code nowMillis} is the override, floored at 0 because a past date means
     * "retry now" which the next sync already does. Only the IMF-fixdate form
     * is supported: the two obsolete RFC 7231 spellings (RFC 850, asctime) are
     * effectively extinct on the wire and a failed parse simply falls back to
     * the exponential ladder.</li>
     * </ol>
     * Anything else (null status, absent/blank header, malformed value,
     * unparseable date) returns 0 - a hostile or broken header must never
     * become a crash or an unbounded skip. The result is clamped to
     * {@link #BACKOFF_MAX_MILLIS} so a "Retry-After: 999999999" cannot park a
     * feed for years; the cap keeps the once-a-day guarantee.
     * Pure - the caller passes the time an HTTP-date is resolved against.
     *
     * @param httpStatus       the observed HTTP status of the failure, null when none
     * @param retryAfterHeader the raw header value, null when absent
     * @param nowMillis        the failure time in epoch millis (HTTP-date resolution)
     * @return the override window in millis clamped to BACKOFF_MAX_MILLIS, 0 when none
     */
    public static long retryAfterOverrideMillis(Integer httpStatus, String retryAfterHeader,
                                                long nowMillis) {
        if (httpStatus == null || retryAfterHeader == null) {
            return 0;
        }
        if (httpStatus != HTTP_TOO_MANY_REQUESTS
                && httpStatus != HTTP_SERVICE_UNAVAILABLE
                && httpStatus != HTTP_FORBIDDEN) {
            return 0;
        }
        String trimmedHeader = retryAfterHeader.trim();
        if (trimmedHeader.isEmpty()) {
            return 0;
        }
        try {
            long deltaSeconds = Long.parseLong(trimmedHeader);
            if (deltaSeconds < 0) {
                // delta-seconds is 1*DIGIT; a minus sign is malformed, not a hint
                return 0;
            }
            return clampToMax(TimeUnit.SECONDS.toMillis(deltaSeconds));
        } catch (NumberFormatException notDeltaSeconds) {
            // fall through to the HTTP-date form
        }
        try {
            long retryAtMillis = OffsetDateTime.parse(trimmedHeader,
                    DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US))
                    .toInstant().toEpochMilli();
            long deltaMillis = retryAtMillis - nowMillis;
            if (deltaMillis < 0) {
                // past date = "retry now"; the next sync already does that
                return 0;
            }
            return clampToMax(deltaMillis);
        } catch (Throwable malformedDate) {
            return 0;
        }
    }

    /**
     * The counter-encoding arithmetic behind the Retry-After design (see the
     * class javadoc): how many ADDITIONAL SQL-side failure increments the
     * worker must write, AFTER the ordinary single increment of
     * {@code markRssChannelSyncFailure}, so the persisted counter's exponential
     * window covers the parsed Retry-After hint. The worker writes
     * {@code 1 + result} total increments and nothing else - the returned
     * count is bounded by construction (at most 7: from rung 8 on the ladder
     * sits at the 24h cap, which the clamped override can never exceed).
     * <p>
     * The reference point is the counter value AFTER the ordinary increment
     * ({@code consecutiveFailuresBeforeFailure + 1}): that is what the NEXT
     * sync's {@link #effectiveBackoffWindow(RssChannel)} will see. When that
     * window already covers the hint (a fresh failure's 15-minute rung covers
     * "Retry-After: 300", an already-backing-off channel's rung may dwarf the
     * hint), the result is 0 and the failure path costs exactly one write - the
     * hint must LENGTHEN, never shorten. The total target counter is the
     * smallest n whose window is at least the hint, so the encoded schedule is
     * the tightest ladder rung satisfying the server, not a loose overshoot.
     * Pure and overflow-proof: the override is clamped first, and
     * {@link #windowForCounter(int)} stops at the cap.
     *
     * @param consecutiveFailuresBeforeFailure the channel's failure counter as
     *                                         read at sync start, BEFORE the
     *                                         ordinary failure increment
     * @param retryAfterOverrideMillis         the parsed Retry-After override
     *                                         (as returned by
     *                                         {@link #retryAfterOverrideMillis(Integer, String, long)},
     *                                         already clamped; a defensive
     *                                         re-clamp costs nothing)
     * @return the number of extra incrementRssChannelSyncFailure writes to
     * perform, 0 when the ordinary increment already suffices
     */
    public static int extraIncrementsForRetryAfter(int consecutiveFailuresBeforeFailure,
                                                   long retryAfterOverrideMillis) {
        if (retryAfterOverrideMillis <= 0) {
            return 0;
        }
        long clampedOverride = clampToMax(retryAfterOverrideMillis);
        // saturating increment: an already-capped counter must not wrap
        // negative here (the window is capped anyway, so the exact value
        // above the cap is irrelevant - only that it stays positive)
        int counterAfterFailure = Math.min(consecutiveFailuresBeforeFailure,
                Integer.MAX_VALUE - 1) + 1;
        if (windowForCounter(counterAfterFailure) >= clampedOverride) {
            return 0;
        }
        int targetCounter = counterAfterFailure;
        while (windowForCounter(targetCounter) < clampedOverride) {
            targetCounter++;
        }
        return targetCounter - counterAfterFailure;
    }

    /**
     * The ladder rung of a given counter value:
     * {@code min(BACKOFF_BASE_MILLIS << (counter - 1), BACKOFF_MAX_MILLIS)},
     * computed by cap-guarded doubling (overflow rationale on
     * {@link #effectiveBackoffWindow(RssChannel)}). Shared by the persisted-only
     * window, the combined window and the encoding arithmetic so the ladder
     * exists exactly once. Zero and negative counters yield 0.
     *
     * @param counter the failure-counter value
     * @return the window in millis for that counter, capped at BACKOFF_MAX_MILLIS
     */
    private static long windowForCounter(int counter) {
        if (counter <= 0) {
            return 0;
        }
        long window = BACKOFF_BASE_MILLIS;
        for (int i = 1; i < counter; i++) {
            // the value entering this line is proven < BACKOFF_MAX_MILLIS by
            // the check below (or is the base), so doubling cannot overflow
            window *= BACKOFF_MULTIPLIER;
            if (window >= BACKOFF_MAX_MILLIS) {
                return BACKOFF_MAX_MILLIS;
            }
        }
        return window;
    }

    /**
     * Clamps a window to {@link #BACKOFF_MAX_MILLIS} - the once-a-day ceiling
     * every window (ladder rung, Retry-After hint, encoded override) shares.
     *
     * @param windowMillis the raw window in millis
     * @return the window capped at BACKOFF_MAX_MILLIS
     */
    private static long clampToMax(long windowMillis) {
        return Math.min(windowMillis, BACKOFF_MAX_MILLIS);
    }
}
