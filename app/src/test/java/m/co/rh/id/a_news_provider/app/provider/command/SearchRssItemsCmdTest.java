package m.co.rh.id.a_news_provider.app.provider.command;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchRssItemsCmd covering LIKE wildcard escaping,
 * filter/sort mapping and keyset (cursor) pagination handling.
 */
public class SearchRssItemsCmdTest {

    private Provider mMockProvider;
    private RssDao mMockRssDao;
    private ILogger mMockLogger;
    private SearchRssItemsCmd mSearchRssItemsCmd;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockRssDao = mock(RssDao.class);
        mMockLogger = mock(ILogger.class);
        when(mMockProvider.get(ExecutorService.class)).thenReturn(new DirectExecutorService());
        when(mMockProvider.get(RssDao.class)).thenReturn(mMockRssDao);
        when(mMockProvider.get(ILogger.class)).thenReturn(mMockLogger);
        mSearchRssItemsCmd = new SearchRssItemsCmd(mMockProvider);
    }

    /**
     * Builds a fake row with the id and effective sort key the keyset cursor
     * would carry after loading this row as the last row of a page.
     */
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

    @Test
    public void searchEscapesLikeWildcards() {
        mSearchRssItemsCmd.setQuery("50%_a\\");

        verify(mMockRssDao).searchRssItemsPageDesc(eq("50\\%\\_a\\\\"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void blankQueryEmitsEmptyAndSkipsDao() {
        mSearchRssItemsCmd.setQuery("   ");

        assertTrue(mSearchRssItemsCmd.getAllRssItems().isEmpty());
        verifyNoInteractions(mMockRssDao);
    }

    @Test
    public void filterMappingMapsUnreadToIsReadZero() {
        mSearchRssItemsCmd.setFilterType(BaseRssItemsCmd.FILTER_BY_UNREAD);
        mSearchRssItemsCmd.setQuery("news");

        verify(mMockRssDao).searchRssItemsPageDesc(eq("news"),
                isNull(), eq(0), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void filterMappingMapsFavoriteToIsFavoriteOne() {
        mSearchRssItemsCmd.setFilterType(BaseRssItemsCmd.FILTER_BY_FAVORITE);
        mSearchRssItemsCmd.setQuery("news");

        verify(mMockRssDao).searchRssItemsPageDesc(eq("news"),
                isNull(), isNull(), eq(1), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void sortOrderAscUsesAscQuery() {
        mSearchRssItemsCmd.setSortOrder(BaseRssItemsCmd.SORT_ORDER_OLDEST);
        mSearchRssItemsCmd.setQuery("x");

        verify(mMockRssDao).searchRssItemsPageAsc(eq("x"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void loadNextPageAppendsCursorPageUntilExhausted() {
        // first page: PAGE_SIZE full rows, so mHasMore is true
        List<RssItem> page1 = pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE);
        when(mMockRssDao.searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(page1);

        mSearchRssItemsCmd.setQuery("item");
        assertEquals(BaseRssItemsCmd.PAGE_SIZE,
                mSearchRssItemsCmd.getAllRssItems().size());

        // the next-page query must carry the cursor taken from the LAST row
        // of page 1 (its effective sort key + unique id), not a bigger LIMIT
        RssItem lastOfPage1 = page1.get(BaseRssItemsCmd.PAGE_SIZE - 1);
        List<RssItem> page2 = pageOfIds(1 + BaseRssItemsCmd.PAGE_SIZE,
                BaseRssItemsCmd.PAGE_SIZE);
        when(mMockRssDao.searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(),
                eq(lastOfPage1.createdDateTime.getTime()), eq(lastOfPage1.id),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(page2);

        mSearchRssItemsCmd.loadNextPage();

        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2,
                mSearchRssItemsCmd.getAllRssItems().size());
        verify(mMockRssDao).searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(),
                eq(lastOfPage1.createdDateTime.getTime()), eq(lastOfPage1.id),
                eq(BaseRssItemsCmd.PAGE_SIZE));

        // next page returns nothing: the list is exhausted
        RssItem lastOfPage2 = page2.get(BaseRssItemsCmd.PAGE_SIZE - 1);
        when(mMockRssDao.searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(),
                eq(lastOfPage2.createdDateTime.getTime()), eq(lastOfPage2.id),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(new ArrayList<>());

        mSearchRssItemsCmd.loadNextPage();
        assertEquals(BaseRssItemsCmd.PAGE_SIZE * 2,
                mSearchRssItemsCmd.getAllRssItems().size());

        // after exhaustion no further cursor query may be issued
        // (three queries so far: first page + two next pages)
        verify(mMockRssDao, times(3)).searchRssItemsPageDesc(anyString(),
                isNull(), isNull(), isNull(), any(), any(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
        mSearchRssItemsCmd.loadNextPage();
        verify(mMockRssDao, times(3)).searchRssItemsPageDesc(anyString(),
                isNull(), isNull(), isNull(), any(), any(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void partialPageMarksListExhausted() {
        // a page smaller than PAGE_SIZE on the first load: no more pages may
        // be requested afterwards
        List<RssItem> page1 = pageOfIds(1, 3);
        when(mMockRssDao.searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(page1);

        mSearchRssItemsCmd.setQuery("item");
        assertEquals(3, mSearchRssItemsCmd.getAllRssItems().size());

        mSearchRssItemsCmd.loadNextPage();

        verify(mMockRssDao, times(1)).searchRssItemsPageDesc(anyString(),
                any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    public void unchangedQuerySkipsReload() {
        when(mMockRssDao.searchRssItemsPageDesc(eq("news"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(new ArrayList<>());

        mSearchRssItemsCmd.setQuery("news");
        verify(mMockRssDao, times(1)).searchRssItemsPageDesc(eq("news"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));

        mSearchRssItemsCmd.setQuery("news");
        verify(mMockRssDao, times(1)).searchRssItemsPageDesc(eq("news"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));

        mSearchRssItemsCmd.setQuery("  news  ");
        verify(mMockRssDao, times(1)).searchRssItemsPageDesc(eq("news"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));

        mSearchRssItemsCmd.setQuery("other");
        verify(mMockRssDao, times(1)).searchRssItemsPageDesc(eq("other"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
        verify(mMockRssDao, times(2)).searchRssItemsPageDesc(anyString(),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void categoryTermWithLikeWildcardsEscapedAndCategorizedItemFlowsThrough() {
        // the DAO query matches categories too, so a chip-tapped term containing
        // % or _ must reach the DAO pre-escaped (kept literal, not a wildcard)
        RssItem categoryItem = new RssItem();
        categoryItem.title = "Markets close mixed";
        categoryItem.categories = "50%_growth\u001FTech";
        when(mMockRssDao.searchRssItemsPageDesc(eq("50\\%\\_growth"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE)))
                .thenReturn(new ArrayList<>(Collections.singletonList(categoryItem)));

        mSearchRssItemsCmd.setQuery("50%_growth");

        List<RssItem> results = mSearchRssItemsCmd.getAllRssItems();
        assertEquals(1, results.size());
        assertEquals("50%_growth\u001FTech", results.get(0).categories);
        verify(mMockRssDao).searchRssItemsPageDesc(eq("50\\%\\_growth"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
    }

    @Test
    public void sortChangeResetsCursorToFreshPage() {
        // trap: a stale keyset cursor from sort order A silently skips rows
        // when reused for sort order B - the cursor must be reset
        List<RssItem> page1 = pageOfIds(1, BaseRssItemsCmd.PAGE_SIZE);
        when(mMockRssDao.searchRssItemsPageDesc(eq("item"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(page1);
        mSearchRssItemsCmd.setQuery("item");

        when(mMockRssDao.searchRssItemsPageAsc(eq("item"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE))).thenReturn(page1);

        mSearchRssItemsCmd.setSortOrder(BaseRssItemsCmd.SORT_ORDER_OLDEST);

        // fresh cursor: both nulls, never the cursor of the previous sort order
        verify(mMockRssDao).searchRssItemsPageAsc(eq("item"),
                isNull(), isNull(), isNull(), isNull(), isNull(),
                eq(BaseRssItemsCmd.PAGE_SIZE));
        verify(mMockRssDao, never()).searchRssItemsPageAsc(eq("item"),
                isNull(), isNull(), isNull(), isNotNull(), isNotNull(), anyInt());
    }
}
