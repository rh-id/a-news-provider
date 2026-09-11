package m.co.rh.id.a_news_provider.app.provider.command;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SearchRssItemsCmd covering LIKE wildcard escaping,
 * filter/sort mapping and pagination limit handling.
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

    @Test
    public void searchEscapesLikeWildcards() {
        mSearchRssItemsCmd.setQuery("50%_a\\");

        verify(mMockRssDao).searchRssItemsWithLimit(eq("50\\%\\_a\\\\"),
                isNull(), isNull(), isNull(), eq(1000));
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

        verify(mMockRssDao).searchRssItemsWithLimit(eq("news"),
                isNull(), eq(0), isNull(), eq(1000));
    }

    @Test
    public void filterMappingMapsFavoriteToIsFavoriteOne() {
        mSearchRssItemsCmd.setFilterType(BaseRssItemsCmd.FILTER_BY_FAVORITE);
        mSearchRssItemsCmd.setQuery("news");

        verify(mMockRssDao).searchRssItemsWithLimit(eq("news"),
                isNull(), isNull(), eq(1), eq(1000));
    }

    @Test
    public void sortOrderAscUsesAscQuery() {
        mSearchRssItemsCmd.setSortOrder(BaseRssItemsCmd.SORT_ORDER_OLDEST);
        mSearchRssItemsCmd.setQuery("x");

        verify(mMockRssDao).searchRssItemsWithLimitAsc(eq("x"),
                isNull(), isNull(), isNull(), eq(1000));
    }

    @Test
    public void loadNextPageDoublesLimit() {
        List<RssItem> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            RssItem rssItem = new RssItem();
            rssItem.title = "item " + i;
            items.add(rssItem);
        }
        when(mMockRssDao.searchRssItemsWithLimit(eq("item"),
                isNull(), isNull(), isNull(), eq(1000))).thenReturn(items);

        mSearchRssItemsCmd.setQuery("item");
        assertEquals(1000, mSearchRssItemsCmd.getAllRssItems().size());

        mSearchRssItemsCmd.loadNextPage();

        verify(mMockRssDao).searchRssItemsWithLimit(eq("item"),
                isNull(), isNull(), isNull(), eq(2000));
    }

    @Test
    public void unchangedQuerySkipsReload() {
        when(mMockRssDao.searchRssItemsWithLimit(eq("news"),
                isNull(), isNull(), isNull(), eq(1000))).thenReturn(new ArrayList<>());

        mSearchRssItemsCmd.setQuery("news");
        verify(mMockRssDao, times(1)).searchRssItemsWithLimit(eq("news"),
                isNull(), isNull(), isNull(), eq(1000));

        mSearchRssItemsCmd.setQuery("news");
        verify(mMockRssDao, times(1)).searchRssItemsWithLimit(eq("news"),
                isNull(), isNull(), isNull(), eq(1000));

        mSearchRssItemsCmd.setQuery("  news  ");
        verify(mMockRssDao, times(1)).searchRssItemsWithLimit(eq("news"),
                isNull(), isNull(), isNull(), eq(1000));

        mSearchRssItemsCmd.setQuery("other");
        verify(mMockRssDao, times(1)).searchRssItemsWithLimit(eq("other"),
                isNull(), isNull(), isNull(), eq(1000));
        verify(mMockRssDao, times(2)).searchRssItemsWithLimit(anyString(),
                isNull(), isNull(), isNull(), eq(1000));
    }

    private static class DirectExecutorService extends AbstractExecutorService {

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            // no-op
        }

        @Override
        public List<Runnable> shutdownNow() {
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return false;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
