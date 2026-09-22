package m.co.rh.id.a_news_provider.app.provider.repository;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.ChannelUnreadCount;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.*;

/**
 * Instrumented tests for RssRepository covering persistence item-state merging,
 * favorites union and unread-count map building, against a real in-memory Room
 * database (no mocking framework - Mockito is unreliable on ART).
 * <p>
 * Two sections:
 * <ul>
 *     <li>static helper tests ({@code applyItemState}, {@code unionMissingFavorites},
 *     {@code buildUnreadCountMap}) which need no instance or database,</li>
 *     <li>{@link RssRepository#persist(RssModel)} tests which exercise the merge,
 *     state carry-over, favorites union and URL normalization behavior through real
 *     DAO rows (absorbing the former RssRepositoryPersistDuplicateTest).</li>
 * </ul>
 */
@RunWith(AndroidJUnit4.class)
public class RssRepositoryTest {

    private AppDatabase mAppDatabase;
    private RssDao mRssDao;
    private Provider mProvider;
    private RssRepository mRssRepository;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mAppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        mRssDao = mAppDatabase.rssDao();
        mProvider = Provider.createProvider(context, new RepositoryTestProviderModule(mRssDao));
        mRssRepository = mProvider.get(RssRepository.class);
    }

    @After
    public void tearDown() {
        if (mProvider != null) {
            mProvider.dispose();
        }
        if (mAppDatabase != null) {
            mAppDatabase.close();
        }
    }

    // ==================================================================================
    // Section 1: static helper applyItemState
    // ==================================================================================

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

    // ==================================================================================
    // Section 2: static helper buildUnreadCountMap
    // ==================================================================================

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

    // ==================================================================================
    // Section 3: static helper unionMissingFavorites
    // ==================================================================================

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

    // ==================================================================================
    // Section 4: persist() against the real in-memory database
    // (absorbs the former RssRepositoryPersistDuplicateTest)
    // ==================================================================================

    @Test
    public void testPersistCarriesReadAndFavoriteStateByLink() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        dbItem.isRead = true;
        dbItem.isFavorite = true;
        mRssDao.insertRssChannel(dbChannel, dbItem);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item1", "http://test.com/item1");
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertTrue("isRead should carry over by link", parsedItem.isRead);
        assertTrue("isFavorite should carry over by link", parsedItem.isFavorite);
        assertEquals("Result should carry the parsed items", 1, result.getRssItems().size());

        // database state: still a single channel, the stored item keeps the carried state
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals(1, channels.size());
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(1, storedItems.size());
        assertTrue("Stored item must keep the read state", storedItems.get(0).isRead);
        assertTrue("Stored item must keep the favorite state", storedItems.get(0).isFavorite);
    }

    @Test
    public void testPersistUnionsFavoritedItemMissingFromFeed() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        dbItem.isFavorite = true;
        mRssDao.insertRssChannel(dbChannel, dbItem);
        Long originalFavoriteId = dbItem.id;
        assertNotNull(originalFavoriteId);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", null);
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item2", "http://test.com/item2");
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        // merged list: parsed item first, then the unioned favorite; its stale id was
        // cleared and the DAO reinsert assigns the fresh id visible in the result
        assertEquals("Returned model should carry the merged list", 2, result.getRssItems().size());
        assertEquals("http://test.com/item2", result.getRssItems().get(0).link);
        assertEquals("http://test.com/item1", result.getRssItems().get(1).link);
        assertTrue("Persisted missing item should stay favorited", result.getRssItems().get(1).isFavorite);
        assertNotNull("Persisted missing item must have been reinserted",
                result.getRssItems().get(1).id);
        assertNotEquals("Reinserted favorite must get a NEW id, not the stale one",
                originalFavoriteId, result.getRssItems().get(1).id);

        // channel identity must be preserved by the merge (no duplicate row)
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals("Existing channel must be updated, not duplicated", 1, channels.size());
        assertEquals("Merge must preserve the channel id", dbChannel.id, channels.get(0).id);
        assertEquals("Merge must preserve the stored feed name", "Feed", channels.get(0).feedName);
        assertEquals("Merge must preserve createdDateTime",
                dbChannel.createdDateTime, channels.get(0).createdDateTime);

        // database state: unioned favorite was reinserted with a new id
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(2, storedItems.size());
        RssItem storedFavorite = findItemByLink(storedItems, "http://test.com/item1");
        assertNotNull(storedFavorite);
        assertTrue("Reinserted favorite must keep isFavorite", storedFavorite.isFavorite);
        assertNotEquals("Reinserted favorite must get a new id", originalFavoriteId, storedFavorite.id);
    }

    @Test
    public void testPersistDoesNotUnionNonFavoritedItemMissingFromFeed() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        dbItem.isFavorite = false;
        mRssDao.insertRssChannel(dbChannel, dbItem);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item2", "http://test.com/item2");
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertEquals("Returned model should only contain parsed items", 1, result.getRssItems().size());
        assertEquals("http://test.com/item2", result.getRssItems().get(0).link);

        // database state: the non-favorited item absent from the feed was dropped
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals(1, storedItems.size());
        assertEquals("http://test.com/item2", storedItems.get(0).link);
        assertNull("Dropped non-favorited item must not be stored",
                findItemByLink(storedItems, "http://test.com/item1"));
    }

    @Test
    public void testPersistNormalizesParsedChannelUrlForExistingChannel() {
        // legacy row stored with the scheme-prepended but un-normalized spelling
        RssChannel dbChannel = createRssChannel("https://Test.com/feed/", "Feed");
        mRssDao.insertRssChannel(dbChannel);

        RssChannel parsedChannel = createRssChannel("https://Test.com/feed/", null);
        ArrayList<RssItem> parsedItems = new ArrayList<>();

        mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertEquals("Parsed channel url should be normalized before persisting",
                "https://test.com/feed", parsedChannel.url);

        // raw-first/normalized-second variant lookup matched the legacy raw spelling,
        // and the row was rewritten to its canonical form without a duplicate
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals(1, channels.size());
        assertEquals("Existing channel url must be rewritten to the canonical spelling",
                "https://test.com/feed", channels.get(0).url);
        assertEquals("Merge must preserve the channel id", dbChannel.id, channels.get(0).id);
        assertEquals("Merge must preserve the stored feed name", "Feed", channels.get(0).feedName);
    }

    @Test
    public void testPersistInsertsNewChannelWithNormalizedUrl() {
        // merged with the former RssRepositoryPersistDuplicateTest
        // persist_rawUrlVariantWithNoExistingChannel_insertsSingleCanonicalChannel
        RssChannel parsedChannel = createRssChannel("https://example.com/feed/", "Fresh feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertEquals("New channel url should be normalized before inserting",
                "https://example.com/feed", parsedChannel.url);
        assertNotNull("Inserted channel must have a database id", result.getRssChannel().id);

        // database state: exactly one row stored with the canonical spelling
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals(1, channels.size());
        assertEquals("New channel must be stored with the canonical URL",
                "https://example.com/feed", channels.get(0).url);
        assertEquals("https://example.com/feed", result.getRssChannel().url);
    }

    @Test
    public void persistRawUrlVariantOfExistingChannel_mergesAndPreservesItemState() {
        // absorbed from the former RssRepositoryPersistDuplicateTest: a parsed feed
        // reporting the raw variant spelling of a stored canonical URL must merge
        // into that channel, not insert a duplicate row
        RssChannel canonicalChannel = createRssChannel("https://example.com/feed", "Example feed");
        RssItem readItem = createRssItem("item one", "https://example.com/feed/item-1");
        readItem.isRead = true;
        readItem.isFavorite = true;
        RssItem unreadItem = createRssItem("item two", "https://example.com/feed/item-2");
        mRssDao.insertRssChannel(canonicalChannel, readItem, unreadItem);

        // parsed feed reports the raw variant spelling of the stored canonical URL
        RssChannel parsedChannel = createRssChannel("https://example.com/feed/", "Parsed feed name");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem overlapItem = createRssItem("item one", "https://example.com/feed/item-1");
        parsedItems.add(overlapItem);
        RssItem newItem = createRssItem("item three", "https://example.com/feed/item-3");
        parsedItems.add(newItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        // merged into the existing channel - no duplicate row inserted
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals(1, channels.size());
        assertEquals("https://example.com/feed", channels.get(0).url);
        assertEquals("Stored feed name must be preserved",
                "Example feed", channels.get(0).feedName);

        // stored item state: read/favorite carried over by link, new item inserted,
        // non-favorited item absent from the feed dropped
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(2, storedItems.size());
        RssItem storedOverlapItem = findItemByLink(storedItems, "https://example.com/feed/item-1");
        assertNotNull(storedOverlapItem);
        assertTrue("Read state must carry over to the merged item", storedOverlapItem.isRead);
        assertTrue("Favorite state must carry over to the merged item", storedOverlapItem.isFavorite);
        assertNotNull("New item must be inserted",
                findItemByLink(storedItems, "https://example.com/feed/item-3"));
        assertNull("Dropped non-favorited item must not be stored",
                findItemByLink(storedItems, "https://example.com/feed/item-2"));

        assertEquals("Returned model must carry the merged items", 2, result.getRssItems().size());
    }

    private RssItem findItemByLink(List<RssItem> rssItems, String link) {
        for (RssItem rssItem : rssItems) {
            if (link.equals(rssItem.link)) {
                return rssItem;
            }
        }
        return null;
    }

    private RssChannel createRssChannel(String url, String feedName) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
        rssChannel.feedName = feedName;
        return rssChannel;
    }

    private RssItem createRssItem(String title, String link) {
        RssItem rssItem = new RssItem();
        rssItem.title = title;
        rssItem.link = link;
        return rssItem;
    }

    /**
     * Standalone test module that registers only what {@link RssRepository} needs:
     * the shared in-memory {@link RssDao} and the real {@link UrlNormalizer}.
     */
    private static class RepositoryTestProviderModule implements ProviderModule {
        private final RssDao mRssDao;

        RepositoryTestProviderModule(RssDao rssDao) {
            mRssDao = rssDao;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerAsync(RssDao.class, () -> mRssDao);
            providerRegistry.registerLazy(UrlNormalizer.class, UrlNormalizer::new);
            providerRegistry.registerLazy(RssRepository.class, () -> new RssRepository(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
