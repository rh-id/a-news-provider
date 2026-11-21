package m.co.rh.id.a_news_provider.app.model;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link FeedHealthItem} (issue #42) covering: the derived
 * status (never-synced healthy channel = OK, real failure columns = ERROR,
 * paused = PAUSED even with a recorded error, Retry-After-inflated counter
 * without error columns = still OK), the errors-first comparator ordering
 * (ERROR, PAUSED, OK; name tiebreak case-insensitive; id as final tiebreak)
 * and the backoff-until math over the persisted attempt stamp + the
 * SyncBackoffPolicy window.
 */
public class FeedHealthItemTest {

    // arbitrary fixed "now" - the model is pure, every test pins its own time
    private static final long NOW = 1_700_000_000_000L;
    private static final long BASE_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final long MAX_MILLIS = TimeUnit.HOURS.toMillis(24);

    private static RssChannel healthyChannel(String feedName, long id) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.id = id;
        rssChannel.feedName = feedName;
        return rssChannel;
    }

    private static FeedHealthItem itemOf(RssChannel rssChannel) {
        return new FeedHealthItem(rssChannel, 10, 3);
    }

    private static Date millisBeforeNow(long millis) {
        return new Date(NOW - millis);
    }

    // ----- status derivation -----

    @Test
    public void testNeverSyncedHealthyChannelIsOkNotError() {
        // a fresh channel has neither failure columns nor a failure counter:
        // nothing may read as ERROR before the very first sync attempt
        assertEquals(FeedHealthItem.Status.OK,
                itemOf(healthyChannel("Feed", 1)).getStatus());
    }

    @Test
    public void testChannelWithLastErrorIsError() {
        // HTTP failures carry a status AND a message; both must map to ERROR
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.lastHttpStatus = 503;
        rssChannel.lastError = "Service Unavailable";
        assertEquals(FeedHealthItem.Status.ERROR, itemOf(rssChannel).getStatus());
    }

    @Test
    public void testChannelWithOnlyHttpStatusIsError() {
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.lastHttpStatus = 429;
        assertEquals(FeedHealthItem.Status.ERROR, itemOf(rssChannel).getStatus());
    }

    @Test
    public void testChannelWithOnlyErrorMessageIsError() {
        // timeout/network failures carry a real error but NO HTTP status
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.lastError = "TimeoutError";
        assertEquals(FeedHealthItem.Status.ERROR, itemOf(rssChannel).getStatus());
    }

    @Test
    public void testPausedChannelIsPausedEvenWithRecordedError() {
        // a paused feed is not synced: its failure columns are frozen history,
        // and classifying it ERROR would offer a sync-now the worker skips
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.isPaused = true;
        rssChannel.lastHttpStatus = 500;
        rssChannel.lastError = "Internal Server Error";
        assertEquals(FeedHealthItem.Status.PAUSED, itemOf(rssChannel).getStatus());
    }

    @Test
    public void testRetryAfterInflatedCounterWithoutErrorColumnsIsOk() {
        // Retry-After counter-encoding (issue #50) inflates consecutiveFailures
        // as a pure skip schedule WITHOUT a real error - that is a schedule,
        // not a failure, so the status stays OK
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.consecutiveFailures = 5;
        rssChannel.lastSyncAttemptDateTime = millisBeforeNow(TimeUnit.MINUTES.toMillis(1));
        assertEquals(FeedHealthItem.Status.OK, itemOf(rssChannel).getStatus());
    }

    // ----- errors-first comparator -----

    @Test
    public void testComparatorOrdersErrorsFirstThenPausedThenOk() {
        RssChannel okChannel = healthyChannel("Zeta", 1);
        RssChannel pausedChannel = healthyChannel("Alpha", 2);
        pausedChannel.isPaused = true;
        RssChannel errorChannel = healthyChannel("Omega", 3);
        errorChannel.lastError = "TimeoutError";

        List<FeedHealthItem> items = new ArrayList<>();
        items.add(itemOf(okChannel));
        items.add(itemOf(pausedChannel));
        items.add(itemOf(errorChannel));
        Collections.sort(items, FeedHealthItem.ERRORS_FIRST_COMPARATOR);

        assertEquals(3, items.size());
        assertEquals(FeedHealthItem.Status.ERROR, items.get(0).getStatus());
        assertEquals(FeedHealthItem.Status.PAUSED, items.get(1).getStatus());
        assertEquals(FeedHealthItem.Status.OK, items.get(2).getStatus());
    }

    @Test
    public void testComparatorOrdersWithinSameStatusByNameCaseInsensitive() {
        // "beta" must sort before "Charlie" despite the lowercase start
        RssChannel charlieChannel = healthyChannel("Charlie", 1);
        RssChannel betaChannel = healthyChannel("beta", 2);

        List<FeedHealthItem> items = new ArrayList<>();
        items.add(itemOf(charlieChannel));
        items.add(itemOf(betaChannel));
        Collections.sort(items, FeedHealthItem.ERRORS_FIRST_COMPARATOR);

        assertEquals("beta", items.get(0).rssChannel.feedName);
        assertEquals("Charlie", items.get(1).rssChannel.feedName);
    }

    @Test
    public void testComparatorTiebreaksEqualNamesById() {
        RssChannel olderChannel = healthyChannel("Same", 7);
        RssChannel newerChannel = healthyChannel("Same", 12);

        List<FeedHealthItem> items = new ArrayList<>();
        items.add(itemOf(newerChannel));
        items.add(itemOf(olderChannel));
        Collections.sort(items, FeedHealthItem.ERRORS_FIRST_COMPARATOR);

        assertEquals(7L, items.get(0).rssChannel.id.longValue());
        assertEquals(12L, items.get(1).rssChannel.id.longValue());
    }

    @Test
    public void testComparatorSortsNullNamesLastWithinSameStatus() {
        RssChannel namedChannel = healthyChannel("Named", 1);
        RssChannel namelessChannel = healthyChannel(null, 2);

        List<FeedHealthItem> items = new ArrayList<>();
        items.add(itemOf(namelessChannel));
        items.add(itemOf(namedChannel));
        Collections.sort(items, FeedHealthItem.ERRORS_FIRST_COMPARATOR);

        assertEquals("Named", items.get(0).rssChannel.feedName);
    }

    // ----- backoff-until schedule -----

    @Test
    public void testBackoffUntilZeroWithoutFailures() {
        assertEquals(0L,
                itemOf(healthyChannel("Feed", 1)).getBackoffUntilMillis(NOW));
    }

    @Test
    public void testBackoffUntilZeroWithNullAttemptStamp() {
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.consecutiveFailures = 2;
        assertEquals(0L, itemOf(rssChannel).getBackoffUntilMillis(NOW));
    }

    @Test
    public void testBackoffUntilIsLastAttemptPlusWindow() {
        // one failure, attempted 5 minutes ago: quiet until attempt + 15 min
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.consecutiveFailures = 1;
        rssChannel.lastSyncAttemptDateTime = millisBeforeNow(TimeUnit.MINUTES.toMillis(5));
        FeedHealthItem feedHealthItem = itemOf(rssChannel);
        assertEquals(NOW + BASE_MILLIS - TimeUnit.MINUTES.toMillis(5),
                feedHealthItem.getBackoffUntilMillis(NOW));
        assertTrue(feedHealthItem.getBackoffUntilMillis(NOW) > NOW);
    }

    @Test
    public void testBackoffUntilZeroOnceWindowElapsed() {
        // one failure, 16 minutes elapsed > 15-minute window: no schedule
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.consecutiveFailures = 1;
        rssChannel.lastSyncAttemptDateTime =
                millisBeforeNow(BASE_MILLIS + TimeUnit.MINUTES.toMillis(1));
        assertEquals(0L, itemOf(rssChannel).getBackoffUntilMillis(NOW));
    }

    @Test
    public void testBackoffUntilUsesCappedWindow() {
        // 8+ failures sit at the 24h cap (issue #50 ladder)
        RssChannel rssChannel = healthyChannel("Feed", 1);
        rssChannel.consecutiveFailures = 8;
        Date lastAttempt = millisBeforeNow(TimeUnit.HOURS.toMillis(1));
        rssChannel.lastSyncAttemptDateTime = lastAttempt;
        assertEquals(lastAttempt.getTime() + MAX_MILLIS,
                itemOf(rssChannel).getBackoffUntilMillis(NOW));
    }

    // ----- serialization round trip -----

    @Test
    public void testSerializationRoundTripPreservesRowState() throws Exception {
        // FeedHealthItem is Serializable so the row StatefulView can hold it in
        // its (non-transient) SerialBehaviorSubject - the same rationale as
        // RssItem for RssItemSV. The ERROR-state channel exercises the column
        // fields; the counts and the derived status must survive identically.
        RssChannel rssChannel = healthyChannel("Round Trip", 42);
        rssChannel.lastHttpStatus = 500;
        rssChannel.lastError = "Internal Server Error";
        FeedHealthItem original = new FeedHealthItem(rssChannel, 20, 7);

        ByteArrayOutputStream byteOutput = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(byteOutput)) {
            objectOutput.writeObject(original);
        }
        FeedHealthItem restored;
        try (ObjectInputStream objectInput = new ObjectInputStream(
                new ByteArrayInputStream(byteOutput.toByteArray()))) {
            restored = (FeedHealthItem) objectInput.readObject();
        }

        assertEquals(42L, restored.rssChannel.id.longValue());
        assertEquals("Round Trip", restored.rssChannel.feedName);
        assertEquals(500, restored.rssChannel.lastHttpStatus.intValue());
        assertEquals("Internal Server Error", restored.rssChannel.lastError);
        assertEquals(20, restored.totalItems);
        assertEquals(7, restored.unreadItems);
        assertEquals(FeedHealthItem.Status.ERROR, restored.getStatus());
        // the errors-first comparator is a STATIC field - statics are excluded
        // from Java serialization by definition, so it is not part of the round
        // trip; it is only asserted to still exist on the restored class
        assertNotNull(FeedHealthItem.ERRORS_FIRST_COMPARATOR);
    }
}
