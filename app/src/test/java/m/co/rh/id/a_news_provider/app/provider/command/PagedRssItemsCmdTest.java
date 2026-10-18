package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.annotation.NonNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.LinkCount;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PagedRssItemsCmd}'s combineLatest load-mode mapping:
 * a channel switch must trigger a FRESH keyset load (cursor reset), while a
 * sync-driven re-emission in all-channels mode must trigger a REFRESH that
 * preserves the accumulated window, and a re-emission of the same selected
 * channel must not load at all. Plus the duplicate-story collapsing branch
 * (issue #45): with the setting on and all channels shown, loadItems() must use
 * the link-grouped DAO queries and fill duplicateCount per row, while a selected
 * channel (or the setting off) must keep the flat queries.
 */
public class PagedRssItemsCmdTest {

    private Provider mMockProvider;
    private RssDao mMockRssDao;
    private ILogger mMockLogger;
    private RssChangeNotifier mMockChangeNotifier;
    private RssChannelStateNotifier mMockChannelStateNotifier;
    private AppSharedPreferences mMockSharedPreferences;
    private PublishProcessor<Optional<RssModel>> mRssModelProcessor;
    private BehaviorProcessor<Optional<RssChannel>> mChannelSubject;
    private RecordingPagedRssItemsCmd mCmd;
    private Disposable mDisposable;

    /**
     * Recording subclass of the real command: the combineLatest mapper,
     * load-mode dispatch and cursor state all run for real, only the DAO call
     * is intercepted.
     */
    private static class RecordingPagedRssItemsCmd extends PagedRssItemsCmd {
        final List<long[]> mRequests = new ArrayList<>();
        final Deque<List<RssItem>> mPages = new ArrayDeque<>();

        RecordingPagedRssItemsCmd(Provider provider) {
            super(provider);
        }

        void queuePage(List<RssItem> page) {
            mPages.add(page);
        }

        @NonNull
        @Override
        protected ArrayList<RssItem> loadItems(Long cursorSortKey, Long cursorId, int limit) {
            mRequests.add(new long[]{
                    cursorSortKey == null ? -1L : cursorSortKey,
                    cursorId == null ? -1L : cursorId,
                    limit});
            List<RssItem> page = mPages.poll();
            ArrayList<RssItem> result = new ArrayList<>();
            if (page != null) {
                result.addAll(page);
            }
            return result;
        }
    }

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockRssDao = mock(RssDao.class);
        mMockLogger = mock(ILogger.class);
        mMockChangeNotifier = mock(RssChangeNotifier.class);
        mMockChannelStateNotifier = mock(RssChannelStateNotifier.class);
        mMockSharedPreferences = mock(AppSharedPreferences.class);
        ExecutorService executorService = new DirectExecutorService();
        when(mMockProvider.get(ExecutorService.class)).thenReturn(executorService);
        when(mMockProvider.get(RssDao.class)).thenReturn(mMockRssDao);
        when(mMockProvider.get(ILogger.class)).thenReturn(mMockLogger);
        when(mMockProvider.get(RssChangeNotifier.class)).thenReturn(mMockChangeNotifier);
        when(mMockProvider.get(RssChannelStateNotifier.class)).thenReturn(mMockChannelStateNotifier);
        when(mMockProvider.get(AppSharedPreferences.class)).thenReturn(mMockSharedPreferences);

        mRssModelProcessor = PublishProcessor.create();
        mChannelSubject = BehaviorProcessor.createDefault(Optional.empty());
        when(mMockChangeNotifier.liveNewRssModel()).thenReturn(mRssModelProcessor);
        when(mMockChannelStateNotifier.selectedRssChannel()).thenReturn(mChannelSubject);

        // command built without subscribing: combineLatest is cold, so the
        // initial (FRESH-context) load fires when the test subscribes, after
        // queueing its expected page
        mCmd = new RecordingPagedRssItemsCmd(mMockProvider);
    }

    private void subscribeItems() {
        mDisposable = mCmd.getRssItems().subscribe(items -> {
            // drain the stream
        });
    }

    @After
    public void tearDown() {
        if (mDisposable != null) {
            mDisposable.dispose();
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

    private static RssChannel channel(long id) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.id = id;
        rssChannel.url = "http://test.com/feed" + id;
        rssChannel.feedName = "Feed " + id;
        return rssChannel;
    }

    private static void assertRequest(long[] request, long expectedSortKey,
                                      long expectedId, int expectedLimit) {
        assertEquals(expectedSortKey, request[0]);
        assertEquals(expectedId, request[1]);
        assertEquals(expectedLimit, request[2]);
    }

    @Test
    public void channelChangeLoadsFreshAndSyncRefreshKeepsWindow() {
        // 1) initial all-channels emission: first window (empty accumulator
        //    clamps to one page)
        mCmd.queuePage(pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE));
        subscribeItems();
        assertEquals(1, mCmd.mRequests.size());
        assertRequest(mCmd.mRequests.get(0), -1L, -1L, BaseRssItemsCmd.PAGE_SIZE);

        // 2) deepen the list to 2 pages, so a following refresh must target 200
        mCmd.queuePage(pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE, BaseRssItemsCmd.PAGE_SIZE));
        mCmd.loadNextPage();
        assertEquals(2, mCmd.mRequests.size());
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, mCmd.getAllRssItems().size());

        // 3) sync re-emission while ALL channels are shown: same context,
        //    REFRESH keeps the accumulated 2-page window
        mCmd.queuePage(pageOfIds(301, BaseRssItemsCmd.PAGE_SIZE * 2));
        mRssModelProcessor.onNext(Optional.of(new RssModel(
                channel(1), new ArrayList<>())));
        assertEquals(3, mCmd.mRequests.size());
        assertRequest(mCmd.mRequests.get(2), -1L, -1L, BaseRssItemsCmd.PAGE_SIZE * 2);
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2, mCmd.getAllRssItems().size());

        // 4) channel switch: context changed, FRESH resets the cursor
        mCmd.queuePage(pageOfIds(201, BaseRssItemsCmd.PAGE_SIZE));
        mChannelSubject.onNext(Optional.of(channel(42)));
        assertEquals(4, mCmd.mRequests.size());
        assertRequest(mCmd.mRequests.get(3), -1L, -1L, BaseRssItemsCmd.PAGE_SIZE);
        assertEquals(BaseRssItemsCmd.PAGE_SIZE, mCmd.getAllRssItems().size());

        // 5) same channel re-selected and a sync emission with a channel
        //    selected: no reload at all (the all-channels path is what reacts
        //    to sync results)
        mChannelSubject.onNext(Optional.of(channel(42)));
        mRssModelProcessor.onNext(Optional.of(new RssModel(
                channel(42), new ArrayList<>())));
        assertEquals(4, mCmd.mRequests.size());
    }

    // ==================================================================================
    // Duplicate-story collapsing branch (issue #45)
    // ==================================================================================

    @Test
    public void loadItems_collapseOnAndAllChannelsUsesGroupedQueriesAndFillsCounts() {
        when(mMockSharedPreferences.isCollapseDuplicateStories()).thenReturn(true);
        ArrayList<RssItem> groupedPage = new ArrayList<>();
        RssItem repA = item(1, 1000);
        repA.link = "http://test.com/a";
        RssItem repB = item(2, 900);
        repB.link = "http://test.com/b";
        groupedPage.add(repA);
        groupedPage.add(repB);
        // the command's initial filter is FILTER_BY_UNREAD, so loadItems() derives
        // isRead = 0 / isFavorite = null; spelling the full argument contract out
        // makes a filter-plumbing regression fail here, not silently in production
        when(mMockRssDao.findRssItemsPageGroupedByLinkDesc(
                isNull(), eq(0), isNull(), isNull(), isNull(), eq(BaseRssItemsCmd.PAGE_SIZE)))
                .thenReturn(groupedPage);
        // only link A has copies; link B's group is a singleton and must default to 1
        List<LinkCount> counts = new ArrayList<>();
        LinkCount countA = new LinkCount();
        countA.link = "http://test.com/a";
        countA.cnt = 3;
        counts.add(countA);
        when(mMockRssDao.countRssItemsByLinks(anyList())).thenReturn(counts);

        // the REAL loadItems must run: a plain (non-recording) command instance
        PagedRssItemsCmd cmd = new PagedRssItemsCmd(mMockProvider);
        mDisposable = cmd.getRssItems().subscribe(items -> {
            // drain the stream
        });

        // initial all-channels emission: REFRESH with an empty accumulator = one page
        ArrayList<RssItem> items = cmd.getAllRssItems();
        assertEquals(2, items.size());
        assertEquals(3, items.get(0).duplicateCount);
        assertEquals(1, items.get(1).duplicateCount);
        verify(mMockRssDao).findRssItemsPageGroupedByLinkDesc(
                isNull(), eq(0), isNull(), isNull(), isNull(), eq(BaseRssItemsCmd.PAGE_SIZE));
        verify(mMockRssDao).countRssItemsByLinks(anyList());
        verify(mMockRssDao, never()).findRssItemsPageDesc(
                any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    public void loadItems_collapseOffUsesFlatQueriesAndLeavesCountsZero() {
        when(mMockSharedPreferences.isCollapseDuplicateStories()).thenReturn(false);
        ArrayList<RssItem> flatPage = new ArrayList<>();
        RssItem flatItem = item(7, 1000);
        flatItem.link = "http://test.com/a";
        flatPage.add(flatItem);
        when(mMockRssDao.findRssItemsPageDesc(
                isNull(), eq(0), isNull(), isNull(), isNull(), eq(BaseRssItemsCmd.PAGE_SIZE)))
                .thenReturn(flatPage);

        PagedRssItemsCmd cmd = new PagedRssItemsCmd(mMockProvider);
        mDisposable = cmd.getRssItems().subscribe(items -> {
            // drain the stream
        });

        ArrayList<RssItem> items = cmd.getAllRssItems();
        assertEquals(1, items.size());
        // not a grouped load: duplicateCount stays at its field default
        assertEquals(0, items.get(0).duplicateCount);
        verify(mMockRssDao).findRssItemsPageDesc(
                isNull(), eq(0), isNull(), isNull(), isNull(), eq(BaseRssItemsCmd.PAGE_SIZE));
        verify(mMockRssDao, never()).findRssItemsPageGroupedByLinkDesc(
                any(), any(), any(), any(), any(), anyInt());
        verify(mMockRssDao, never()).countRssItemsByLinks(anyList());
    }

    @Test
    public void loadItems_selectedChannelOverridesCollapseSetting() {
        // setting ON, but a channel is selected: grouping only ever happens in
        // the all-channels view, the flat query must win
        when(mMockSharedPreferences.isCollapseDuplicateStories()).thenReturn(true);
        PagedRssItemsCmd cmd = new PagedRssItemsCmd(mMockProvider);
        mDisposable = cmd.getRssItems().subscribe(items -> {
            // drain the stream
        });

        // initial emission: all channels (grouped query runs once here)
        verify(mMockRssDao, times(1)).findRssItemsPageGroupedByLinkDesc(
                any(), any(), any(), any(), any(), anyInt());

        // channel switch: FRESH load for channel 42 through the FLAT query
        mChannelSubject.onNext(Optional.of(channel(42)));
        verify(mMockRssDao, times(1)).findRssItemsPageDesc(
                eq(42L), eq(0), isNull(), isNull(), isNull(), eq(BaseRssItemsCmd.PAGE_SIZE));
        // the grouped query ran ONLY for the initial all-channels load
        verify(mMockRssDao, times(1)).findRssItemsPageGroupedByLinkDesc(
                any(), any(), any(), any(), any(), anyInt());
        verify(mMockRssDao, never()).countRssItemsByLinks(anyList());
    }
}
