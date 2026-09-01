package m.co.rh.id.a_news_provider.app.provider.repository;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RssRepository covering persistence item-state merging,
 * favorites union and unread-count map building.
 */
public class RssRepositoryTest {

    private Provider mMockProvider;
    private RssDao mMockRssDao;
    private RssRepository mRssRepository;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockRssDao = mock(RssDao.class);
        when(mMockProvider.get(RssDao.class)).thenReturn(mMockRssDao);
        mRssRepository = new RssRepository(mMockProvider);
    }

    @Test
    public void testApplyItemStateMatchingLinksCarryOver() {
        // Create DB items with read and favorite state
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.link = "http://test.com/item1";
        dbItem1.isRead = true;
        dbItem1.isFavorite = true;
        dbItems.add(dbItem1);

        RssItem dbItem2 = new RssItem();
        dbItem2.link = "http://test.com/item2";
        dbItem2.isRead = false;
        dbItem2.isFavorite = false;
        dbItems.add(dbItem2);

        // Create parsed items
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem1 = new RssItem();
        parsedItem1.link = "http://test.com/item1";
        parsedItem1.isRead = false; // Initially false
        parsedItem1.isFavorite = false; // Initially false
        parsedItems.add(parsedItem1);

        RssItem parsedItem2 = new RssItem();
        parsedItem2.link = "http://test.com/item2";
        parsedItem2.isRead = true; // Initially true
        parsedItem2.isFavorite = true; // Initially true
        parsedItems.add(parsedItem2);

        RssItem parsedItem3 = new RssItem();
        parsedItem3.link = "http://test.com/item3";
        parsedItem3.isRead = false; // No match in DB
        parsedItem3.isFavorite = true; // No match in DB
        parsedItems.add(parsedItem3);

        // Apply item state
        RssRepository.applyItemState(dbItems, parsedItems);

        // Verify read and favorite state was carried over for matching links
        assertTrue("Item 1 should have isRead=true from DB", parsedItem1.isRead);
        assertTrue("Item 1 should have isFavorite=true from DB", parsedItem1.isFavorite);
        assertFalse("Item 2 should have isRead=false from DB", parsedItem2.isRead);
        assertFalse("Item 2 should have isFavorite=false from DB", parsedItem2.isFavorite);
        assertFalse("Item 3 should keep default isRead=false (no DB match)", parsedItem3.isRead);
        assertTrue("Item 3 should keep isFavorite=true (no DB match)", parsedItem3.isFavorite);
    }

    @Test
    public void testApplyItemStateWithNullDbItems() {
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItems.add(parsedItem);

        // Should not throw exception
        RssRepository.applyItemState(null, parsedItems);

        assertFalse("Item should keep default value", parsedItem.isRead);
    }

    @Test
    public void testApplyItemStateWithEmptyDbItems() {
        List<RssItem> dbItems = new ArrayList<>();
        
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItems.add(parsedItem);

        RssRepository.applyItemState(dbItems, parsedItems);

        assertFalse("Item should keep default value", parsedItem.isRead);
    }

    @Test
    public void testApplyItemStateWithNullParsedItems() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItems.add(dbItem);

        // Should not throw exception
        RssRepository.applyItemState(dbItems, null);
    }

    @Test
    public void testApplyItemStateWithEmptyParsedItems() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItems.add(dbItem);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssRepository.applyItemState(dbItems, parsedItems);

        // Should complete without error
    }

    @Test
    public void testApplyItemStateWithNullLinks() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.link = null; // Null link in DB
        dbItem1.isRead = true;
        dbItems.add(dbItem1);

        RssItem dbItem2 = new RssItem();
        dbItem2.link = ""; // Empty link in DB
        dbItem2.isRead = true;
        dbItems.add(dbItem2);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem1 = new RssItem();
        parsedItem1.link = null; // Null link in parsed
        parsedItem1.isRead = false;
        parsedItems.add(parsedItem1);

        RssItem parsedItem2 = new RssItem();
        parsedItem2.link = ""; // Empty link in parsed
        parsedItem2.isRead = false;
        parsedItems.add(parsedItem2);

        RssRepository.applyItemState(dbItems, parsedItems);

        // Null/empty links should not affect read state
        assertFalse("Null link should not be updated", parsedItem1.isRead);
        assertFalse("Empty link should not be updated", parsedItem2.isRead);
    }

    @Test
    public void testApplyItemStateWithDuplicateLinks() {
        // Test behavior when multiple items have the same link
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.link = "http://test.com/duplicate";
        dbItem1.isRead = true;
        dbItems.add(dbItem1);

        RssItem dbItem2 = new RssItem();
        dbItem2.link = "http://test.com/duplicate"; // Same link
        dbItem2.isRead = false;
        dbItems.add(dbItem2);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/duplicate";
        parsedItem.isRead = false;
        parsedItems.add(parsedItem);

        RssRepository.applyItemState(dbItems, parsedItems);

        // The last matching item in DB should determine the read state
        // HashMap.put() will overwrite with the last value
        assertFalse("Should take last DB value for duplicate link", parsedItem.isRead);
    }

    @Test
    public void testApplyItemStatePreservesOtherFields() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItem.isFavorite = true;
        dbItem.title = "DB Title";
        dbItem.description = "DB Description";
        dbItems.add(dbItem);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItem.isFavorite = false;
        parsedItem.title = "Parsed Title";
        parsedItem.description = "Parsed Description";
        parsedItems.add(parsedItem);

        RssRepository.applyItemState(dbItems, parsedItems);

        assertEquals("Only isRead/isFavorite should change, title should stay", "Parsed Title", parsedItem.title);
        assertEquals("Only isRead/isFavorite should change, description should stay", "Parsed Description", parsedItem.description);
        assertTrue("isRead should be updated", parsedItem.isRead);
        assertTrue("isFavorite should be updated", parsedItem.isFavorite);
    }

    @Test
    public void testApplyItemStateWithMultipleMixedCases() {
        // Test a comprehensive scenario with multiple items, some matching, some not
        List<RssItem> dbItems = new ArrayList<>();
        
        RssItem db1 = new RssItem();
        db1.link = "http://test.com/1";
        db1.isRead = true;
        dbItems.add(db1);

        RssItem db2 = new RssItem();
        db2.link = "http://test.com/2";
        db2.isRead = false;
        dbItems.add(db2);

        RssItem db3 = new RssItem();
        db3.link = "http://test.com/3";
        db3.isRead = true;
        dbItems.add(db3);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        
        RssItem p1 = new RssItem();
        p1.link = "http://test.com/1";
        p1.isRead = false;
        parsedItems.add(p1);

        RssItem p2 = new RssItem();
        p2.link = "http://test.com/2";
        p2.isRead = true;
        parsedItems.add(p2);

        RssItem p4 = new RssItem();
        p4.link = "http://test.com/4"; // Not in DB
        p4.isRead = false;
        parsedItems.add(p4);

        RssItem p3 = new RssItem();
        p3.link = "http://test.com/3";
        p3.isRead = false;
        parsedItems.add(p3);

        RssRepository.applyItemState(dbItems, parsedItems);

        assertTrue("Item 1: isRead should be true from DB", p1.isRead);
        assertFalse("Item 2: isRead should be false from DB", p2.isRead);
        assertFalse("Item 4: isRead should keep default (no DB match)", p4.isRead);
        assertTrue("Item 3: isRead should be true from DB", p3.isRead);
    }

    @Test
    public void testApplyItemStateCaseSensitiveLinks() {
        // Test if link matching is case-sensitive (it should be for URLs)
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/Item";
        dbItem.isRead = true;
        dbItems.add(dbItem);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem1 = new RssItem();
        parsedItem1.link = "http://test.com/Item"; // Exact match
        parsedItem1.isRead = false;
        parsedItems.add(parsedItem1);

        RssItem parsedItem2 = new RssItem();
        parsedItem2.link = "http://test.com/item"; // Different case
        parsedItem2.isRead = false;
        parsedItems.add(parsedItem2);

        RssRepository.applyItemState(dbItems, parsedItems);

        assertTrue("Exact case match should update isRead", parsedItem1.isRead);
        assertFalse("Different case should not match", parsedItem2.isRead);
    }

    @Test
    public void testBuildUnreadCountMapWithNullChannels() {
        List<RssChannel> channels = null;
        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertTrue("Result should be empty map", result.isEmpty());
    }

    @Test
    public void testBuildUnreadCountMapWithEmptyChannels() {
        List<RssChannel> channels = new ArrayList<>();
        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertTrue("Result should be empty map", result.isEmpty());
    }

    @Test
    public void testBuildUnreadCountMapWithChannelsNoCounts() {
        List<RssChannel> channels = new ArrayList<>();
        
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";
        channels.add(channel1);
        
        RssChannel channel2 = new RssChannel();
        channel2.id = 2L;
        channel2.feedName = "Channel 2";
        channels.add(channel2);
        
        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertEquals("Should have 2 channels", 2, result.size());
        assertEquals("Channel 1 should have 0 unread", Integer.valueOf(0), result.get(channel1));
        assertEquals("Channel 2 should have 0 unread", Integer.valueOf(0), result.get(channel2));
    }

    @Test
    public void testBuildUnreadCountMapWithCounts() {
        List<RssChannel> channels = new ArrayList<>();
        
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";
        channels.add(channel1);
        
        RssChannel channel2 = new RssChannel();
        channel2.id = 2L;
        channel2.feedName = "Channel 2";
        channels.add(channel2);
        
        RssChannel channel3 = new RssChannel();
        channel3.id = 3L;
        channel3.feedName = "Channel 3";
        channels.add(channel3);
        
        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();
        
        ChannelUnreadCount count1 = new ChannelUnreadCount();
        count1.channel_id = 1L;
        count1.cnt = 5;
        unreadCounts.add(count1);
        
        ChannelUnreadCount count2 = new ChannelUnreadCount();
        count2.channel_id = 3L;
        count2.cnt = 10;
        unreadCounts.add(count2);
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertEquals("Should have 3 channels", 3, result.size());
        assertEquals("Channel 1 should have 5 unread", Integer.valueOf(5), result.get(channel1));
        assertEquals("Channel 2 should have 0 unread", Integer.valueOf(0), result.get(channel2));
        assertEquals("Channel 3 should have 10 unread", Integer.valueOf(10), result.get(channel3));
    }

    @Test
    public void testBuildUnreadCountMapPreservesOrder() {
        List<RssChannel> channels = new ArrayList<>();
        
        RssChannel channel1 = new RssChannel();
        channel1.id = 3L;
        channel1.feedName = "Channel 3";
        channels.add(channel1);
        
        RssChannel channel2 = new RssChannel();
        channel2.id = 1L;
        channel2.feedName = "Channel 1";
        channels.add(channel2);
        
        RssChannel channel3 = new RssChannel();
        channel3.id = 2L;
        channel3.feedName = "Channel 2";
        channels.add(channel3);
        
        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();
        
        ChannelUnreadCount count1 = new ChannelUnreadCount();
        count1.channel_id = 1L;
        count1.cnt = 5;
        unreadCounts.add(count1);
        
        ChannelUnreadCount count2 = new ChannelUnreadCount();
        count2.channel_id = 2L;
        count2.cnt = 10;
        unreadCounts.add(count2);
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertTrue("Result should be LinkedHashMap", result instanceof LinkedHashMap);
        
        // Verify order is preserved
        Object[] keys = result.keySet().toArray();
        assertSame("First channel should be channel1", channel1, keys[0]);
        assertSame("Second channel should be channel2", channel2, keys[1]);
        assertSame("Third channel should be channel3", channel3, keys[2]);
    }

    @Test
    public void testBuildUnreadCountMapWithNullUnreadCounts() {
        List<RssChannel> channels = new ArrayList<>();
        
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";
        channels.add(channel1);
        
        List<ChannelUnreadCount> unreadCounts = null;
        
        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);
        
        assertEquals("Should have 1 channel", 1, result.size());
        assertEquals("Channel 1 should have 0 unread", Integer.valueOf(0), result.get(channel1));
    }

    @Test
    public void testBuildUnreadCountMapWithEmptyUnreadCounts() {
        List<RssChannel> channels = new ArrayList<>();

        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";
        channels.add(channel1);

        List<ChannelUnreadCount> unreadCounts = new ArrayList<>();

        Map<RssChannel, Integer> result = RssRepository.buildUnreadCountMap(channels, unreadCounts);

        assertEquals("Should have 1 channel", 1, result.size());
        assertEquals("Channel 1 should have 0 unread", Integer.valueOf(0), result.get(channel1));
    }

    @Test
    public void testUnionMissingFavoritesKeepsFavoritedItemAbsentFromFeed() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.id = 10L;
        dbItem1.link = "http://test.com/item1";
        dbItem1.isFavorite = true;
        dbItems.add(dbItem1);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item2";
        parsedItems.add(parsedItem);

        ArrayList<RssItem> mergedItems = RssRepository.unionMissingFavorites(dbItems, parsedItems);

        assertEquals("Merged list should contain parsed item and unioned favorite", 2, mergedItems.size());
        assertSame("Parsed item should come first", parsedItem, mergedItems.get(0));
        assertSame("Favorited DB item should be unioned", dbItem1, mergedItems.get(1));
        assertNull("Unioned favorite id should be cleared", mergedItems.get(1).id);
    }

    @Test
    public void testUnionMissingFavoritesSkipsNonFavoritedItemAbsentFromFeed() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.id = 10L;
        dbItem1.link = "http://test.com/item1";
        dbItem1.isFavorite = false;
        dbItems.add(dbItem1);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item2";
        parsedItems.add(parsedItem);

        ArrayList<RssItem> mergedItems = RssRepository.unionMissingFavorites(dbItems, parsedItems);

        assertEquals("Non-favorited DB item should not be unioned", 1, mergedItems.size());
        assertSame("Only the parsed item should remain", parsedItem, mergedItems.get(0));
    }

    @Test
    public void testUnionMissingFavoritesSkipsFavoriteStillInFeed() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.id = 10L;
        dbItem1.link = "http://test.com/item1";
        dbItem1.isFavorite = true;
        dbItems.add(dbItem1);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1"; // Same link as favorited DB item
        parsedItems.add(parsedItem);

        ArrayList<RssItem> mergedItems = RssRepository.unionMissingFavorites(dbItems, parsedItems);

        assertEquals("Favorite still in feed should not be duplicated", 1, mergedItems.size());
        assertSame(parsedItem, mergedItems.get(0));
    }

    @Test
    public void testUnionMissingFavoritesIncludesNullLinkFavorite() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.id = 10L;
        dbItem1.link = null; // Null link cannot match the feed
        dbItem1.isFavorite = true;
        dbItems.add(dbItem1);

        RssItem dbItem2 = new RssItem();
        dbItem2.id = 11L;
        dbItem2.link = ""; // Empty link cannot match the feed
        dbItem2.isFavorite = true;
        dbItems.add(dbItem2);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item2";
        parsedItems.add(parsedItem);

        ArrayList<RssItem> mergedItems = RssRepository.unionMissingFavorites(dbItems, parsedItems);

        assertEquals("Null and empty link favorites should be included", 3, mergedItems.size());
        assertNull("Null link favorite id should be cleared", mergedItems.get(1).id);
        assertNull("Empty link favorite id should be cleared", mergedItems.get(2).id);
    }

    @Test
    public void testUnionMissingFavoritesWithNullInputs() {
        ArrayList<RssItem> mergedItems = RssRepository.unionMissingFavorites(null, null);

        assertNotNull("Merged list should not be null", mergedItems);
        assertTrue("Merged list should be empty", mergedItems.isEmpty());
    }

    @Test
    public void testPersistCarriesReadAndFavoriteStateByLink() {
        RssChannel dbChannel = new RssChannel();
        dbChannel.id = 1L;
        dbChannel.url = "http://test.com/feed";
        dbChannel.feedName = "Feed";
        when(mMockRssDao.findRssChannelByUrl("http://test.com/feed")).thenReturn(dbChannel);

        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.id = 10L;
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItem.isFavorite = true;
        dbItems.add(dbItem);
        when(mMockRssDao.findRssItemsByChannelId(1L)).thenReturn(dbItems);

        RssChannel parsedChannel = new RssChannel();
        parsedChannel.url = "http://test.com/feed";
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertTrue("isRead should carry over by link", parsedItem.isRead);
        assertTrue("isFavorite should carry over by link", parsedItem.isFavorite);
        assertEquals("Result should carry the parsed items", 1, result.getRssItems().size());
    }

    @Test
    public void testPersistUnionsFavoritedItemMissingFromFeed() {
        RssChannel dbChannel = new RssChannel();
        dbChannel.id = 1L;
        dbChannel.url = "http://test.com/feed";
        dbChannel.feedName = "Feed";
        when(mMockRssDao.findRssChannelByUrl("http://test.com/feed")).thenReturn(dbChannel);

        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.id = 10L;
        dbItem.link = "http://test.com/item1";
        dbItem.isFavorite = true;
        dbItems.add(dbItem);
        when(mMockRssDao.findRssItemsByChannelId(1L)).thenReturn(dbItems);

        RssChannel parsedChannel = new RssChannel();
        parsedChannel.url = "http://test.com/feed";
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item2";
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        ArgumentCaptor<RssItem[]> rssItemsCaptor = ArgumentCaptor.forClass(RssItem[].class);
        verify(mMockRssDao).updateRssChannel(eq(parsedChannel), rssItemsCaptor.capture());

        RssItem[] persistedItems = rssItemsCaptor.getValue();
        assertEquals("Favorited item absent from feed should be persisted", 2, persistedItems.length);
        assertEquals("http://test.com/item2", persistedItems[0].link);
        assertEquals("http://test.com/item1", persistedItems[1].link);
        assertTrue("Persisted missing item should stay favorited", persistedItems[1].isFavorite);
        assertNull("Persisted missing item id should be null", persistedItems[1].id);

        assertEquals("Returned model should carry the merged list", 2, result.getRssItems().size());
    }

    @Test
    public void testPersistDoesNotUnionNonFavoritedItemMissingFromFeed() {
        RssChannel dbChannel = new RssChannel();
        dbChannel.id = 1L;
        dbChannel.url = "http://test.com/feed";
        dbChannel.feedName = "Feed";
        when(mMockRssDao.findRssChannelByUrl("http://test.com/feed")).thenReturn(dbChannel);

        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.id = 10L;
        dbItem.link = "http://test.com/item1";
        dbItem.isFavorite = false;
        dbItems.add(dbItem);
        when(mMockRssDao.findRssItemsByChannelId(1L)).thenReturn(dbItems);

        RssChannel parsedChannel = new RssChannel();
        parsedChannel.url = "http://test.com/feed";
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item2";
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        ArgumentCaptor<RssItem[]> rssItemsCaptor = ArgumentCaptor.forClass(RssItem[].class);
        verify(mMockRssDao).updateRssChannel(eq(parsedChannel), rssItemsCaptor.capture());

        RssItem[] persistedItems = rssItemsCaptor.getValue();
        assertEquals("Non-favorited item absent from feed should not be persisted", 1, persistedItems.length);
        assertEquals("http://test.com/item2", persistedItems[0].link);

        assertEquals("Returned model should only contain parsed items", 1, result.getRssItems().size());
    }
}
