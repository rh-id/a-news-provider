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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
 * Instrumented tests for RssRepository covering persistence item retention,
 * unread-count map building and the auto mark-read retention window, against a
 * real in-memory Room database (no mocking framework - Mockito is unreliable on ART).
 * <p>
 * Main sections:
 * <ul>
 *     <li>static helper tests ({@code buildUnreadCountMap}) which need no instance or
 *     database,</li>
 *     <li>{@link RssRepository#persist(RssModel)} tests which exercise the upsert
 *     retention merge (issue #37): state carry-over, original-id retention of items
 *     missing from the feed, feed-field refresh and URL normalization behavior
 *     through real DAO rows (absorbing the former RssRepositoryPersistDuplicateTest),</li>
 *     <li>{@link RssRepository#markOldItemsRead(long)} tests for the unread retention
 *     window (cutoff boundary, favorite immunity, cross-channel link consistency,
 *     null/empty-link exclusion, paused-channel aging),</li>
 *     <li>neighbor navigation queries (next/previous article lookups) and keyset
 *     (cursor) pagination walks, including tied-sort-key page boundaries.</li>
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
    // Section 1: static helper buildUnreadCountMap
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
    // Section 2: persist() against the real in-memory database
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

        // the upsert updates the stored row in place; the returned model exposes the
        // authoritative persisted row with the carried-over state (the parsed object
        // is discarded)
        assertEquals("Result should carry the matched row", 1, result.getRssItems().size());
        assertTrue("isRead should carry over by link", result.getRssItems().get(0).isRead);
        assertTrue("isFavorite should carry over by link", result.getRssItems().get(0).isFavorite);
        assertEquals("Matched row keeps its id", dbItem.id, result.getRssItems().get(0).id);

        // database state: still a single channel, the stored item keeps the carried state
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals(1, channels.size());
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(1, storedItems.size());
        assertTrue("Stored item must keep the read state", storedItems.get(0).isRead);
        assertTrue("Stored item must keep the favorite state", storedItems.get(0).isFavorite);
    }

    @Test
    public void testPersistRetainsFavoritedItemMissingFromFeedWithOriginalId() {
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

        // merged list: parsed item first, then the retained favorite with its
        // ORIGINAL id - upsert retention keeps the row (no id-clearing reinsert)
        assertEquals("Returned model should carry the merged list", 2, result.getRssItems().size());
        assertEquals("http://test.com/item2", result.getRssItems().get(0).link);
        assertEquals("http://test.com/item1", result.getRssItems().get(1).link);
        assertTrue("Retained missing item should stay favorited", result.getRssItems().get(1).isFavorite);
        assertEquals("Retained favorite keeps its original id",
                originalFavoriteId, result.getRssItems().get(1).id);

        // channel identity must be preserved by the merge (no duplicate row)
        List<RssChannel> channels = mRssDao.loadAllRssChannel();
        assertEquals("Existing channel must be updated, not duplicated", 1, channels.size());
        assertEquals("Merge must preserve the channel id", dbChannel.id, channels.get(0).id);
        assertEquals("Merge must preserve the stored feed name", "Feed", channels.get(0).feedName);
        assertEquals("Merge must preserve createdDateTime",
                dbChannel.createdDateTime, channels.get(0).createdDateTime);

        // database state: the favorite row is retained untouched with its original id
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(2, storedItems.size());
        RssItem storedFavorite = findItemByLink(storedItems, "http://test.com/item1");
        assertNotNull(storedFavorite);
        assertTrue("Retained favorite must keep isFavorite", storedFavorite.isFavorite);
        assertEquals("Retained favorite must keep its original id",
                originalFavoriteId, storedFavorite.id);
    }

    @Test
    public void testPersistRetainsNonFavoritedItemMissingFromFeed() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        dbItem.isFavorite = false;
        mRssDao.insertRssChannel(dbChannel, dbItem);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item2", "http://test.com/item2");
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        // returned model = parsed item first, then the retained row
        assertEquals("Returned model should contain parsed + retained items",
                2, result.getRssItems().size());
        assertEquals("http://test.com/item2", result.getRssItems().get(0).link);
        assertEquals("http://test.com/item1", result.getRssItems().get(1).link);
        assertEquals("Retained row keeps its original id",
                dbItem.id, result.getRssItems().get(1).id);

        // database state: the non-favorited item absent from the feed is RETAINED
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals(2, storedItems.size());
        RssItem retainedItem = findItemByLink(storedItems, "http://test.com/item1");
        assertNotNull("Non-favorited item missing from the feed must be retained", retainedItem);
        assertEquals("Retained item keeps its original id", dbItem.id, retainedItem.id);
    }

    @Test
    public void testPersistRefreshesFeedFieldsAndPreservesStableFields() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("old title", "http://test.com/item1");
        dbItem.description = "old description";
        dbItem.categories = "Stale";
        dbItem.pubDate = new Date(1000L);
        dbItem.isRead = true;
        dbItem.isFavorite = true;
        mRssDao.insertRssChannel(dbChannel, dbItem);
        Long originalId = dbItem.id;
        Date originalCreated = dbItem.createdDateTime;
        Date originalUpdated = dbItem.updatedDateTime;
        assertNotNull(originalId);
        assertNotNull(originalCreated);
        assertNotNull(originalUpdated);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("new title", "http://test.com/item1");
        parsedItem.description = "new description";
        parsedItem.categories = "Fresh\u001FTech";
        parsedItem.pubDate = new Date(2000L);
        parsedItems.add(parsedItem);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals("Matched item must be updated in place, not duplicated", 1, storedItems.size());
        RssItem stored = storedItems.get(0);
        assertEquals("Title must be refreshed from the feed", "new title", stored.title);
        assertEquals("Description must be refreshed from the feed", "new description", stored.description);
        assertEquals("Categories must be refreshed from the feed", "Fresh\u001FTech", stored.categories);
        assertEquals("Pub date must be refreshed from the feed", 2000L, stored.pubDate.getTime());
        assertEquals("Id must be preserved", originalId, stored.id);
        assertTrue("Read state must be preserved", stored.isRead);
        assertTrue("Favorite state must be preserved", stored.isFavorite);
        assertEquals("createdDateTime must be preserved", originalCreated, stored.createdDateTime);
        assertNotNull("updatedDateTime must stay set", stored.updatedDateTime);
        assertFalse("updatedDateTime must not be rolled back",
                stored.updatedDateTime.before(originalUpdated));

        // the returned model exposes the persisted row, not the parsed object
        assertEquals(1, result.getRssItems().size());
        assertEquals("Merged model carries the persisted row's id",
                originalId, result.getRssItems().get(0).id);
        assertEquals("new title", result.getRssItems().get(0).title);
    }

    @Test
    public void testPersistReturnedModelIsParsedOrderThenRetained() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem retained = createRssItem("retained", "http://test.com/retained");
        mRssDao.insertRssChannel(dbChannel, retained);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        parsedItems.add(createRssItem("first", "http://test.com/first"));
        parsedItems.add(createRssItem("second", "http://test.com/second"));
        // the item matching the stored row comes LAST in feed order to prove the
        // merged list keeps parsed order before appending retained rows
        RssItem matched = createRssItem("retained", "http://test.com/retained");
        parsedItems.add(matched);

        RssModel result = mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        assertEquals(3, result.getRssItems().size());
        assertEquals("http://test.com/first", result.getRssItems().get(0).link);
        assertEquals("http://test.com/second", result.getRssItems().get(1).link);
        assertEquals("http://test.com/retained", result.getRssItems().get(2).link);
        assertEquals("Matched row keeps its original id", retained.id, result.getRssItems().get(2).id);
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals(3, storedItems.size());
    }

    @Test
    public void testPersistLinklessItemUpsertedNotDuplicated() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(dbChannel);

        ArrayList<RssItem> firstBatch = new ArrayList<>();
        RssItem linkless = createRssItem("linkless item", null);
        linkless.pubDate = new Date(1700000000000L);
        firstBatch.add(linkless);
        mRssRepository.persist(new RssModel(
                createRssChannel("http://test.com/feed", "Feed"), firstBatch));

        List<RssItem> afterFirst = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals("First persist inserts the linkless item", 1, afterFirst.size());
        Long firstId = afterFirst.get(0).id;
        assertNotNull(firstId);

        // second sync carries the same linkless item again (the parser emits "" for
        // RSS items without <link> and null for Atom entries) - the null-safe
        // (title, pubDate) tuple key must match the stored row
        ArrayList<RssItem> secondBatch = new ArrayList<>();
        RssItem linklessAgain = createRssItem("linkless item", "");
        linklessAgain.pubDate = new Date(1700000000000L);
        secondBatch.add(linklessAgain);
        RssModel result = mRssRepository.persist(new RssModel(
                createRssChannel("http://test.com/feed", "Feed"), secondBatch));

        List<RssItem> afterSecond = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals("Second persist must update in place, not duplicate", 1, afterSecond.size());
        assertEquals("Same row keeps its original id", firstId, afterSecond.get(0).id);
        assertEquals(1, result.getRssItems().size());
    }

    @Test
    public void testPersistLinklessFavoriteNotDuplicated() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(dbChannel);

        ArrayList<RssItem> firstBatch = new ArrayList<>();
        RssItem linklessFavorite = createRssItem("linkless favorite", null);
        linklessFavorite.isFavorite = true;
        firstBatch.add(linklessFavorite);
        mRssRepository.persist(new RssModel(
                createRssChannel("http://test.com/feed", "Feed"), firstBatch));

        List<RssItem> afterFirst = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals(1, afterFirst.size());
        Long favoriteId = afterFirst.get(0).id;
        assertTrue(afterFirst.get(0).isFavorite);

        // later syncs no longer mark the item favorite (parsed state is plain) - the
        // stored favorite must be matched by the tuple key and updated in place,
        // never duplicated (the old merge reinserted linkless favorites with a fresh
        // id on EVERY sync)
        ArrayList<RssItem> secondBatch = new ArrayList<>();
        RssItem linklessAgain = createRssItem("linkless favorite", null);
        secondBatch.add(linklessAgain);
        RssModel result = mRssRepository.persist(new RssModel(
                createRssChannel("http://test.com/feed", "Feed"), secondBatch));

        List<RssItem> afterSecond = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals("Linkless favorite must not duplicate", 1, afterSecond.size());
        assertEquals("Row identity is stable", favoriteId, afterSecond.get(0).id);
        assertTrue("Favorite state must be preserved", afterSecond.get(0).isFavorite);
        assertEquals(1, result.getRssItems().size());
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
        // non-favorited item absent from the feed retained (issue #37)
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channels.get(0).id);
        assertEquals(3, storedItems.size());
        RssItem storedOverlapItem = findItemByLink(storedItems, "https://example.com/feed/item-1");
        assertNotNull(storedOverlapItem);
        assertTrue("Read state must carry over to the merged item", storedOverlapItem.isRead);
        assertTrue("Favorite state must carry over to the merged item", storedOverlapItem.isFavorite);
        assertNotNull("New item must be inserted",
                findItemByLink(storedItems, "https://example.com/feed/item-3"));
        assertNotNull("Item absent from the feed must be retained",
                findItemByLink(storedItems, "https://example.com/feed/item-2"));

        assertEquals("Returned model must carry the merged items", 3, result.getRssItems().size());
    }

    // ==================================================================================
    // Section 3: markOldItemsRead (unread retention window)
    // ==================================================================================

    @Test
    public void testMarkOldItemsReadCutoffBoundary() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long cutoff = System.currentTimeMillis();
        RssItem atCutoff = createRssItem("at cutoff", "http://test.com/at-cutoff");
        atCutoff.pubDate = new Date(cutoff); // exactly AT the cutoff, must NOT age
        RssItem older = createRssItem("older", "http://test.com/older");
        older.pubDate = new Date(cutoff - 1); // strictly older, must age
        mRssDao.insertRssChannel(channel, atCutoff, older);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Only the strictly-older item must be updated", 1, rowsUpdated);
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channel.id);
        assertFalse("Item exactly at the cutoff must stay unread",
                findItemByLink(storedItems, "http://test.com/at-cutoff").isRead);
        assertTrue("Item older than the cutoff must be marked read",
                findItemByLink(storedItems, "http://test.com/older").isRead);
    }

    @Test
    public void testMarkOldItemsReadFavoriteImmunity() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long cutoff = System.currentTimeMillis();
        RssItem oldFavorite = createRssItem("old favorite", "http://test.com/fav");
        oldFavorite.pubDate = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        oldFavorite.isFavorite = true;
        RssItem oldUnread = createRssItem("old unread", "http://test.com/unread");
        oldUnread.pubDate = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        mRssDao.insertRssChannel(channel, oldFavorite, oldUnread);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Only the non-favorite item must be updated", 1, rowsUpdated);
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channel.id);
        assertFalse("Old favorite must stay unread",
                findItemByLink(storedItems, "http://test.com/fav").isRead);
        assertTrue("Old non-favorite item must be marked read",
                findItemByLink(storedItems, "http://test.com/unread").isRead);
    }

    @Test
    public void testMarkOldItemsReadCrossChannelDuplicatesStayConsistent() {
        RssChannel oldChannel = createRssChannel("http://test.com/feed-old", "Old feed");
        RssChannel newChannel = createRssChannel("http://test.com/feed-new", "New feed");
        long cutoff = System.currentTimeMillis();
        RssItem oldCopy = createRssItem("dup", "http://test.com/dup");
        oldCopy.pubDate = new Date(cutoff - TimeUnit.DAYS.toMillis(30)); // old copy
        RssItem freshCopy = createRssItem("dup", "http://test.com/dup");
        freshCopy.pubDate = new Date(cutoff); // recent republish, not old itself
        mRssDao.insertRssChannel(oldChannel, oldCopy);
        mRssDao.insertRssChannel(newChannel, freshCopy);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Both copies of the same link must be marked read", 2, rowsUpdated);
        List<RssItem> duplicates = mRssDao.findRssItemsByLink("http://test.com/dup");
        assertEquals(2, duplicates.size());
        for (RssItem rssItem : duplicates) {
            assertTrue("Cross-channel duplicate must be marked read", rssItem.isRead);
        }
    }

    @Test
    public void testMarkOldItemsReadSkipsNullAndEmptyLinks() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long cutoff = System.currentTimeMillis();
        RssItem nullLink = createRssItem("null link", null);
        nullLink.createdDateTime = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        RssItem emptyLink = createRssItem("empty link", "");
        emptyLink.createdDateTime = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        mRssDao.insertRssChannel(channel, nullLink, emptyLink);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Null/empty link rows must never be aged", 0, rowsUpdated);
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(channel.id);
        assertEquals(2, storedItems.size());
        for (RssItem rssItem : storedItems) {
            assertFalse("Null/empty link item must stay unread", rssItem.isRead);
        }
    }

    @Test
    public void testMarkOldItemsReadAgesPausedChannelItems() {
        RssChannel pausedChannel = createRssChannel("http://test.com/feed", "Paused feed");
        pausedChannel.isPaused = true;
        long cutoff = System.currentTimeMillis();
        RssItem oldItem = createRssItem("old", "http://test.com/old");
        oldItem.createdDateTime = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        mRssDao.insertRssChannel(pausedChannel, oldItem);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Paused channels are aged too", 1, rowsUpdated);
        assertTrue("Old item of the paused channel must be marked read",
                mRssDao.findRssItemsByChannelId(pausedChannel.id).get(0).isRead);
    }

    @Test
    public void testMarkOldItemsReadLeavesAlreadyReadItemsUntouched() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long cutoff = System.currentTimeMillis();
        RssItem oldRead = createRssItem("old read", "http://test.com/read");
        oldRead.createdDateTime = new Date(cutoff - TimeUnit.DAYS.toMillis(90));
        oldRead.isRead = true;
        mRssDao.insertRssChannel(channel, oldRead);

        int rowsUpdated = mRssRepository.markOldItemsRead(cutoff);

        assertEquals("Already-read rows must not be counted", 0, rowsUpdated);
    }

    // ==================================================================================
    // Section 4: item categories (feed-provided, joined with the unit separator)
    // ==================================================================================

    @Test
    public void testPersistStoresCategoriesThroughUpsert() {
        // categories refresh from the feed on every sync: the upsert overwrites the
        // feed-provided fields of the matched row, and unlike isRead/isFavorite
        // nothing about categories is preserved - the parsed feed's categories are
        // the stored ones
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        mRssDao.insertRssChannel(dbChannel, dbItem);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item1", "http://test.com/item1");
        parsedItem.categories = "Tech\u001FNews";
        parsedItems.add(parsedItem);

        mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals("Item must be updated in place", 1, storedItems.size());
        assertEquals("Categories must ride the upsert path",
                "Tech\u001FNews", storedItems.get(0).categories);
        assertEquals("Upsert must preserve the row id", dbItem.id, storedItems.get(0).id);
    }

    @Test
    public void testPersistReplacesCategoriesOnNextSync() {
        RssChannel dbChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dbItem = createRssItem("item1", "http://test.com/item1");
        dbItem.categories = "Stale";
        mRssDao.insertRssChannel(dbChannel, dbItem);

        RssChannel parsedChannel = createRssChannel("http://test.com/feed", "Feed");
        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsedItem = createRssItem("item1", "http://test.com/item1");
        parsedItem.categories = "Fresh\u001FTech";
        parsedItems.add(parsedItem);

        mRssRepository.persist(new RssModel(parsedChannel, parsedItems));

        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(dbChannel.id);
        assertEquals(1, storedItems.size());
        assertEquals("Stale categories must be replaced by the feed's terms",
                "Fresh\u001FTech", storedItems.get(0).categories);
    }

    @Test
    public void testSearchRssItemsMatchesCategoriesIncludingLikeWildcards() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        RssItem plainItem = createRssItem("plain title", "http://test.com/plain");
        RssItem categorizedItem = createRssItem("markets", "http://test.com/markets");
        categorizedItem.categories = "50%_growth\u001FTech";
        mRssDao.insertRssChannel(channel, plainItem, categorizedItem);

        // substring match against the categories column only
        List<RssItem> byCategory = mRssDao.searchRssItemsPageDesc(
                "growth", null, null, null, null, null, 10);
        assertEquals(1, byCategory.size());
        assertEquals("http://test.com/markets", byCategory.get(0).link);

        // a chip-tapped term containing % and _ arrives pre-escaped and must stay
        // literal (match exactly, not act as wildcards)
        List<RssItem> byEscapedCategory = mRssDao.searchRssItemsPageDesc(
                "50\\%\\_growth", null, null, null, null, null, 10);
        assertEquals(1, byEscapedCategory.size());
        assertEquals("http://test.com/markets", byEscapedCategory.get(0).link);

        List<RssItem> ascByCategory = mRssDao.searchRssItemsPageAsc(
                "50\\%\\_growth", null, null, null, null, null, 10);
        assertEquals(1, ascByCategory.size());
        assertEquals("http://test.com/markets", ascByCategory.get(0).link);
    }

    // ==================================================================================
    // Section 5: neighbor navigation (next/previous article lookups)
    // ==================================================================================

    @Test
    public void testFindNeighborWalksNewestFirstOrderAndStopsAtEdges() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long base = System.currentTimeMillis();
        RssItem oldest = createRssItem("oldest", "http://test.com/oldest");
        oldest.pubDate = new Date(base - 2000);
        RssItem middle = createRssItem("middle", "http://test.com/middle");
        middle.pubDate = new Date(base - 1000);
        RssItem newest = createRssItem("newest", "http://test.com/newest");
        newest.pubDate = new Date(base);
        mRssDao.insertRssChannel(channel, oldest, middle, newest);

        // walking the newest-first list order: the older neighbor of the middle
        // item is the oldest one
        RssItem older = mRssDao.findOlderRssItem(null, null, null,
                middle.pubDate.getTime(), middle.id);
        assertNotNull(older);
        assertEquals("http://test.com/oldest", older.link);

        // and the newer neighbor of the middle item is the newest one
        RssItem newer = mRssDao.findNewerRssItem(null, null, null,
                middle.pubDate.getTime(), middle.id);
        assertNotNull(newer);
        assertEquals("http://test.com/newest", newer.link);

        // edges: nothing exists beyond the oldest / newest item
        assertNull(mRssDao.findOlderRssItem(null, null, null,
                oldest.pubDate.getTime(), oldest.id));
        assertNull(mRssDao.findNewerRssItem(null, null, null,
                newest.pubDate.getTime(), newest.id));
    }

    @Test
    public void testFindNeighborAnchorNeverMatchesItself() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        RssItem item = createRssItem("item", "http://test.com/item");
        item.pubDate = new Date(System.currentTimeMillis());
        mRssDao.insertRssChannel(channel, item);

        // the strict compound comparison must exclude the anchor row itself,
        // even when it is the only row in the table
        assertNull(mRssDao.findOlderRssItem(null, null, null,
                item.pubDate.getTime(), item.id));
        assertNull(mRssDao.findNewerRssItem(null, null, null,
                item.pubDate.getTime(), item.id));
    }

    @Test
    public void testFindNeighborFallsBackToCreatedDateTimeWhenPubDateNull() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long base = System.currentTimeMillis();
        RssItem older = createRssItem("older", "http://test.com/older");
        older.createdDateTime = new Date(base - 1000); // no pubDate: created date orders
        RssItem newer = createRssItem("newer", "http://test.com/newer");
        newer.createdDateTime = new Date(base);
        mRssDao.insertRssChannel(channel, older, newer);

        // COALESCE(pub_date, created_date_time) falls back to created_date_time,
        // matching the home list ordering for items without a publication date
        RssItem newerOfOlder = mRssDao.findNewerRssItem(null, null, null,
                older.createdDateTime.getTime(), older.id);
        assertNotNull(newerOfOlder);
        assertEquals("http://test.com/newer", newerOfOlder.link);

        RssItem olderOfNewer = mRssDao.findOlderRssItem(null, null, null,
                newer.createdDateTime.getTime(), newer.id);
        assertNotNull(olderOfNewer);
        assertEquals("http://test.com/older", olderOfNewer.link);
    }

    @Test
    public void testFindNeighborUsesIdAsTiebreaker() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long base = System.currentTimeMillis();
        RssItem first = createRssItem("first", "http://test.com/first");
        first.pubDate = new Date(base);
        // deliberately conflicting created dates: the created_date_time order
        // here CONTRADICTS the id (insert) order, so the assertions below prove
        // id - not created_date_time - decides the neighbor order
        first.createdDateTime = new Date(base + 5000);
        RssItem second = createRssItem("second", "http://test.com/second");
        second.pubDate = new Date(base); // same pub date as first
        second.createdDateTime = new Date(base);
        mRssDao.insertRssChannel(channel, first, second);

        // equal primary sort key: the unique id tiebreaker decides the order,
        // exactly as the list queries' secondary ORDER BY does
        RssItem newer = mRssDao.findNewerRssItem(null, null, null,
                first.pubDate.getTime(), first.id);
        assertNotNull(newer);
        assertEquals("http://test.com/second", newer.link);

        RssItem older = mRssDao.findOlderRssItem(null, null, null,
                second.pubDate.getTime(), second.id);
        assertNotNull(older);
        assertEquals("http://test.com/first", older.link);
    }

    @Test
    public void testFindNeighborRespectsChannelAndStateFilters() {
        RssChannel channelA = createRssChannel("http://test.com/feed-a", "Feed A");
        RssChannel channelB = createRssChannel("http://test.com/feed-b", "Feed B");
        long base = System.currentTimeMillis();
        RssItem aOld = createRssItem("a old", "http://test.com/a-old");
        aOld.pubDate = new Date(base - 2000);
        aOld.isFavorite = true;
        RssItem aMiddle = createRssItem("a middle", "http://test.com/a-middle");
        aMiddle.pubDate = new Date(base - 1000);
        RssItem aNewRead = createRssItem("a new read", "http://test.com/a-new-read");
        aNewRead.pubDate = new Date(base);
        aNewRead.isRead = true;
        mRssDao.insertRssChannel(channelA, aOld, aMiddle, aNewRead);
        RssItem bNewest = createRssItem("b newest", "http://test.com/b-newest");
        bNewest.pubDate = new Date(base + 1000);
        mRssDao.insertRssChannel(channelB, bNewest);

        // the channel filter must exclude the newer item of channel B
        RssItem older = mRssDao.findOlderRssItem(channelA.id, null, null,
                aMiddle.pubDate.getTime(), aMiddle.id);
        assertNotNull(older);
        assertEquals("http://test.com/a-old", older.link);

        // unread-only home list: the newer read item must be filtered out
        RssItem newerUnread = mRssDao.findNewerRssItem(channelA.id, 0, null,
                aMiddle.pubDate.getTime(), aMiddle.id);
        assertNull("Read newer item must be filtered out", newerUnread);
        RssItem olderUnread = mRssDao.findOlderRssItem(channelA.id, 0, null,
                aMiddle.pubDate.getTime(), aMiddle.id);
        assertNotNull(olderUnread);
        assertEquals("http://test.com/a-old", olderUnread.link);

        // favorites-only home list: only the favorited neighbor remains
        RssItem olderFavorite = mRssDao.findOlderRssItem(channelA.id, null, 1,
                aMiddle.pubDate.getTime(), aMiddle.id);
        assertNotNull(olderFavorite);
        assertEquals("http://test.com/a-old", olderFavorite.link);
    }

    // ==================================================================================
    // Section 6: keyset (cursor) pagination
    // ==================================================================================

    /**
     * Walks the keyset pages of the given query direction, collecting every row
     * exactly once. Page size 3 against 10/7 rows forces three page boundaries,
     * including boundaries inside a block of identical sort keys where a
     * non-unique tiebreaker would skip or duplicate rows.
     */
    private List<RssItem> walkKeysetPages(KeysetPageLoader loader) {
        List<RssItem> all = new ArrayList<>();
        Long cursorSortKey = null;
        Long cursorId = null;
        while (true) {
            List<RssItem> page = loader.load(cursorSortKey, cursorId);
            if (page.isEmpty()) {
                break;
            }
            all.addAll(page);
            RssItem last = page.get(page.size() - 1);
            cursorSortKey = last.pubDate != null
                    ? last.pubDate.getTime() : last.createdDateTime.getTime();
            cursorId = last.id;
        }
        return all;
    }

    private interface KeysetPageLoader {
        List<RssItem> load(Long cursorSortKey, Long cursorId);
    }

    @Test
    public void testKeysetPaginationWalksAllRowsWithTiedPubDates() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        Date sameDate = new Date(System.currentTimeMillis());
        List<RssItem> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RssItem item = createRssItem("item" + i, "http://test.com/" + i);
            item.pubDate = sameDate; // ALL rows share the primary sort key
            items.add(item);
        }
        mRssDao.insertRssChannel(channel, items.toArray(new RssItem[0]));

        // newest first: 10 rows in pages of 3 - every boundary falls inside the
        // identical-date block, so the id tiebreaker carries the cursor
        List<RssItem> descWalk = walkKeysetPages((cursorSortKey, cursorId) ->
                mRssDao.findRssItemsPageDesc(null, null, null, cursorSortKey, cursorId, 3));
        assertEquals("no row may be skipped or duplicated across boundaries",
                10, descWalk.size());
        for (int i = 0; i < descWalk.size(); i++) {
            assertEquals("http://test.com/" + (9 - i), descWalk.get(i).link);
            if (i > 0) {
                assertTrue("DESC list must have strictly decreasing ids",
                        descWalk.get(i - 1).id > descWalk.get(i).id);
            }
        }

        // oldest first: same rows in reverse
        List<RssItem> ascWalk = walkKeysetPages((cursorSortKey, cursorId) ->
                mRssDao.findRssItemsPageAsc(null, null, null, cursorSortKey, cursorId, 3));
        assertEquals(10, ascWalk.size());
        for (int i = 0; i < ascWalk.size(); i++) {
            assertEquals("http://test.com/" + i, ascWalk.get(i).link);
            if (i > 0) {
                assertTrue("ASC list must have strictly increasing ids",
                        ascWalk.get(i - 1).id < ascWalk.get(i).id);
            }
        }
    }

    @Test
    public void testKeysetListOrderMatchesNeighborNavigationOrder() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        long base = System.currentTimeMillis();
        RssItem a = createRssItem("a", "http://test.com/a");
        a.pubDate = new Date(base);
        RssItem b = createRssItem("b", "http://test.com/b");
        b.pubDate = new Date(base); // ties a and c on the primary sort key
        RssItem c = createRssItem("c", "http://test.com/c");
        c.pubDate = new Date(base);
        RssItem d = createRssItem("d", "http://test.com/d");
        d.pubDate = new Date(base - 1000);
        RssItem e = createRssItem("e", "http://test.com/e"); // no pubDate: created date orders
        e.createdDateTime = new Date(base - 2000);
        mRssDao.insertRssChannel(channel, a, b, c, d, e);

        // the DESC list, walked in single steps via findOlderRssItem from the
        // first row, must reproduce the keyset-paginated list exactly
        List<RssItem> pagedList = walkKeysetPages((cursorSortKey, cursorId) ->
                mRssDao.findRssItemsPageDesc(null, null, null, cursorSortKey, cursorId, 2));
        assertEquals(5, pagedList.size());

        List<RssItem> neighborWalk = new ArrayList<>();
        RssItem cursor = pagedList.get(0);
        neighborWalk.add(cursor);
        while (true) {
            RssItem older = mRssDao.findOlderRssItem(null, null, null,
                    cursor.pubDate != null ? cursor.pubDate.getTime()
                            : cursor.createdDateTime.getTime(),
                    cursor.id);
            if (older == null) {
                break;
            }
            neighborWalk.add(older);
            cursor = older;
        }
        assertEquals("neighbor navigation must walk the visible list order",
                linksOf(pagedList), linksOf(neighborWalk));

        // and the ASC list against findNewerRssItem from the last row
        List<RssItem> pagedListAsc = walkKeysetPages((cursorSortKey, cursorId) ->
                mRssDao.findRssItemsPageAsc(null, null, null, cursorSortKey, cursorId, 2));
        assertEquals(5, pagedListAsc.size());
        List<RssItem> neighborWalkAsc = new ArrayList<>();
        cursor = pagedListAsc.get(0);
        neighborWalkAsc.add(cursor);
        while (true) {
            RssItem newer = mRssDao.findNewerRssItem(null, null, null,
                    cursor.pubDate != null ? cursor.pubDate.getTime()
                            : cursor.createdDateTime.getTime(),
                    cursor.id);
            if (newer == null) {
                break;
            }
            neighborWalkAsc.add(newer);
            cursor = newer;
        }
        assertEquals(linksOf(pagedListAsc), linksOf(neighborWalkAsc));
    }

    @Test
    public void testSearchKeysetPaginationWalksAllMatches() {
        RssChannel channel = createRssChannel("http://test.com/feed", "Feed");
        Date sameDate = new Date(System.currentTimeMillis());
        List<RssItem> items = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            RssItem item = createRssItem("bitcoin report " + i, "http://test.com/" + i);
            item.pubDate = sameDate; // tied sort keys across page boundaries
            items.add(item);
        }
        RssItem nonMatch = createRssItem("weather today", "http://test.com/weather");
        nonMatch.pubDate = sameDate;
        items.add(nonMatch);
        mRssDao.insertRssChannel(channel, items.toArray(new RssItem[0]));

        List<RssItem> walk = walkKeysetPages((cursorSortKey, cursorId) ->
                mRssDao.searchRssItemsPageDesc("bitcoin", null, null, null,
                        cursorSortKey, cursorId, 3));

        assertEquals("all 7 matches across the page boundaries, nothing else",
                7, walk.size());
        for (int i = 0; i < walk.size(); i++) {
            assertEquals("http://test.com/" + (6 - i), walk.get(i).link);
            if (i > 0) {
                assertTrue(walk.get(i - 1).id > walk.get(i).id);
            }
        }
    }

    private List<String> linksOf(List<RssItem> rssItems) {
        List<String> links = new ArrayList<>();
        for (RssItem rssItem : rssItems) {
            links.add(rssItem.link);
        }
        return links;
    }

    private RssItem findItemByLink(List<RssItem> rssItems, String link) {
        for (RssItem rssItem : rssItems) {
            if (link == null ? rssItem.link == null : link.equals(rssItem.link)) {
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
