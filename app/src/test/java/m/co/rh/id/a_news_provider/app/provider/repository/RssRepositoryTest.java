package m.co.rh.id.a_news_provider.app.provider.repository;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;

import static org.junit.Assert.*;

/**
 * Unit tests for RssRepository covering persistence read-state merging and unread-count map building.
 */
public class RssRepositoryTest {

    @Test
    public void testApplyReadStateMatchingLinksCarryOver() {
        // Create DB items with read state
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem1 = new RssItem();
        dbItem1.link = "http://test.com/item1";
        dbItem1.isRead = true;
        dbItems.add(dbItem1);

        RssItem dbItem2 = new RssItem();
        dbItem2.link = "http://test.com/item2";
        dbItem2.isRead = false;
        dbItems.add(dbItem2);

        // Create parsed items
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem1 = new RssItem();
        parsedItem1.link = "http://test.com/item1";
        parsedItem1.isRead = false; // Initially false
        parsedItems.add(parsedItem1);

        RssItem parsedItem2 = new RssItem();
        parsedItem2.link = "http://test.com/item2";
        parsedItem2.isRead = true; // Initially true
        parsedItems.add(parsedItem2);

        RssItem parsedItem3 = new RssItem();
        parsedItem3.link = "http://test.com/item3";
        parsedItem3.isRead = false; // No match in DB
        parsedItems.add(parsedItem3);

        // Apply read state
        RssRepository.applyReadState(dbItems, parsedItems);

        // Verify read state was carried over for matching links
        assertTrue("Item 1 should have isRead=true from DB", parsedItem1.isRead);
        assertFalse("Item 2 should have isRead=false from DB", parsedItem2.isRead);
        assertFalse("Item 3 should keep default isRead=false (no DB match)", parsedItem3.isRead);
    }

    @Test
    public void testApplyReadStateWithNullDbItems() {
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItems.add(parsedItem);

        // Should not throw exception
        RssRepository.applyReadState(null, parsedItems);

        assertFalse("Item should keep default value", parsedItem.isRead);
    }

    @Test
    public void testApplyReadStateWithEmptyDbItems() {
        List<RssItem> dbItems = new ArrayList<>();
        
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItems.add(parsedItem);

        RssRepository.applyReadState(dbItems, parsedItems);

        assertFalse("Item should keep default value", parsedItem.isRead);
    }

    @Test
    public void testApplyReadStateWithNullParsedItems() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItems.add(dbItem);

        // Should not throw exception
        RssRepository.applyReadState(dbItems, null);
    }

    @Test
    public void testApplyReadStateWithEmptyParsedItems() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItems.add(dbItem);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssRepository.applyReadState(dbItems, parsedItems);

        // Should complete without error
    }

    @Test
    public void testApplyReadStateWithNullLinks() {
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

        RssRepository.applyReadState(dbItems, parsedItems);

        // Null/empty links should not affect read state
        assertFalse("Null link should not be updated", parsedItem1.isRead);
        assertFalse("Empty link should not be updated", parsedItem2.isRead);
    }

    @Test
    public void testApplyReadStateWithDuplicateLinks() {
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

        RssRepository.applyReadState(dbItems, parsedItems);

        // The last matching item in DB should determine the read state
        // HashMap.put() will overwrite with the last value
        assertFalse("Should take last DB value for duplicate link", parsedItem.isRead);
    }

    @Test
    public void testApplyReadStatePreservesOtherFields() {
        List<RssItem> dbItems = new ArrayList<>();
        RssItem dbItem = new RssItem();
        dbItem.link = "http://test.com/item1";
        dbItem.isRead = true;
        dbItem.title = "DB Title";
        dbItem.description = "DB Description";
        dbItems.add(dbItem);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = new RssItem();
        parsedItem.link = "http://test.com/item1";
        parsedItem.isRead = false;
        parsedItem.title = "Parsed Title";
        parsedItem.description = "Parsed Description";
        parsedItems.add(parsedItem);

        RssRepository.applyReadState(dbItems, parsedItems);

        assertEquals("Only isRead should change, title should stay", "Parsed Title", parsedItem.title);
        assertEquals("Only isRead should change, description should stay", "Parsed Description", parsedItem.description);
        assertTrue("isRead should be updated", parsedItem.isRead);
    }

    @Test
    public void testApplyReadStateWithMultipleMixedCases() {
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

        RssRepository.applyReadState(dbItems, parsedItems);

        assertTrue("Item 1: isRead should be true from DB", p1.isRead);
        assertFalse("Item 2: isRead should be false from DB", p2.isRead);
        assertFalse("Item 4: isRead should keep default (no DB match)", p4.isRead);
        assertTrue("Item 3: isRead should be true from DB", p3.isRead);
    }

    @Test
    public void testApplyReadStateCaseSensitiveLinks() {
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

        RssRepository.applyReadState(dbItems, parsedItems);

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
}
