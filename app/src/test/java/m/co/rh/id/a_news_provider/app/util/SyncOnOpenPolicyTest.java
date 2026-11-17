package m.co.rh.id.a_news_provider.app.util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for SyncOnOpenPolicy (issue #50) covering the five
 * decisions of the sync-on-open debounce gate: disabled (never sync),
 * never-synced stamp of 0 (always sync), a fresh request inside the 15-minute
 * window (suppress), a request EXACTLY at the window boundary (sync - the
 * window is a quiet period, not a scheduled instant) and a stale request
 * (sync); plus the clock-skew edge of a stamp in the future (suppressed until
 * the window elapses). The policy is pure, so every test pins its own fixed
 * time.
 */
public class SyncOnOpenPolicyTest {

    private static final long DEBOUNCE_MILLIS = TimeUnit.MINUTES.toMillis(15);
    // arbitrary fixed "now" - the policy is pure, so every test pins its own time
    private static final long NOW = 1_700_000_000_000L;

    @Test
    public void testDisabledNeverSyncs() {
        // the setting is the hard off-switch: even a stamp from the stone age
        // (or never-synced 0) must not enqueue an automatic sync
        assertFalse(SyncOnOpenPolicy.shouldSyncOnOpen(false, 0, NOW));
        assertFalse(SyncOnOpenPolicy.shouldSyncOnOpen(false,
                NOW - TimeUnit.HOURS.toMillis(24), NOW));
    }

    @Test
    public void testNeverSyncedStampAlwaysSyncs() {
        // 0 means "never requested" (fresh install / wiped pref): the very
        // first app open must sync immediately
        assertTrue(SyncOnOpenPolicy.shouldSyncOnOpen(true, 0, NOW));
    }

    @Test
    public void testFreshRequestIsSuppressed() {
        // 1 millisecond inside the window: suppressed
        assertFalse(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW - DEBOUNCE_MILLIS + 1, NOW));
        // comfortably fresh: 14 minutes old
        assertFalse(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW - TimeUnit.MINUTES.toMillis(14), NOW));
    }

    @Test
    public void testExactlyAtWindowBoundarySyncs() {
        // boundary is inclusive (>=): a request exactly 15 minutes old is due,
        // mirroring SyncBackoffPolicy's "< window = skip" semantics
        assertTrue(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW - DEBOUNCE_MILLIS, NOW));
        assertEquals(TimeUnit.MINUTES.toMillis(15),
                SyncOnOpenPolicy.SYNC_ON_OPEN_DEBOUNCE_MILLIS);
    }

    @Test
    public void testStaleRequestSyncs() {
        // 16 minutes old: past the window
        assertTrue(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW - TimeUnit.MINUTES.toMillis(16), NOW));
        // a day old (app not opened since yesterday): due
        assertTrue(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW - TimeUnit.HOURS.toMillis(24), NOW));
    }

    @Test
    public void testFutureStampIsSuppressedUntilWindowElapses() {
        // a stamp in the future (device clock was ahead when the stamp was
        // written, then corrected) produces a NEGATIVE elapsed time, which is
        // inside every window: the gate suppresses until real time passes the
        // stamped moment plus the debounce - the same "negative elapsed = skip"
        // property SyncBackoffPolicy has for a future attempt stamp. Accepted:
        // the state self-heals once the clock catches up (bounded by how far
        // the clock was off) and a manual sync always works regardless.
        assertFalse(SyncOnOpenPolicy.shouldSyncOnOpen(true,
                NOW + TimeUnit.MINUTES.toMillis(5), NOW));
    }
}
