package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MarkItemsReadOnScrollCmd covering the in-memory flag flip,
 * the background persistence by link and the guarantee that nothing is emitted
 * on RssChangeNotifier (a reload there would jump the scroll position mid-scroll).
 */
public class MarkItemsReadOnScrollCmdTest {

    private Provider mMockProvider;
    private RssRepository mMockRssRepository;
    private RssChannelStateNotifier mMockRssChannelStateNotifier;
    private RssChangeNotifier mMockRssChangeNotifier;
    private ILogger mMockLogger;
    private MarkItemsReadOnScrollCmd mMarkItemsReadOnScrollCmd;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        Context mockContext = mock(Context.class);
        when(mockContext.getApplicationContext()).thenReturn(mockContext);
        when(mMockProvider.getContext()).thenReturn(mockContext);
        mMockRssRepository = mock(RssRepository.class);
        mMockRssChannelStateNotifier = mock(RssChannelStateNotifier.class);
        mMockRssChangeNotifier = mock(RssChangeNotifier.class);
        mMockLogger = mock(ILogger.class);
        when(mMockProvider.get(ExecutorService.class)).thenReturn(new DirectExecutorService());
        when(mMockProvider.get(RssRepository.class)).thenReturn(mMockRssRepository);
        when(mMockProvider.get(RssChannelStateNotifier.class)).thenReturn(mMockRssChannelStateNotifier);
        when(mMockProvider.get(RssChangeNotifier.class)).thenReturn(mMockRssChangeNotifier);
        when(mMockProvider.get(ILogger.class)).thenReturn(mMockLogger);
        mMarkItemsReadOnScrollCmd = new MarkItemsReadOnScrollCmd(mMockProvider);
    }

    @Test
    public void execute_flipsValidLinkItemsAndPersistsTheirLinksOnly() {
        RssItem validItem = createRssItem("valid news", "http://test.com/1");
        RssItem nullLinkItem = createRssItem("null link news", null);
        RssItem emptyLinkItem = createRssItem("empty link news", "");

        mMarkItemsReadOnScrollCmd.execute(Arrays.asList(validItem, nullLinkItem, emptyLinkItem));

        assertTrue(validItem.isRead);
        // null/empty-link items are left untouched, they can never match the link update
        assertFalse(nullLinkItem.isRead);
        assertFalse(emptyLinkItem.isRead);

        verify(mMockRssRepository).markItemsReadByLinks(
                Collections.singletonList("http://test.com/1"));
        verify(mMockRssChannelStateNotifier).refreshUnreadCount();
    }

    @Test
    public void execute_persistsMultipleLinks() {
        RssItem item1 = createRssItem("news 1", "http://test.com/1");
        RssItem item2 = createRssItem("news 2", "http://test.com/2");

        mMarkItemsReadOnScrollCmd.execute(Arrays.asList(item1, item2));

        verify(mMockRssRepository).markItemsReadByLinks(
                Arrays.asList("http://test.com/1", "http://test.com/2"));
        verify(mMockRssChannelStateNotifier).refreshUnreadCount();
    }

    @Test
    public void execute_emitsNothingOnRssChangeNotifier() {
        mMarkItemsReadOnScrollCmd.execute(Collections.singletonList(
                createRssItem("valid news", "http://test.com/1")));

        verifyNoInteractions(mMockRssChangeNotifier);
    }

    @Test
    public void execute_withNoValidLinks_skipsPersistenceAndNotifier() {
        RssItem nullLinkItem = createRssItem("null link news", null);
        RssItem emptyLinkItem = createRssItem("empty link news", "");

        mMarkItemsReadOnScrollCmd.execute(Arrays.asList(nullLinkItem, emptyLinkItem));

        assertFalse(nullLinkItem.isRead);
        assertFalse(emptyLinkItem.isRead);
        verifyNoInteractions(mMockRssRepository,
                mMockRssChannelStateNotifier, mMockRssChangeNotifier);
    }

    @Test
    public void execute_withNullOrEmptyList_skipsPersistenceAndNotifier() {
        mMarkItemsReadOnScrollCmd.execute(null);
        mMarkItemsReadOnScrollCmd.execute(new ArrayList<>());

        verifyNoInteractions(mMockRssRepository,
                mMockRssChannelStateNotifier, mMockRssChangeNotifier);
    }

    private RssItem createRssItem(String title, String link) {
        RssItem rssItem = new RssItem();
        rssItem.title = title;
        rssItem.link = link;
        return rssItem;
    }
}
