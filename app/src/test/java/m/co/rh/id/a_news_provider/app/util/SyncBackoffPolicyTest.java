package m.co.rh.id.a_news_provider.app.util;

import org.junit.Test;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for SyncBackoffPolicy (issue #50) covering: the skip
 * decision (no failures / fresh attempt / elapsed window / null attempt /
 * capped window), the exponential rung progression up to and safely beyond
 * the 24h cap, the Retry-After parsing rules (delta-seconds on 429/503/403
 * only, malformed values, HTTP-date delta, 24h clamp) and the
 * counter-encoding arithmetic that folds a Retry-After hint into the failure
 * counter.
 */
public class SyncBackoffPolicyTest {

    private static final long BASE_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final long MAX_MILLIS = TimeUnit.HOURS.toMillis(24);
    // arbitrary fixed "now" - the policy is pure, so every test pins its own time
    private static final long NOW = 1_700_000_000_000L;

    private static RssChannel channelWith(int consecutiveFailures, Date lastAttempt) {
        RssChannel channel = new RssChannel();
        channel.consecutiveFailures = consecutiveFailures;
        channel.lastSyncAttemptDateTime = lastAttempt;
        return channel;
    }

    private static Date millisBeforeNow(long millis) {
        return new Date(NOW - millis);
    }

    // ----- shouldSkip -----

    @Test
    public void testShouldSkipFalseWithoutFailures() {
        // a healthy channel is never skipped, however recent its sync
        assertFalse(SyncBackoffPolicy.shouldSkip(channelWith(0, millisBeforeNow(0)), NOW));
        assertFalse(SyncBackoffPolicy.shouldSkip(channelWith(0, millisBeforeNow(BASE_MILLIS)), NOW));
    }

    @Test
    public void testShouldSkipTrueWithinFreshWindow() {
        // one failure: inside the 15-minute base window
        assertTrue(SyncBackoffPolicy.shouldSkip(
                channelWith(1, millisBeforeNow(TimeUnit.MINUTES.toMillis(14))), NOW));
        // two failures: 30-minute window, 29 minutes elapsed
        assertTrue(SyncBackoffPolicy.shouldSkip(
                channelWith(2, millisBeforeNow(TimeUnit.MINUTES.toMillis(29))), NOW));
        // capped channel (8+ failures = 24h): 23 hours elapsed is still inside
        assertTrue(SyncBackoffPolicy.shouldSkip(
                channelWith(8, millisBeforeNow(TimeUnit.HOURS.toMillis(23))), NOW));
    }

    @Test
    public void testShouldSkipFalseOnceWindowElapsed() {
        // one failure, 16 minutes elapsed > 15-minute window
        assertFalse(SyncBackoffPolicy.shouldSkip(
                channelWith(1, millisBeforeNow(TimeUnit.MINUTES.toMillis(16))), NOW));
        // boundary is strict (<): exactly at the window the channel is due
        assertFalse(SyncBackoffPolicy.shouldSkip(
                channelWith(1, millisBeforeNow(BASE_MILLIS)), NOW));
        // capped channel past its 24h window
        assertFalse(SyncBackoffPolicy.shouldSkip(
                channelWith(8, millisBeforeNow(TimeUnit.HOURS.toMillis(25))), NOW));
    }

    @Test
    public void testShouldSkipFalseWithNullAttemptStamp() {
        // positive counter but no stamp: elapsed time is unknowable, the
        // policy is deliberately permissive (see shouldSkip javadoc)
        assertFalse(SyncBackoffPolicy.shouldSkip(channelWith(3, null), NOW));
    }

    // ----- effectiveBackoffWindow (ladder progression + cap) -----

    @Test
    public void testWindowZeroForNoFailures() {
        assertEquals(0L, SyncBackoffPolicy.effectiveBackoffWindow(channelWith(0, null)));
    }

    @Test
    public void testWindowProgressionDoublesEachFailure() {
        // 1 failure -> 1x base, 2 -> 2x, 3 -> 4x, 4 -> 8x, 5 -> 16x,
        // 6 -> 32x, 7 -> 64x (16h, still below the cap)
        assertEquals(BASE_MILLIS,
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(1, null)));
        assertEquals(TimeUnit.MINUTES.toMillis(30),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(2, null)));
        assertEquals(TimeUnit.MINUTES.toMillis(60),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(3, null)));
        assertEquals(TimeUnit.HOURS.toMillis(2),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(4, null)));
        assertEquals(TimeUnit.HOURS.toMillis(4),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(5, null)));
        assertEquals(TimeUnit.HOURS.toMillis(8),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(6, null)));
        assertEquals(TimeUnit.HOURS.toMillis(16),
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(7, null)));
    }

    @Test
    public void testWindowCapsAt24h() {
        // 8th rung (15m << 7 = 32h) exceeds the cap; everything above stays there
        assertEquals(MAX_MILLIS, SyncBackoffPolicy.effectiveBackoffWindow(channelWith(8, null)));
        assertEquals(MAX_MILLIS, SyncBackoffPolicy.effectiveBackoffWindow(channelWith(9, null)));
        assertEquals(MAX_MILLIS, SyncBackoffPolicy.effectiveBackoffWindow(channelWith(100, null)));
    }

    @Test
    public void testWindowOverflowProofForHugeCounter() {
        // a feed failing every sync for months can carry a big counter; the
        // cap-guarded doubling must never overflow the long shift
        assertEquals(MAX_MILLIS,
                SyncBackoffPolicy.effectiveBackoffWindow(channelWith(Integer.MAX_VALUE, null)));
    }

    // ----- retryAfterOverrideMillis -----

    @Test
    public void testOverrideOnlyOnThrottlingStatuses() {
        // honored on 429 / 503 / 403 (issue #50)
        assertEquals(TimeUnit.MINUTES.toMillis(2),
                SyncBackoffPolicy.retryAfterOverrideMillis(429, "120", NOW));
        assertEquals(TimeUnit.MINUTES.toMillis(2),
                SyncBackoffPolicy.retryAfterOverrideMillis(503, "120", NOW));
        assertEquals(TimeUnit.MINUTES.toMillis(2),
                SyncBackoffPolicy.retryAfterOverrideMillis(403, "120", NOW));
        // NOT on other statuses, even with an identical header
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(500, "120", NOW));
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(404, "120", NOW));
        // and never without a status (timeout / network error failures)
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(null, "120", NOW));
    }

    @Test
    public void testOverrideZeroForMissingOrBlankHeader() {
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, null, NOW));
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "", NOW));
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "   ", NOW));
    }

    @Test
    public void testOverrideDeltaSeconds() {
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "0", NOW));
        assertEquals(TimeUnit.HOURS.toMillis(1),
                SyncBackoffPolicy.retryAfterOverrideMillis(429, "3600", NOW));
        // surrounding whitespace is trimmed before parsing
        assertEquals(TimeUnit.HOURS.toMillis(1),
                SyncBackoffPolicy.retryAfterOverrideMillis(503, " 3600 ", NOW));
    }

    @Test
    public void testOverrideMalformedValueIsZero() {
        // not a number and not a date
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "abc", NOW));
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "12x", NOW));
        // negative delta-seconds is malformed (1*DIGIT per RFC 7231)
        assertEquals(0L, SyncBackoffPolicy.retryAfterOverrideMillis(429, "-5", NOW));
        // overflows a long: not delta-seconds, not a date either
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(429, "99999999999999999999", NOW));
    }

    @Test
    public void testOverrideHttpDate() {
        // Wed, 01 Jan 2025 00:00:00 GMT == 1735689600000 epoch millis;
        // NOW is 10 minutes before it, so the override is those 10 minutes
        long dateEpochMillis = 1735689600000L;
        assertEquals(TimeUnit.MINUTES.toMillis(10),
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Wed, 01 Jan 2025 00:00:00 GMT",
                        dateEpochMillis - TimeUnit.MINUTES.toMillis(10)));
        // a date already in the past means "retry now" -> no override
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(503,
                        "Wed, 01 Jan 2025 00:00:00 GMT",
                        dateEpochMillis + TimeUnit.MINUTES.toMillis(10)));
        // exact match: zero delta, zero override
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(403,
                        "Wed, 01 Jan 2025 00:00:00 GMT",
                        dateEpochMillis));
    }

    @Test
    public void testOverrideUnsupportedOrBrokenDateIsZero() {
        // month names of the obsolete RFC 850 / asctime forms do not parse
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Wednesday, 01-Jan-25 00:00:00 GMT", NOW));
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Wed Jan  1 00:00:00 2025", NOW));
        // broken IMF-fixdate
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Wed, 01 Foo 2025 00:00:00 GMT", NOW));
        // java.time also rejects an IMF-fixdate whose day-of-week name does
        // not match the date (01 Jan 2030 is a Tuesday, not a Wednesday) -
        // strictness is correct: a self-inconsistent hint is malformed
        assertEquals(0L,
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Wed, 01 Jan 2030 00:00:00 GMT", NOW));
    }

    @Test
    public void testOverrideClampedAt24h() {
        // 1,000,000 seconds is ~11.6 days -> clamped to the 24h ceiling
        assertEquals(MAX_MILLIS,
                SyncBackoffPolicy.retryAfterOverrideMillis(429, "1000000", NOW));
        // a far-future HTTP-date is clamped the same way (01 Jan 2030 is a
        // Tuesday - the day-of-week name must match the date for the parse)
        assertEquals(MAX_MILLIS,
                SyncBackoffPolicy.retryAfterOverrideMillis(429,
                        "Tue, 01 Jan 2030 00:00:00 GMT", NOW));
    }

    // ----- effectiveWindowMillis (max of ladder and hint) -----

    @Test
    public void testEffectiveWindowIsMaxOfLadderAndHint() {
        // one failure = 15m ladder; 20m hint dominates
        assertEquals(TimeUnit.MINUTES.toMillis(20),
                SyncBackoffPolicy.effectiveWindowMillis(channelWith(1, null),
                        429, "1200", NOW));
        // 10m hint is BELOW the 15m ladder -> ladder wins
        assertEquals(BASE_MILLIS,
                SyncBackoffPolicy.effectiveWindowMillis(channelWith(1, null),
                        429, "600", NOW));
        // header on a non-honored status -> ladder alone
        assertEquals(TimeUnit.HOURS.toMillis(2),
                SyncBackoffPolicy.effectiveWindowMillis(channelWith(4, null),
                        500, "99999", NOW));
        // healthy channel with a hint -> hint alone
        assertEquals(TimeUnit.MINUTES.toMillis(10),
                SyncBackoffPolicy.effectiveWindowMillis(channelWith(0, null),
                        429, "600", NOW));
        // nothing at all -> 0
        assertEquals(0L,
                SyncBackoffPolicy.effectiveWindowMillis(channelWith(0, null), null, null, NOW));
    }

    // ----- extraIncrementsForRetryAfter (counter-encoding rule) -----

    @Test
    public void testNoExtraIncrementsWithoutOverride() {
        assertEquals(0, SyncBackoffPolicy.extraIncrementsForRetryAfter(0, 0));
        assertEquals(0, SyncBackoffPolicy.extraIncrementsForRetryAfter(3, 0));
    }

    @Test
    public void testExactRungMatchNeedsNoExtraIncrement() {
        // hint equals the rung the ordinary failure increment already gives
        // (fresh channel: counter 0 -> 1 -> 15m window): 0 extra writes
        assertEquals(0, SyncBackoffPolicy.extraIncrementsForRetryAfter(0, BASE_MILLIS));
    }

    @Test
    public void testHintSmallerThanExistingLadderNeedsNoExtraIncrement() {
        // the hint can only LENGTHEN the quiet period: a channel already 3
        // failures deep gets a 60m window from the ordinary increment, far
        // above a 10m hint -> the failure path costs exactly one write
        assertEquals(0, SyncBackoffPolicy.extraIncrementsForRetryAfter(3,
                TimeUnit.MINUTES.toMillis(10)));
    }

    @Test
    public void testExtraIncrementsReachSmallestCoveringRung() {
        // fresh channel, 20m hint: ordinary increment gives rung 1 (15m),
        // rung 2 (30m) is the smallest covering one -> 1 extra increment
        assertEquals(1, SyncBackoffPolicy.extraIncrementsForRetryAfter(0,
                TimeUnit.MINUTES.toMillis(20)));
        // 45m hint: rung 1 (15m) and rung 2 (30m) too small, rung 3 (60m)
        // covers -> 2 extra increments
        assertEquals(2, SyncBackoffPolicy.extraIncrementsForRetryAfter(0,
                TimeUnit.MINUTES.toMillis(45)));
        // mid-ladder channel (2 failures -> ordinary increment gives rung 3 =
        // 60m): a 3h hint needs rung 5 (240m) -> 5 - 3 = 2 extra increments
        assertEquals(2, SyncBackoffPolicy.extraIncrementsForRetryAfter(2,
                TimeUnit.HOURS.toMillis(3)));
    }

    @Test
    public void testExtraIncrementsBoundedAtCap() {
        // a clamped 24h hint on a fresh channel: rung 8 is the first capped
        // rung -> 7 extra increments, and never more however large the hint
        assertEquals(7, SyncBackoffPolicy.extraIncrementsForRetryAfter(0, MAX_MILLIS));
        assertEquals(7, SyncBackoffPolicy.extraIncrementsForRetryAfter(0, Long.MAX_VALUE));
    }

    @Test
    public void testExtraIncrementsOverflowProofOnHugeCounter() {
        // an already-capped counter (500 failures -> 24h window) covers even
        // the maximum hint with zero extra writes, no long overflow anywhere
        assertEquals(0, SyncBackoffPolicy.extraIncrementsForRetryAfter(500, MAX_MILLIS));
    }
}
