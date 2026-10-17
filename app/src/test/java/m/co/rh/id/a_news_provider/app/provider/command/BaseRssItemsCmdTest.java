package m.co.rh.id.a_news_provider.app.provider.command;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import androidx.annotation.NonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.observers.TestObserver;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BaseRssItemsCmd} keyset (cursor) pagination state
 * management: cursor resets on context changes, refresh window preservation,
 * the load-generation staleness guard and the next-page in-flight guard.
 */
public class BaseRssItemsCmdTest {

    private Provider mMockProvider;
    private RssDao mMockRssDao;
    private ILogger mMockLogger;
    private ExecutorService mExecutor;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockRssDao = mock(RssDao.class);
        mMockLogger = mock(ILogger.class);
        when(mMockProvider.get(RssDao.class)).thenReturn(mMockRssDao);
        when(mMockProvider.get(ILogger.class)).thenReturn(mMockLogger);
        useDirectExecutor();
    }

    @After
    public void tearDown() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
    }

    private void useDirectExecutor() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        mExecutor = new DirectExecutorService();
        when(mMockProvider.get(ExecutorService.class)).thenReturn(mExecutor);
    }

    private void useSingleThreadExecutor() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        mExecutor = Executors.newSingleThreadExecutor();
        when(mMockProvider.get(ExecutorService.class)).thenReturn(mExecutor);
    }

    /**
     * Fake command: records every loadItems(cursorSortKey, cursorId, limit)
     * request ([cursorSortKey|-1, cursorId|-1, limit]) and returns scripted
     * pages. Optionally blocks inside loadItems once, to hold a load in flight
     * while the test triggers competing loads.
     */
    private static class FakeRssItemsCmd extends BaseRssItemsCmd {
        final List<long[]> mRequests = Collections.synchronizedList(new ArrayList<>());
        final Deque<List<RssItem>> mPages = new ArrayDeque<>();
        volatile CountDownLatch mBlockEntered;
        volatile CountDownLatch mBlockRelease;

        FakeRssItemsCmd(Provider provider) {
            super(provider, FILTER_BY_NONE, false);
        }

        void queuePage(List<RssItem> page) {
            mPages.add(page);
        }

        void blockNextLoad() {
            mBlockEntered = new CountDownLatch(1);
            mBlockRelease = new CountDownLatch(1);
        }

        @Override
        protected String getTag() {
            return "FakeRssItemsCmd";
        }

        @NonNull
        @Override
        protected ArrayList<RssItem> loadItems(Long cursorSortKey, Long cursorId, int limit) {
            mRequests.add(new long[]{
                    cursorSortKey == null ? -1L : cursorSortKey,
                    cursorId == null ? -1L : cursorId,
                    limit});
            CountDownLatch entered = mBlockEntered;
            if (entered != null) {
                mBlockEntered = null;
                entered.countDown();
                try {
                    mBlockRelease.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            List<RssItem> page = mPages.poll();
            return page == null ? new ArrayList<>() : new ArrayList<>(page);
        }
    }

    private static RssItem item(long id, long sortKey) {
        RssItem rssItem = new RssItem();
        rssItem.id = id;
        rssItem.createdDateTime = new Date(sortKey);
        return rssItem;
    }

    private static List<RssItem> pageOfIds(long firstId, int count) {
        List<RssItem> page = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            page.add(item(firstId + i, 10_000L + firstId + i));
        }
        return page;
    }

    private static List<Long> idsOf(List<RssItem> items) {
        List<Long> ids = new ArrayList<>();
        for (RssItem rssItem : items) {
            ids.add(rssItem.id);
        }
        return ids;
    }

    private static void awaitRequestCount(FakeRssItemsCmd cmd, int count)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (cmd.mRequests.size() < count
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("waiting for " + count + " load requests",
                count, cmd.mRequests.size());
    }

    private static void awaitBlockedLoad(FakeRssItemsCmd cmd)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (cmd.mBlockEntered != null
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertNull("loadItems should be blocked in flight", cmd.mBlockEntered);
    }

    /**
     * A request being recorded and its list emission are separate steps on the
     * executor thread: poll the list size so the assertion cannot race the
     * emission that follows the recorded request.
     */
    private static void awaitListSize(FakeRssItemsCmd cmd, int size)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (cmd.getAllRssItems().size() != size
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("waiting for list size " + size,
                size, cmd.getAllRssItems().size());
    }

    @Test
    public void firstLoadUsesNullCursorAndPageSize() {
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));

        cmd.load();

        assertEquals(1, cmd.mRequests.size());
        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE},
                cmd.mRequests.get(0));
        assertEquals(BaseRssItemsCmd.PAGE_SIZE, cmd.getAllRssItems().size());
    }

    @Test
    public void filterChangeResetsCursorToFreshPage() {
        // TRAP: the keyset cursor of filter context A is invalid for filter
        // context B - reusing it would silently skip rows on every later page
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        List<RssItem> page1 = pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE);
        cmd.queuePage(page1);
        cmd.load();

        // serve the fresh load's page too
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        cmd.setFilterType(BaseRssItemsCmd.FILTER_BY_FAVORITE);

        assertEquals(2, cmd.mRequests.size());
        // fresh: null cursor even though a cursor was established by page 1
        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE},
                cmd.mRequests.get(1));
        assertEquals(BaseRssItemsCmd.PAGE_SIZE, cmd.getAllRssItems().size());
    }

    @Test
    public void sortChangeResetsCursorToFreshPage() {
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        cmd.load();

        cmd.setSortOrder(BaseRssItemsCmd.SORT_ORDER_OLDEST);

        assertEquals(2, cmd.mRequests.size());
        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE},
                cmd.mRequests.get(1));
    }

    @Test
    public void refreshPreservesAccumulatedTargetCount() {
        // TRAP: PAGE_SIZE-only reloads would collapse a deep list back to one
        // page (scroll jump); refresh keeps the accumulated window
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        cmd.load();
        cmd.queuePage(pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE));
        cmd.loadNextPage();
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, cmd.getAllRssItems().size());

        cmd.queuePage(pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE * 2,
                BaseRssItemsCmd.PAGE_SIZE * 2));
        cmd.load();

        assertEquals(3, cmd.mRequests.size());
        // refresh: fresh cursor (top of list) but window kept at the
        // accumulated 2 pages
        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE * 2},
                cmd.mRequests.get(2));
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, cmd.getAllRssItems().size());
    }

    @Test
    public void refreshOnEmptyListUsesPageSize() {
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(new ArrayList<>());

        cmd.load();

        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE},
                cmd.mRequests.get(0));
        assertTrue(cmd.getAllRssItems().isEmpty());
    }

    @Test
    public void loadNextPageBeforeAnyLoadDoesNothing() {
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);

        cmd.loadNextPage();

        assertTrue(cmd.mRequests.isEmpty());
    }

    @Test
    public void emittedListIsNeverMutated() {
        // BehaviorSubject re-emits the same instance to late subscribers, so a
        // previously-emitted list must never be appended to in place
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        cmd.load();
        ArrayList<RssItem> firstEmission = cmd.getAllRssItems();

        cmd.queuePage(pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE));
        cmd.loadNextPage();

        assertNotSame(firstEmission, cmd.getAllRssItems());
        assertEquals(BaseRssItemsCmd.PAGE_SIZE, firstEmission.size());
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, cmd.getAllRssItems().size());
    }

    @Test
    public void epochGuardDiscardsStaleNextPageResult() throws InterruptedException {
        useSingleThreadExecutor();
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        // every emission is the whole accumulated window: no emission in this
        // scenario may ever exceed one page
        TestObserver<ArrayList<RssItem>> itemsObserver = TestObserver.create();
        cmd.mItemsSubject.subscribe(itemsObserver);
        List<RssItem> page1 = pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE);
        cmd.queuePage(page1);
        cmd.load();
        awaitRequestCount(cmd, 1);
        awaitListSize(cmd, BaseRssItemsCmd.PAGE_SIZE);

        // hold the next-page load in flight...
        List<RssItem> stalePage = pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE);
        cmd.blockNextLoad();
        cmd.queuePage(stalePage);
        cmd.loadNextPage();
        awaitBlockedLoad(cmd);

        // ...then dispatch a refresh (new generation) while it is blocked
        List<RssItem> refreshedWindow = pageOfIds(101, BaseRssItemsCmd.PAGE_SIZE);
        cmd.queuePage(refreshedWindow);
        cmd.load();

        // release the stale page: its result must be discarded, not appended
        // behind the new context's cursor
        cmd.mBlockRelease.countDown();
        awaitRequestCount(cmd, 3);
        awaitListSize(cmd, BaseRssItemsCmd.PAGE_SIZE);

        assertEquals(idsOf(refreshedWindow), idsOf(cmd.getAllRssItems()));
        // the stale page's rows never made it into the list
        assertEquals(BaseRssItemsCmd.PAGE_SIZE, cmd.getAllRssItems().size());
        // DISCRIMINATING: request #3 is the refresh that ran AFTER the stale
        // page result settled. With the under-lock recheck intact the append
        // is discarded, so the refresh sees a one-page accumulator and
        // queries limit = PAGE_SIZE; without it the buggy append grows the
        // accumulator to two pages and the refresh would query
        // limit = 2 * PAGE_SIZE
        assertArrayEquals(new long[]{-1L, -1L, BaseRssItemsCmd.PAGE_SIZE},
                cmd.mRequests.get(2));
        // no emission ever carried the stale page's appended rows
        for (ArrayList<RssItem> emission : itemsObserver.values()) {
            assertTrue("emission exceeded one page: " + emission.size(),
                    emission.size() <= BaseRssItemsCmd.PAGE_SIZE);
        }
    }

    @Test
    public void doubleLoadNextPageWhileInFlightRunsSingleQuery() throws InterruptedException {
        useSingleThreadExecutor();
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        cmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        cmd.load();
        awaitRequestCount(cmd, 1);
        awaitListSize(cmd, BaseRssItemsCmd.PAGE_SIZE);

        // scroll listeners fire repeatedly while the list rests at the bottom:
        // the second trigger during an in-flight page load must be swallowed
        List<RssItem> page2 = pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE);
        cmd.blockNextLoad();
        cmd.queuePage(page2);
        cmd.loadNextPage();
        awaitBlockedLoad(cmd);
        cmd.loadNextPage();
        cmd.loadNextPage();

        cmd.mBlockRelease.countDown();
        awaitRequestCount(cmd, 2);
        awaitListSize(cmd, BaseRssItemsCmd.PAGE_SIZE * 2);

        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, cmd.getAllRssItems().size());
        assertEquals(idsOf(page2),
                idsOf(cmd.getAllRssItems()
                        .subList(BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE * 2)));
    }

    @Test
    public void nextPageQueuedBehindFreshLoadDiscardsOldCursorPage() throws InterruptedException {
        // a next-page request dispatched while a fresh load is still queued
        // ahead of it captures the OLD cursor; once the fresh load replaces the
        // cursor, the stale page must be discarded at dispatch
        useSingleThreadExecutor();
        FakeRssItemsCmd cmd = new FakeRssItemsCmd(mMockProvider);
        List<RssItem> page1 = pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE);
        cmd.queuePage(page1);
        cmd.load();
        awaitRequestCount(cmd, 1);
        awaitListSize(cmd, BaseRssItemsCmd.PAGE_SIZE);

        // dispatch the fresh load FIRST, the next-page request SECOND: the
        // next-page captures the pre-fresh cursor
        List<RssItem> refreshedWindow = pageOfIds(500, BaseRssItemsCmd.PAGE_SIZE);
        cmd.queuePage(refreshedWindow);
        cmd.load();
        cmd.loadNextPage();

        // the fresh window wins; the stale page (with the old cursor) never
        // appends - settle briefly to let a buggy append surface
        long deadline = System.currentTimeMillis() + 5000;
        while (!idsOf(cmd.getAllRssItems()).equals(idsOf(refreshedWindow))
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        Thread.sleep(250);
        assertEquals(idsOf(refreshedWindow), idsOf(cmd.getAllRssItems()));
        // only the initial load and the fresh load ran a query
        assertEquals(2, cmd.mRequests.size());
    }

    private static void assertArrayEquals(long[] expected, long[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("index " + i, expected[i], actual[i]);
        }
    }
}
