package m.co.rh.id.a_news_provider.base;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.LinkCount;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Instrumented test suite for {@link RssDao}, split in six sections:
 * <p>
 * 1. Search queries ({@code searchRssItemsPageDesc} / {@code searchRssItemsPageAsc}):
 * LIKE matching, wildcard escaping, state filters, limits and sort order.
 * <p>
 * 2. Duplicate detection ({@link RssDao#findRssChannelByUrlVariants(String, String)}):
 * matches a channel by either the raw requested spelling or the normalized spelling.
 * <p>
 * 3. Mark as read by links ({@code markItemsReadByLinks}): flips only unread rows with
 * a matching link across channels, skips null/empty/already-read rows, is idempotent,
 * and handles link lists that cross a query-chunk boundary.
 * <p>
 * 4. Link-grouped queries (issue #45): the grouped keyset pages collapse rows sharing
 * a link into one representative row (newest copy, id tiebreak), null/empty links
 * stay singleton groups, read/favorite filters apply at copy level before grouping,
 * keyset pagination walks every group exactly once, the grouped neighbor queries walk
 * exactly the collapsed list order, and {@code countRssItemsByLinks} counts copies
 * per link across chunk boundaries.
 * <p>
 * 5. Upsert retention (issue #37) and updated-since queries:
 * {@code upsertRssItemsForChannel} inserts new rows, updates matched rows in place
 * (by link, or by the null-safe title/pubDate tuple for linkless items) while
 * preserving id/state/created stamp, retains items missing from the feed untouched,
 * and collapses intra-batch duplicates; the updated-since queries expose the
 * "last seen in feed" meaning of updated_date_time for the notification guard.
 * <p>
 * 6. Keep-read-items pruning (issue #37 phase 2): {@code deleteOldReadRssItems}
 * deletes only old READ non-favorite rows (by the last-seen stamp), keeps unread
 * items and favorites whatever their age, keeps recently-seen rows, is counted
 * across all channels and includes paused channels (global opt-in window).
 */
@RunWith(AndroidJUnit4.class)
public class RssDaoTest {
    private AppDatabase mAppDatabase;
    private RssDao mRssDao;

    @Before
    public void setUp() {
        mAppDatabase = Room.inMemoryDatabaseBuilder(
                        InstrumentationRegistry.getInstrumentation().getTargetContext(),
                        AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        mRssDao = mAppDatabase.rssDao();
    }

    @After
    public void tearDown() {
        mAppDatabase.close();
    }

    // ==================================================================================
    // Section 1: search queries
    // ==================================================================================

    @Test
    public void searchMatchesTitleAndDescriptionCaseInsensitively() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem titleItem = createRssItem("Bitcoin price", "market update",
                "http://test.com/1", createDate(2026, 1, 1));
        RssItem descriptionItem = createRssItem("Market news", "BITCOIN surges today",
                "http://test.com/2", createDate(2026, 1, 2));
        RssItem otherItem = createRssItem("Weather report", "sunny day",
                "http://test.com/3", createDate(2026, 1, 3));
        mRssDao.insertRssChannel(rssChannel, titleItem, descriptionItem, otherItem);

        List<RssItem> results = mRssDao.searchRssItemsPageDesc(
                "bitcoin", null, null, null, null, null, 100);

        assertEquals(2, results.size());
        assertEquals("Market news", results.get(0).title);
        assertEquals("Bitcoin price", results.get(1).title);
    }

    @Test
    public void searchTreatsEscapedPercentAsLiteralCharacter() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem saleItem = createRssItem("50% off", "big sale",
                "http://test.com/1", createDate(2026, 1, 1));
        RssItem otherItem = createRssItem("100% match", "full coverage",
                "http://test.com/2", createDate(2026, 1, 2));
        mRssDao.insertRssChannel(rssChannel, saleItem, otherItem);

        List<RssItem> results = mRssDao.searchRssItemsPageDesc(
                "50\\% off", null, null, null, null, null, 100);

        assertEquals(1, results.size());
        assertEquals("50% off", results.get(0).title);
    }

    @Test
    public void searchTreatsUnescapedUnderscoreAsSingleCharWildcard() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem tileItem = createRssItem("tile roof", "home repair",
                "http://test.com/1", createDate(2026, 1, 1));
        RssItem taleItem = createRssItem("tale of two cities", "book review",
                "http://test.com/2", createDate(2026, 1, 2));
        RssItem otherItem = createRssItem("total recall", "movie night",
                "http://test.com/3", createDate(2026, 1, 3));
        mRssDao.insertRssChannel(rssChannel, tileItem, taleItem, otherItem);

        List<RssItem> results = mRssDao.searchRssItemsPageDesc(
                "t_le", null, null, null, null, null, 100);

        assertEquals(2, results.size());
        assertEquals("tale of two cities", results.get(0).title);
        assertEquals("tile roof", results.get(1).title);
    }

    @Test
    public void searchFiltersByReadAndFavoriteState() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem readItem = createRssItem("read news", "already read",
                "http://test.com/1", createDate(2026, 1, 3));
        RssItem unreadItem = createRssItem("unread news", "not read yet",
                "http://test.com/2", createDate(2026, 1, 1));
        RssItem favoriteItem = createRssItem("favorite news", "marked favorite",
                "http://test.com/3", createDate(2026, 1, 2));
        mRssDao.insertRssChannel(rssChannel, readItem, unreadItem, favoriteItem);
        readItem.isRead = true;
        mRssDao.updateRssItem(readItem);
        favoriteItem.isFavorite = true;
        mRssDao.updateRssItem(favoriteItem);

        List<RssItem> unreadResults = mRssDao.searchRssItemsPageDesc(
                "news", null, 0, null, null, null, 100);
        assertEquals(2, unreadResults.size());
        assertEquals("favorite news", unreadResults.get(0).title);
        assertEquals("unread news", unreadResults.get(1).title);

        List<RssItem> readResults = mRssDao.searchRssItemsPageDesc(
                "news", null, 1, null, null, null, 100);
        assertEquals(1, readResults.size());
        assertEquals("read news", readResults.get(0).title);

        List<RssItem> favoriteResults = mRssDao.searchRssItemsPageDesc(
                "news", null, null, 1, null, null, 100);
        assertEquals(1, favoriteResults.size());
        assertEquals("favorite news", favoriteResults.get(0).title);

        List<RssItem> unreadFavoriteResults = mRssDao.searchRssItemsPageDesc(
                "news", null, 0, 1, null, null, 100);
        assertEquals(1, unreadFavoriteResults.size());
        assertEquals("favorite news", unreadFavoriteResults.get(0).title);
    }

    @Test
    public void searchRespectsLimitAndSortOrder() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem item1 = createRssItem("bitcoin news 1", null,
                "http://test.com/1", createDate(2026, 1, 1));
        RssItem item2 = createRssItem("bitcoin news 2", null,
                "http://test.com/2", createDate(2026, 1, 2));
        RssItem item3 = createRssItem("bitcoin news 3", null,
                "http://test.com/3", createDate(2026, 1, 3));
        RssItem item4 = createRssItem("bitcoin news 4", null,
                "http://test.com/4", createDate(2026, 1, 4));
        RssItem item5 = createRssItem("bitcoin news 5", null,
                "http://test.com/5", createDate(2026, 1, 5));
        mRssDao.insertRssChannel(rssChannel, item1, item2, item3, item4, item5);

        List<RssItem> newestResults = mRssDao.searchRssItemsPageDesc(
                "bitcoin", null, null, null, null, null, 3);
        assertEquals(3, newestResults.size());
        assertEquals("bitcoin news 5", newestResults.get(0).title);
        assertEquals("bitcoin news 4", newestResults.get(1).title);
        assertEquals("bitcoin news 3", newestResults.get(2).title);

        List<RssItem> oldestResults = mRssDao.searchRssItemsPageAsc(
                "bitcoin", null, null, null, null, null, 3);
        assertEquals(3, oldestResults.size());
        assertEquals("bitcoin news 1", oldestResults.get(0).title);
        assertEquals("bitcoin news 2", oldestResults.get(1).title);
        assertEquals("bitcoin news 3", oldestResults.get(2).title);
    }

    @Test
    public void searchWithNullChannelIdReturnsItemsAcrossChannels() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssItem channel1Item = createRssItem("bitcoin in feed 1", null,
                "http://test.com/1", createDate(2026, 1, 1));
        RssItem channel2Item = createRssItem("bitcoin in feed 2", null,
                "http://test.com/2", createDate(2026, 1, 2));
        RssItem channel2OtherItem = createRssItem("weather report", null,
                "http://test.com/3", createDate(2026, 1, 3));
        mRssDao.insertRssChannel(channel1, channel1Item);
        mRssDao.insertRssChannel(channel2, channel2Item, channel2OtherItem);

        List<RssItem> allResults = mRssDao.searchRssItemsPageDesc(
                "bitcoin", null, null, null, null, null, 100);
        assertEquals(2, allResults.size());
        assertEquals("bitcoin in feed 2", allResults.get(0).title);
        assertEquals("bitcoin in feed 1", allResults.get(1).title);

        List<RssItem> channel1Results = mRssDao.searchRssItemsPageDesc(
                "bitcoin", channel1.id, null, null, null, null, 100);
        assertEquals(1, channel1Results.size());
        assertEquals("bitcoin in feed 1", channel1Results.get(0).title);
        assertTrue(mRssDao.searchRssItemsPageDesc(
                "bitcoin", channel2.id, null, null, null, null, 100).size() == 1);
    }

    // ==================================================================================
    // Section 2: duplicate detection via findRssChannelByUrlVariants
    // ==================================================================================

    @Test
    public void findRssChannelByUrlVariants_exactMatchReturnsInsertedChannel() {
        RssChannel rssChannel = createRssChannel("https://example.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        RssChannel found = mRssDao.findRssChannelByUrlVariants(
                "https://example.com/feed", "https://example.com/feed");

        assertNotNull(found);
        assertEquals(rssChannel.id, found.id);
    }

    @Test
    public void findRssChannelByUrlVariants_normalizedParamMatchesStoredCanonicalUrl() {
        RssChannel rssChannel = createRssChannel("https://example.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        // raw param has a trailing slash and does not match - the normalized param must find it
        RssChannel found = mRssDao.findRssChannelByUrlVariants(
                "https://example.com/feed/", "https://example.com/feed");

        assertNotNull(found);
        assertEquals(rssChannel.id, found.id);
    }

    @Test
    public void findRssChannelByUrlVariants_rawParamMatchesLegacyRawStoredUrl() {
        // legacy rows were stored with the scheme-prepended but UN-normalized spelling
        RssChannel legacyChannel = createRssChannel("https://Example.com/feed/", "Legacy Feed");
        mRssDao.insertRssChannel(legacyChannel);

        // normalized param differs in host case and trailing slash - the raw param must find it
        RssChannel found = mRssDao.findRssChannelByUrlVariants(
                "https://Example.com/feed/", "https://example.com/feed");

        assertNotNull(found);
        assertEquals(legacyChannel.id, found.id);
    }

    @Test
    public void findRssChannelByUrlVariants_duplicatePairReturnsLowestIdRow() {
        // while a duplicate pair from before URL normalization still exists,
        // the query must deterministically return the OLDEST row (lowest id)
        RssChannel legacyChannel = createRssChannel("https://Example.com/feed/", "Legacy Feed");
        mRssDao.insertRssChannel(legacyChannel);
        RssChannel canonicalChannel = createRssChannel("https://example.com/feed", "Canonical Feed");
        mRssDao.insertRssChannel(canonicalChannel);
        assertTrue(legacyChannel.id < canonicalChannel.id);

        RssChannel found = mRssDao.findRssChannelByUrlVariants(
                "https://Example.com/feed/", "https://example.com/feed");

        assertNotNull(found);
        assertEquals(legacyChannel.id, found.id);
        assertEquals("Legacy Feed", found.feedName);
    }

    @Test
    public void findRssChannelByUrlVariants_duplicatePairReversedInsertOrderStillReturnsLowestIdRow() {
        // insert order reversed - the lower id (now the canonical-spelled row) must win
        RssChannel canonicalChannel = createRssChannel("https://example.com/feed", "Canonical Feed");
        mRssDao.insertRssChannel(canonicalChannel);
        RssChannel legacyChannel = createRssChannel("https://Example.com/feed/", "Legacy Feed");
        mRssDao.insertRssChannel(legacyChannel);
        assertTrue(canonicalChannel.id < legacyChannel.id);

        RssChannel found = mRssDao.findRssChannelByUrlVariants(
                "https://Example.com/feed/", "https://example.com/feed");

        assertNotNull(found);
        assertEquals(canonicalChannel.id, found.id);
        assertEquals("Canonical Feed", found.feedName);
    }

    // ==================================================================================
    // Section 3: mark as read by links
    // ==================================================================================

    @Test
    public void markItemsReadByLinks_marksOnlyUnreadMatchingRowsAcrossChannels() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssItem channel1SharedItem = createRssItem("shared news in feed 1", null,
                "http://test.com/shared", createDate(2026, 1, 1));
        RssItem channel2SharedItem = createRssItem("shared news in feed 2", null,
                "http://test.com/shared", createDate(2026, 1, 2));
        RssItem nullLinkItem = createRssItem("null link news", null,
                null, createDate(2026, 1, 3));
        RssItem emptyLinkItem = createRssItem("empty link news", null,
                "", createDate(2026, 1, 4));
        RssItem alreadyReadItem = createRssItem("read news", null,
                "http://test.com/read", createDate(2026, 1, 5));
        mRssDao.insertRssChannel(channel1, channel1SharedItem, nullLinkItem, alreadyReadItem);
        mRssDao.insertRssChannel(channel2, channel2SharedItem, emptyLinkItem);
        alreadyReadItem.isRead = true;
        mRssDao.updateRssItem(alreadyReadItem);

        // null and empty entries must neither break the IN clause nor match the
        // null/empty-link rows
        List<String> links = new ArrayList<>();
        links.add("http://test.com/shared");
        links.add("http://test.com/read");
        links.add(null);
        links.add("");

        int markedRows = mRssDao.markItemsReadByLinks(links);

        // only the two unread rows sharing the link flip, across both channels
        assertEquals(2, markedRows);
        assertTrue(findByTitle(mRssDao.findRssItemsByChannelId(channel1.id),
                "shared news in feed 1").isRead);
        assertTrue(findByTitle(mRssDao.findRssItemsByChannelId(channel2.id),
                "shared news in feed 2").isRead);

        // already-read, null-link and empty-link rows stay untouched
        assertTrue(findByTitle(mRssDao.findRssItemsByChannelId(channel1.id),
                "read news").isRead);
        assertFalse(findByTitle(mRssDao.findRssItemsByChannelId(channel1.id),
                "null link news").isRead);
        assertFalse(findByTitle(mRssDao.findRssItemsByChannelId(channel2.id),
                "empty link news").isRead);

        // second call is a no-op thanks to the is_read = 0 guard
        assertEquals(0, mRssDao.markItemsReadByLinks(links));
    }

    @Test
    public void markItemsReadByLinks_chunkBoundary_marksAllRowsAcrossChunks() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        // 501 distinct links cross exactly one 500-link query chunk (500 + 1)
        int itemCount = 501;
        ArrayList<RssItem> rssItems = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String link = "http://test.com/item" + i;
            rssItems.add(createRssItem("item " + i, null, link, createDate(2026, 1, 1)));
            links.add(link);
        }
        mRssDao.insertRssChannel(rssChannel, rssItems.toArray(new RssItem[0]));

        // the null and empty entries land in the overflow chunk (chunk 2) and
        // must not break the chunk loop - the query never matches them anyway
        links.add(null);
        links.add("");

        int markedRows = mRssDao.markItemsReadByLinks(links);

        // both chunks ran fully: 500 rows in chunk 1 plus 1 row in chunk 2
        assertEquals(itemCount, markedRows);
        List<RssItem> storedItems = mRssDao.findRssItemsByChannelId(rssChannel.id);
        assertEquals(itemCount, storedItems.size());
        for (int i = 0; i < itemCount; i++) {
            RssItem storedItem = findByTitle(storedItems, "item " + i);
            assertNotNull(storedItem);
            assertTrue(storedItem.isRead);
        }
    }

    // ==================================================================================
    // Section 4: link-grouped queries (issue #45)
    // ==================================================================================

    @Test
    public void groupedPageDesc_collapsesDuplicateLinksAcrossChannels() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssChannel channel3 = createRssChannel("http://test.com/feed3", "Feed 3");
        // the same story in three feeds, plus one story that only feed 3 carries
        RssItem copyA = createRssItem("shared story in feed 1", null,
                "http://test.com/shared", createDate(2026, 1, 1));
        RssItem copyB = createRssItem("shared story in feed 2", null,
                "http://test.com/shared", createDate(2026, 1, 2));
        RssItem copyC = createRssItem("shared story in feed 3", null,
                "http://test.com/shared", createDate(2026, 1, 3));
        RssItem soloItem = createRssItem("exclusive story", null,
                "http://test.com/solo", createDate(2026, 1, 4));
        mRssDao.insertRssChannel(channel1, copyA);
        mRssDao.insertRssChannel(channel2, copyB);
        mRssDao.insertRssChannel(channel3, copyC, soloItem);

        List<RssItem> grouped = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);

        // 4 rows in the table but only 2 collapsed groups
        assertEquals(2, grouped.size());
        // DESC order: exclusive (Jan 4) first, then the shared group (rep = Jan 3)
        assertEquals("exclusive story", grouped.get(0).title);
        assertEquals(channel3.id.longValue(), grouped.get(0).channelId.longValue());
        assertEquals("shared story in feed 3", grouped.get(1).title);
        assertEquals(channel3.id.longValue(), grouped.get(1).channelId.longValue());
        assertEquals("http://test.com/shared", grouped.get(1).link);

        // ASC mirror returns the same groups in reverse
        List<RssItem> groupedAsc = mRssDao.findRssItemsPageGroupedByLinkAsc(
                null, null, null, null, null, 100);
        assertEquals(2, groupedAsc.size());
        assertEquals("shared story in feed 3", groupedAsc.get(0).title);
        assertEquals("exclusive story", groupedAsc.get(1).title);
    }

    @Test
    public void groupedPageDesc_nullAndEmptyLinksStaySingletonRows() {
        // TRAP: a plain GROUP BY link would collapse ALL null-link rows into one
        // group and all empty-link rows into another - the grouping expression
        // COALESCE(NULLIF(link, ''), 'rss_item#' || id) must give each invalid-link
        // row its own singleton group
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssItem nullLink1 = createRssItem("null link news 1", null,
                null, createDate(2026, 1, 3));
        RssItem nullLink2 = createRssItem("null link news 2", null,
                null, createDate(2026, 1, 1));
        RssItem emptyLink1 = createRssItem("empty link news 1", null,
                "", createDate(2026, 1, 4));
        RssItem emptyLink2 = createRssItem("empty link news 2", null,
                "", createDate(2026, 1, 2));
        mRssDao.insertRssChannel(channel1, nullLink1, nullLink2, emptyLink1);
        mRssDao.insertRssChannel(channel2, emptyLink2);

        List<RssItem> grouped = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);

        // every invalid-link row survives as its own list row
        assertEquals(4, grouped.size());
        assertEquals("empty link news 1", grouped.get(0).title);
        assertEquals("null link news 1", grouped.get(1).title);
        assertEquals("empty link news 2", grouped.get(2).title);
        assertEquals("null link news 2", grouped.get(3).title);
    }

    @Test
    public void groupedPageDesc_representativeIsNewestCopyWithIdTiebreak() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        // copy dates deliberately out of insert order: the newest copy (Jan 3) is
        // NOT the first-inserted or lowest-id row
        RssItem copyOld = createRssItem("copy old", null,
                "http://test.com/shared", createDate(2026, 1, 1));
        RssItem copyMid = createRssItem("copy mid", null,
                "http://test.com/shared", createDate(2026, 1, 2));
        RssItem copyNew = createRssItem("copy new", null,
                "http://test.com/shared", createDate(2026, 1, 3));
        mRssDao.insertRssChannel(channel1, copyOld);
        mRssDao.insertRssChannel(channel2, copyMid, copyNew);

        List<RssItem> grouped = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);

        assertEquals(1, grouped.size());
        assertEquals("copy new", grouped.get(0).title);
        assertEquals(channel2.id.longValue(), grouped.get(0).channelId.longValue());

        // TRAP: same-millisecond ties - sync batches persist rows in one
        // insertRssItem call, so equal dates are the norm, not the exception.
        // The tiebreak is the highest id. pubDate AND createdDateTime are set
        // explicitly on both copies so insertRssItem cannot re-stamp them with
        // different current times.
        Date sameMillis = createDate(2026, 2, 1);
        RssItem tieLowId = createRssItem("tie low id", null,
                "http://test.com/tie", sameMillis);
        RssItem tieHighId = createRssItem("tie high id", null,
                "http://test.com/tie", sameMillis);
        tieLowId.createdDateTime = sameMillis;
        tieHighId.createdDateTime = sameMillis;
        // a FRESH channel entity: channel1/channel2 already carry their assigned
        // ids, re-inserting them collides on the rss_channel.id primary key
        RssChannel channel3 = createRssChannel("http://test.com/feed3", "Feed 3");
        mRssDao.insertRssChannel(channel3, tieLowId, tieHighId);
        assertTrue(tieLowId.id < tieHighId.id);

        List<RssItem> groupedWithTie = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);
        assertEquals(2, groupedWithTie.size());
        // newest first: the tie group (Feb 1) outranks the Jan group...
        assertEquals("tie high id", groupedWithTie.get(0).title);
        assertEquals(tieHighId.id, groupedWithTie.get(0).id);
        // ...and the Jan group keeps its newest copy
        assertEquals("copy new", groupedWithTie.get(1).title);
    }

    @Test
    public void groupedPageDesc_representativeFallsBackToCreatedDateTime() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        // neither copy has a pub_date: the effective date falls back to
        // createdDateTime, set explicitly so the test does not depend on
        // insert-order timestamps
        RssItem noPubOld = createRssItem("no pub old", null,
                "http://test.com/nopub", null);
        noPubOld.createdDateTime = createDate(2026, 1, 1);
        RssItem noPubNew = createRssItem("no pub new", null,
                "http://test.com/nopub", null);
        noPubNew.createdDateTime = createDate(2026, 1, 5);
        mRssDao.insertRssChannel(channel1, noPubOld);
        mRssDao.insertRssChannel(channel2, noPubNew);

        List<RssItem> grouped = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);

        assertEquals(1, grouped.size());
        assertEquals("no pub new", grouped.get(0).title);
    }

    @Test
    public void groupedPageDesc_filtersApplyAtCopyLevelBeforeGrouping() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        // mixed-state copies of one story: the newest copy is read, the older one unread
        RssItem readCopy = createRssItem("story read copy", null,
                "http://test.com/mixed", createDate(2026, 1, 3));
        RssItem unreadCopy = createRssItem("story unread copy", null,
                "http://test.com/mixed", createDate(2026, 1, 2));
        // favorite story: the favorite copy is the OLDER one
        RssItem newerPlainCopy = createRssItem("fav story plain copy", null,
                "http://test.com/fav", createDate(2026, 1, 3));
        RssItem olderFavCopy = createRssItem("fav story favorite copy", null,
                "http://test.com/fav", createDate(2026, 1, 2));
        // a story with only unread copies must vanish under the read filter
        RssItem allUnread = createRssItem("all unread story", null,
                "http://test.com/allunread", createDate(2026, 1, 4));
        mRssDao.insertRssChannel(channel1, readCopy, olderFavCopy, allUnread);
        mRssDao.insertRssChannel(channel2, unreadCopy, newerPlainCopy);
        readCopy.isRead = true;
        mRssDao.updateRssItem(readCopy);
        olderFavCopy.isFavorite = true;
        mRssDao.updateRssItem(olderFavCopy);

        // no filter: representative of the mixed group = newest copy (the read one);
        // the two Jan-3 representatives tie on date and break by id: newerPlainCopy
        // (channel 2, inserted second, id 5) outranks readCopy (channel 1, id 1)
        List<RssItem> noFilter = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);
        assertEquals(3, noFilter.size());
        assertEquals("all unread story", noFilter.get(0).title);
        assertEquals("fav story plain copy", noFilter.get(1).title);
        assertEquals("story read copy", noFilter.get(2).title);

        // unread filter: BOTH mixed-state stories survive - the mixed group through
        // its unread copy (rep = newest unread copy, Jan 2) and the fav group
        // through its plain unread copy (rep = newerPlainCopy, Jan 3; being a
        // favorite does not exclude a copy from the unread filter)
        List<RssItem> unreadOnly = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, 0, null, null, null, 100);
        assertEquals(3, unreadOnly.size());
        assertEquals("all unread story", unreadOnly.get(0).title);
        assertEquals("fav story plain copy", unreadOnly.get(1).title);
        assertEquals("story unread copy", unreadOnly.get(2).title);
        assertEquals("http://test.com/mixed", unreadOnly.get(2).link);

        // read filter: the all-unread story disappears, the mixed group shows its
        // read copy
        List<RssItem> readOnly = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, 1, null, null, null, 100);
        assertEquals(1, readOnly.size());
        assertEquals("story read copy", readOnly.get(0).title);

        // favorite filter: only the favorite story remains, represented by its
        // favorite copy even though that copy is the older one
        List<RssItem> favOnly = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, 1, null, null, 100);
        assertEquals(1, favOnly.size());
        assertEquals("fav story favorite copy", favOnly.get(0).title);
        assertTrue(favOnly.get(0).isFavorite);

        // channel filter passes straight through: only feed 2's rows are candidates
        List<RssItem> channel2Only = mRssDao.findRssItemsPageGroupedByLinkDesc(
                channel2.id, null, null, null, null, 100);
        assertEquals(2, channel2Only.size());
        assertEquals("fav story plain copy", channel2Only.get(0).title);
        assertEquals("story unread copy", channel2Only.get(1).title);
    }

    @Test
    public void groupedPageDesc_pageBoundaryWalkYieldsEachGroupExactlyOnce() {
        // 4 distinct links duplicated across both channels, plus a null-link and an
        // empty-link singleton row = 6 collapsed groups out of 10 rows
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        ArrayList<RssItem> channel1Items = new ArrayList<>();
        ArrayList<RssItem> channel2Items = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Date date = createDate(2026, 1, 1 + i);
            channel1Items.add(createRssItem("story " + i + " feed1", null,
                    "http://test.com/story" + i, date));
            channel2Items.add(createRssItem("story " + i + " feed2", null,
                    "http://test.com/story" + i, date));
        }
        channel1Items.add(createRssItem("null link row", null, null,
                createDate(2026, 1, 10)));
        channel1Items.add(createRssItem("empty link row", null, "",
                createDate(2026, 1, 11)));
        mRssDao.insertRssChannel(channel1,
                channel1Items.toArray(new RssItem[0]));
        mRssDao.insertRssChannel(channel2,
                channel2Items.toArray(new RssItem[0]));

        // full keyset walk with a limit smaller than the group count: pages must
        // tile the collapsed list without repeating or skipping a group
        int limit = 3;
        List<String> walkedTitles = new ArrayList<>();
        Long afterSortKey = null;
        Long afterId = null;
        while (true) {
            List<RssItem> page = mRssDao.findRssItemsPageGroupedByLinkDesc(
                    null, null, null, afterSortKey, afterId, limit);
            if (page.isEmpty()) {
                break;
            }
            assertTrue("page overflow", page.size() <= limit);
            for (RssItem rssItem : page) {
                walkedTitles.add(rssItem.title);
            }
            RssItem last = page.get(page.size() - 1);
            afterSortKey = (last.pubDate != null ? last.pubDate : last.createdDateTime)
                    .getTime();
            afterId = last.id;
        }
        // 6 groups walked, every title exactly once
        assertEquals(6, walkedTitles.size());
        assertEquals(6, new ArrayList<>(new HashSet<>(walkedTitles)).size());
        // DESC order: empty link (Jan 11), null link (Jan 10), then stories 3..0
        assertEquals("empty link row", walkedTitles.get(0));
        assertEquals("null link row", walkedTitles.get(1));
        for (int i = 0; i < 4; i++) {
            // duplicates collapse to the feed2 spelling (higher id wins the tie,
            // both copies share the same date)
            assertEquals("story " + (3 - i) + " feed2", walkedTitles.get(2 + i));
        }

        // ASC mirror walks the exact reverse order
        List<String> walkedAsc = new ArrayList<>();
        afterSortKey = null;
        afterId = null;
        while (true) {
            List<RssItem> page = mRssDao.findRssItemsPageGroupedByLinkAsc(
                    null, null, null, afterSortKey, afterId, limit);
            if (page.isEmpty()) {
                break;
            }
            for (RssItem rssItem : page) {
                walkedAsc.add(rssItem.title);
            }
            RssItem last = page.get(page.size() - 1);
            afterSortKey = (last.pubDate != null ? last.pubDate : last.createdDateTime)
                    .getTime();
            afterId = last.id;
        }
        // the ASC walk runs oldest-first: flip it before comparing with the
        // DESC walk so equality means "exactly the mirrored order"
        Collections.reverse(walkedAsc);
        assertEquals(walkedTitles, walkedAsc);
    }

    @Test
    public void groupedNeighborWalksFollowCollapsedListOrder() {
        // 4 distinct links, each duplicated across two channels = 4 collapsed groups
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        ArrayList<RssItem> channel1Items = new ArrayList<>();
        ArrayList<RssItem> channel2Items = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Date date = createDate(2026, 1, 1 + i);
            channel1Items.add(createRssItem("story " + i + " feed1", null,
                    "http://test.com/story" + i, date));
            channel2Items.add(createRssItem("story " + i + " feed2", null,
                    "http://test.com/story" + i, date));
        }
        mRssDao.insertRssChannel(channel1,
                channel1Items.toArray(new RssItem[0]));
        mRssDao.insertRssChannel(channel2,
                channel2Items.toArray(new RssItem[0]));

        // the collapsed DESC list, head to tail
        List<RssItem> grouped = mRssDao.findRssItemsPageGroupedByLinkDesc(
                null, null, null, null, null, 100);
        assertEquals(4, grouped.size());

        // walk DOWN the list from the newest representative: every step must land
        // on the next visible row of the grouped list
        RssItem anchor = grouped.get(0);
        for (int i = 1; i < grouped.size(); i++) {
            RssItem older = mRssDao.findOlderRssItemGroupedByLink(null, null, null,
                    anchor.pubDate.getTime(), anchor.id);
            assertNotNull("older neighbor missing at index " + i, older);
            assertEquals(grouped.get(i).id, older.id);
            assertEquals(grouped.get(i).title, older.title);
            anchor = older;
        }
        // below the oldest group there is nothing left
        assertNull(mRssDao.findOlderRssItemGroupedByLink(null, null, null,
                anchor.pubDate.getTime(), anchor.id));

        // walk UP the list from the oldest representative: same invariant, ASC
        anchor = grouped.get(grouped.size() - 1);
        for (int i = grouped.size() - 2; i >= 0; i--) {
            RssItem newer = mRssDao.findNewerRssItemGroupedByLink(null, null, null,
                    anchor.pubDate.getTime(), anchor.id);
            assertNotNull("newer neighbor missing at index " + i, newer);
            assertEquals(grouped.get(i).id, newer.id);
            assertEquals(grouped.get(i).title, newer.title);
            anchor = newer;
        }
        assertNull(mRssDao.findNewerRssItemGroupedByLink(null, null, null,
                anchor.pubDate.getTime(), anchor.id));

        // copy-level filters carry over: with the unread filter on, a group whose
        // every copy is read must be skipped by the walk
        RssItem story0Feed1 = findByTitle(mRssDao.findRssItemsByChannelId(channel1.id),
                "story 0 feed1");
        RssItem story0Feed2 = findByTitle(mRssDao.findRssItemsByChannelId(channel2.id),
                "story 0 feed2");
        story0Feed1.isRead = true;
        mRssDao.updateRssItem(story0Feed1);
        story0Feed2.isRead = true;
        mRssDao.updateRssItem(story0Feed2);
        // anchor on the newest group (story 3): the next older UNREAD group is
        // story 2 (Jan 3) - story 1 (Jan 2) and story 0 (Jan 1) are older still,
        // and story 0 is additionally fully read so no unread filter could ever
        // return it; a wrong expectation here once masked a real regression risk
        RssItem story3 = grouped.get(0);
        RssItem olderUnread = mRssDao.findOlderRssItemGroupedByLink(null, 0, null,
                story3.pubDate.getTime(), story3.id);
        assertNotNull(olderUnread);
        assertEquals("story 2 feed2", olderUnread.title);
        // the walk continues past story 2 down to story 1 (story 0 is fully read
        // and invisible to the unread filter)
        RssItem nextOlderUnread = mRssDao.findOlderRssItemGroupedByLink(null, 0, null,
                olderUnread.pubDate.getTime(), olderUnread.id);
        assertNotNull(nextOlderUnread);
        assertEquals("story 1 feed2", nextOlderUnread.title);
        RssItem lastUnread = mRssDao.findOlderRssItemGroupedByLink(null, 0, null,
                nextOlderUnread.pubDate.getTime(), nextOlderUnread.id);
        assertNull("the fully-read story 0 must be invisible to the unread filter",
                lastUnread);
    }

    @Test
    public void countRssItemsByLinks_countsCopiesPerLinkIgnoringNullAndEmpty() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssChannel channel3 = createRssChannel("http://test.com/feed3", "Feed 3");
        // triple copy, double copy, single copy, null-link and empty-link rows
        RssItem triple1 = createRssItem("triple 1", null,
                "http://test.com/triple", createDate(2026, 1, 1));
        RssItem triple2 = createRssItem("triple 2", null,
                "http://test.com/triple", createDate(2026, 1, 2));
        RssItem triple3 = createRssItem("triple 3", null,
                "http://test.com/triple", createDate(2026, 1, 3));
        RssItem double1 = createRssItem("double 1", null,
                "http://test.com/double", createDate(2026, 1, 1));
        RssItem double2 = createRssItem("double 2", null,
                "http://test.com/double", createDate(2026, 1, 2));
        RssItem single = createRssItem("single", null,
                "http://test.com/single", createDate(2026, 1, 1));
        RssItem nullLink = createRssItem("null link", null, null,
                createDate(2026, 1, 1));
        RssItem emptyLink = createRssItem("empty link", null, "",
                createDate(2026, 1, 1));
        mRssDao.insertRssChannel(channel1, triple1, double1, single, nullLink, emptyLink);
        mRssDao.insertRssChannel(channel2, triple2, double2);
        mRssDao.insertRssChannel(channel3, triple3);

        List<String> links = new ArrayList<>();
        links.add("http://test.com/triple");
        links.add("http://test.com/double");
        links.add("http://test.com/single");
        links.add("http://test.com/missing");
        // null and empty entries must be dropped, never matched
        links.add(null);
        links.add("");

        List<LinkCount> counts =
                mRssDao.countRssItemsByLinks(links);

        // absolute copy counts across ALL channels, regardless of read state
        assertEquals(3, counts.size());
        for (LinkCount linkCount : counts) {
            switch (linkCount.link) {
                case "http://test.com/triple":
                    assertEquals(3, linkCount.cnt);
                    break;
                case "http://test.com/double":
                    assertEquals(2, linkCount.cnt);
                    break;
                case "http://test.com/single":
                    assertEquals(1, linkCount.cnt);
                    break;
                default:
                    fail("unexpected link: " + linkCount.link);
            }
        }

        // null/empty input produces no count rows - empty-link rows are NOT copies
        // of each other
        ArrayList<String> invalidOnly = new ArrayList<>();
        invalidOnly.add(null);
        invalidOnly.add("");
        assertTrue(mRssDao.countRssItemsByLinks(invalidOnly).isEmpty());
        assertTrue(mRssDao.countRssItemsByLinks(null).isEmpty());
        assertTrue(mRssDao.countRssItemsByLinks(new ArrayList<>()).isEmpty());
    }

    @Test
    public void countRssItemsByLinks_chunkBoundaryPast500Links() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        // 501 distinct links cross exactly one 500-link query chunk (500 + 1);
        // the first link additionally has a cross-channel copy, so its absolute
        // count is 2 while every other link counts 1
        int linkCount = 501;
        ArrayList<RssItem> rssItems = new ArrayList<>();
        ArrayList<String> links = new ArrayList<>();
        for (int i = 0; i < linkCount; i++) {
            String link = "http://test.com/item" + i;
            rssItems.add(createRssItem("item " + i, null, link,
                    createDate(2026, 1, 1)));
            links.add(link);
        }
        mRssDao.insertRssChannel(rssChannel, rssItems.toArray(new RssItem[0]));
        RssChannel copyChannel = createRssChannel("http://test.com/feed-copy", "Feed Copy");
        RssItem duplicatedCopy = createRssItem("item 0 copy", null,
                "http://test.com/item0", createDate(2026, 1, 2));
        mRssDao.insertRssChannel(copyChannel, duplicatedCopy);

        List<LinkCount> counts =
                mRssDao.countRssItemsByLinks(links);

        // the overflow chunk ran: all 501 links came back
        assertEquals(linkCount, counts.size());
        for (LinkCount linkCountResult : counts) {
            if ("http://test.com/item0".equals(linkCountResult.link)) {
                assertEquals(2, linkCountResult.cnt);
            } else {
                assertEquals(1, linkCountResult.cnt);
            }
        }
    }

    // ==================================================================================
    // Section 5: upsert retention (issue #37) and updated-since queries
    // ==================================================================================

    @Test
    public void upsertRssItemsForChannel_insertsNewItemsWithStamps() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        parsedItems.add(createRssItem("item", "desc", "http://test.com/item",
                createDate(2026, 1, 1)));

        List<RssItem> persisted = mRssDao.upsertRssItemsForChannel(rssChannel, parsedItems);

        assertEquals(1, persisted.size());
        assertNotNull("Inserted row must have a generated id", persisted.get(0).id);
        assertEquals("Inserted row belongs to the channel", rssChannel.id, persisted.get(0).channelId);
        assertNotNull("Inserted row must be stamped created_date_time", persisted.get(0).createdDateTime);
        assertNotNull("Inserted row must be stamped updated_date_time", persisted.get(0).updatedDateTime);

        List<RssItem> stored = mRssDao.findRssItemsByChannelId(rssChannel.id);
        assertEquals(1, stored.size());
        assertEquals(persisted.get(0).id, stored.get(0).id);
        assertEquals(persisted.get(0).createdDateTime, stored.get(0).createdDateTime);
    }

    @Test
    public void upsertRssItemsForChannel_updatesMatchedRowInPlacePreservingState() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem original = createRssItem("old title", "old desc",
                "http://test.com/item", createDate(2026, 1, 1));
        original.isRead = true;
        original.isFavorite = true;
        mRssDao.insertRssChannel(rssChannel, original);
        Long originalId = original.id;
        Date originalCreated = original.createdDateTime;
        Date originalUpdated = original.updatedDateTime;
        assertNotNull(originalId);
        assertNotNull(originalCreated);
        assertNotNull(originalUpdated);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        RssItem parsed = createRssItem("new title", "new desc",
                "http://test.com/item", createDate(2026, 2, 2));
        parsed.mediaImage = "http://test.com/img.png";
        parsed.categories = "Fresh\u001FTech";
        parsedItems.add(parsed);

        List<RssItem> persisted = mRssDao.upsertRssItemsForChannel(rssChannel, parsedItems);

        assertEquals(1, persisted.size());
        RssItem merged = persisted.get(0);
        assertEquals("Matched row keeps its id", originalId, merged.id);
        assertEquals("Title refreshed from the feed", "new title", merged.title);
        assertEquals("Description refreshed from the feed", "new desc", merged.description);
        assertEquals("Pub date refreshed from the feed", createDate(2026, 2, 2), merged.pubDate);
        assertEquals("Media refreshed from the feed", "http://test.com/img.png", merged.mediaImage);
        assertEquals("Categories refreshed from the feed", "Fresh\u001FTech", merged.categories);
        assertTrue("Read state preserved", merged.isRead);
        assertTrue("Favorite state preserved", merged.isFavorite);
        assertEquals("created_date_time preserved", originalCreated, merged.createdDateTime);
        assertNotNull("updated_date_time refreshed", merged.updatedDateTime);
        assertFalse("updated_date_time not rolled back",
                merged.updatedDateTime.before(originalUpdated));
    }

    @Test
    public void upsertRssItemsForChannel_retainsItemsMissingFromFeedUntouched() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem dropped = createRssItem("dropped", "desc",
                "http://test.com/dropped", createDate(2026, 1, 1));
        dropped.isRead = true;
        dropped.isFavorite = true;
        mRssDao.insertRssChannel(rssChannel, dropped);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        parsedItems.add(createRssItem("fresh", "desc",
                "http://test.com/fresh", createDate(2026, 2, 1)));

        List<RssItem> persisted = mRssDao.upsertRssItemsForChannel(rssChannel, parsedItems);

        assertEquals("Persisted order: parsed rows first, then retained", 2, persisted.size());
        assertEquals("http://test.com/fresh", persisted.get(0).link);
        assertEquals("http://test.com/dropped", persisted.get(1).link);

        List<RssItem> stored = mRssDao.findRssItemsByChannelId(rssChannel.id);
        assertEquals(2, stored.size());
        RssItem retained = findByTitle(stored, "dropped");
        assertNotNull("Item missing from the feed must be retained", retained);
        assertEquals("Retained row keeps its id", dropped.id, retained.id);
        assertTrue(retained.isRead);
        assertTrue(retained.isFavorite);
        assertEquals("Retained row keeps its created stamp",
                dropped.createdDateTime, retained.createdDateTime);
        assertEquals("Retained row keeps its OLD updated stamp (last seen in feed)",
                dropped.updatedDateTime, retained.updatedDateTime);
    }

    @Test
    public void upsertRssItemsForChannel_intraBatchDuplicateLinkUpdatesSameRow() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        ArrayList<RssItem> parsedItems = new ArrayList<>();
        parsedItems.add(createRssItem("first copy", "desc",
                "http://test.com/dup", createDate(2026, 1, 1)));
        parsedItems.add(createRssItem("second copy", "desc2",
                "http://test.com/dup", createDate(2026, 1, 2)));

        List<RssItem> persisted = mRssDao.upsertRssItemsForChannel(rssChannel, parsedItems);

        assertEquals("Duplicate link within one batch must not insert twice", 1, persisted.size());
        assertEquals("The later duplicate updates the just-persisted row",
                "second copy", persisted.get(0).title);
        assertEquals(createDate(2026, 1, 2), persisted.get(0).pubDate);
        assertEquals("Only one row exists",
                1, mRssDao.findRssItemsByChannelId(rssChannel.id).size());
    }

    @Test
    public void upsertRssItemsForChannel_matchesLinklessItemsByTitleAndDate() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        // first sync: linkless item (the parser emits "" links for RSS items without
        // <link> and null links for Atom entries)
        ArrayList<RssItem> firstBatch = new ArrayList<>();
        firstBatch.add(createRssItem("linkless", "desc", null, createDate(2026, 1, 1)));
        List<RssItem> persistedFirst = mRssDao.upsertRssItemsForChannel(rssChannel, firstBatch);
        assertEquals(1, persistedFirst.size());
        Long firstId = persistedFirst.get(0).id;
        assertNotNull(firstId);

        // second sync: same title/pubDate, "" link spelling - must match the same row
        ArrayList<RssItem> secondBatch = new ArrayList<>();
        RssItem second = createRssItem("linkless", "desc updated", "", createDate(2026, 1, 1));
        second.isFavorite = true; // feed-side state must NOT win over the stored row
        secondBatch.add(second);
        List<RssItem> persistedSecond = mRssDao.upsertRssItemsForChannel(rssChannel, secondBatch);

        assertEquals(1, persistedSecond.size());
        assertEquals("Linkless item matched by the (title, pubDate) tuple",
                firstId, persistedSecond.get(0).id);
        assertEquals("desc updated", persistedSecond.get(0).description);
        assertFalse("Matched row keeps its own favorite state", persistedSecond.get(0).isFavorite);
        assertEquals("Still exactly one row",
                1, mRssDao.findRssItemsByChannelId(rssChannel.id).size());

        // duplicate linkless entries within ONE batch also collapse into one row
        ArrayList<RssItem> thirdBatch = new ArrayList<>();
        thirdBatch.add(createRssItem("linkless", "d1", null, createDate(2026, 1, 1)));
        thirdBatch.add(createRssItem("linkless", "d2", null, createDate(2026, 1, 1)));
        List<RssItem> persistedThird = mRssDao.upsertRssItemsForChannel(rssChannel, thirdBatch);
        assertEquals(1, persistedThird.size());
        assertEquals(firstId, persistedThird.get(0).id);
        assertEquals("d2", persistedThird.get(0).description);
        assertEquals("Still exactly one row",
                1, mRssDao.findRssItemsByChannelId(rssChannel.id).size());
    }

    @Test
    public void updatedSinceQueries_includeTouchedItemsAndExcludeRetained()
            throws InterruptedException {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        mRssDao.insertRssChannel(rssChannel);

        // sync 1 touches both rows (upsert refreshes their updated_date_time)
        ArrayList<RssItem> sync1 = new ArrayList<>();
        sync1.add(createRssItem("a", "desc", "http://test.com/a", createDate(2026, 1, 1)));
        sync1.add(createRssItem("b", "desc", "http://test.com/b", createDate(2026, 1, 2)));
        mRssDao.upsertRssItemsForChannel(rssChannel, sync1);
        // guarantee the sync-1 stamps are strictly in the past before sync 2 starts
        Thread.sleep(5);

        // sync 2 drops b: only a is parsed again, b is retained with its OLD stamp
        long syncStart2 = System.currentTimeMillis();
        ArrayList<RssItem> sync2 = new ArrayList<>();
        sync2.add(createRssItem("a", "desc v2", "http://test.com/a", createDate(2026, 1, 1)));
        List<RssItem> persisted = mRssDao.upsertRssItemsForChannel(rssChannel, sync2);
        assertEquals("a updated + b retained", 2, persisted.size());

        // the updated-since window sees exactly the rows sync 2 touched
        List<RssItem> touched =
                mRssDao.findRssItemsByChannelIdUpdatedSince(rssChannel.id, syncStart2);
        assertEquals("Only the row touched by sync 2", 1, touched.size());
        assertEquals("http://test.com/a", touched.get(0).link);
        assertEquals("desc v2", touched.get(0).description);

        // the retained row is invisible to the same window: its stamp is older
        List<RssItem> unreadTouched =
                mRssDao.findUnreadRssItemsByChannelIdUpdatedSince(rssChannel.id, syncStart2);
        assertEquals(1, unreadTouched.size());
        assertEquals("http://test.com/a", unreadTouched.get(0).link);

        // read-state flips via markRssItemsReadByChannelId do NOT stamp
        // updated_date_time, so the unread window drops the row while the unfiltered
        // window keeps it
        mRssDao.markRssItemsReadByChannelId(rssChannel.id);
        assertTrue("Read rows leave the unread window",
                mRssDao.findUnreadRssItemsByChannelIdUpdatedSince(rssChannel.id, syncStart2).isEmpty());
        assertEquals("Unfiltered window still sees the touched row",
                1, mRssDao.findRssItemsByChannelIdUpdatedSince(rssChannel.id, syncStart2).size());

        // since 0 includes everything (the workers' missing-key fallback behaves the same)
        assertEquals(2, mRssDao.findRssItemsByChannelIdUpdatedSince(rssChannel.id, 0L).size());
    }

    // ==================================================================================
    // Section 6: keep-read-items pruning (issue #37 phase 2)
    // ==================================================================================

    /**
     * Backdates a row's updated_date_time via raw SQL: the public DAO surface only
     * ever STAMPS updated_date_time to "now" (insert / upsert / updateRssItem), so
     * tests simulate "not seen in a feed since" by writing the stamp directly
     * instead of adding a production backdoor.
     */
    private void backdateUpdated(long itemId, long updatedMillis) {
        mAppDatabase.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE rss_item SET updated_date_time = ? WHERE id = ?",
                new Object[]{updatedMillis, itemId});
    }

    @Test
    public void deleteOldReadRssItems_deletesOnlyOldReadNonFavorites() {
        RssChannel rssChannel = createRssChannel("http://test.com/feed", "Feed");
        RssItem oldRead = createRssItem("old read", "desc",
                "http://test.com/old-read", createDate(2026, 1, 1));
        RssItem oldFavorite = createRssItem("old favorite", "desc",
                "http://test.com/old-favorite", createDate(2026, 1, 1));
        RssItem oldUnread = createRssItem("old unread", "desc",
                "http://test.com/old-unread", createDate(2026, 1, 1));
        RssItem recentRead = createRssItem("recent read", "desc",
                "http://test.com/recent-read", createDate(2026, 1, 1));
        RssItem atCutoffRead = createRssItem("at cutoff read", "desc",
                "http://test.com/at-cutoff-read", createDate(2026, 1, 1));
        mRssDao.insertRssChannel(rssChannel, oldRead, oldFavorite, oldUnread, recentRead, atCutoffRead);
        oldRead.isRead = true;
        mRssDao.updateRssItem(oldRead);
        oldFavorite.isRead = true;
        oldFavorite.isFavorite = true; // favorites must survive the prune, even read+old
        mRssDao.updateRssItem(oldFavorite);
        atCutoffRead.isRead = true;
        mRssDao.updateRssItem(atCutoffRead);

        long cutoff = System.currentTimeMillis();
        // the old rows get stamps strictly before the cutoff
        backdateUpdated(oldRead.id, cutoff - TimeUnit.DAYS.toMillis(30));
        backdateUpdated(oldFavorite.id, cutoff - TimeUnit.DAYS.toMillis(30));
        backdateUpdated(oldUnread.id, cutoff - TimeUnit.DAYS.toMillis(30));
        backdateUpdated(atCutoffRead.id, cutoff); // exactly AT the cutoff: strict < keeps it
        // recently-seen read row: (re-)stamped AFTER the cutoff capture, as the
        // sync upsert would re-stamp an item still carried by its feed
        recentRead.isRead = true;
        mRssDao.updateRssItem(recentRead);

        int deleted = mRssDao.deleteOldReadRssItems(cutoff);

        assertEquals("Only the old read non-favorite is deleted", 1, deleted);
        List<RssItem> stored = mRssDao.findRssItemsByChannelId(rssChannel.id);
        assertEquals(4, stored.size());
        assertNull("Old read non-favorite must be deleted", findByTitle(stored, "old read"));
        assertNotNull("Old READ FAVORITE must never be deleted", findByTitle(stored, "old favorite"));
        assertNotNull("Old UNREAD item must never be deleted", findByTitle(stored, "old unread"));
        assertNotNull("Recently-seen read item must be kept (re-stamped by every sync)",
                findByTitle(stored, "recent read"));
        assertNotNull("Item stamped exactly AT the cutoff must be kept (strict <)",
                findByTitle(stored, "at cutoff read"));
    }

    @Test
    public void deleteOldReadRssItems_countsAcrossAllChannels() {
        RssChannel channel1 = createRssChannel("http://test.com/feed1", "Feed 1");
        RssChannel channel2 = createRssChannel("http://test.com/feed2", "Feed 2");
        RssItem c1Old = createRssItem("c1 old", "desc",
                "http://test.com/c1-old", createDate(2026, 1, 1));
        RssItem c2Old = createRssItem("c2 old", "desc",
                "http://test.com/c2-old", createDate(2026, 1, 1));
        RssItem c2OldUnread = createRssItem("c2 old unread", "desc",
                "http://test.com/c2-old-unread", createDate(2026, 1, 1));
        mRssDao.insertRssChannel(channel1, c1Old);
        mRssDao.insertRssChannel(channel2, c2Old, c2OldUnread);
        c1Old.isRead = true;
        mRssDao.updateRssItem(c1Old);
        c2Old.isRead = true;
        mRssDao.updateRssItem(c2Old);

        long cutoff = System.currentTimeMillis();
        backdateUpdated(c1Old.id, cutoff - TimeUnit.DAYS.toMillis(90));
        backdateUpdated(c2Old.id, cutoff - TimeUnit.DAYS.toMillis(90));
        backdateUpdated(c2OldUnread.id, cutoff - TimeUnit.DAYS.toMillis(90));

        int deleted = mRssDao.deleteOldReadRssItems(cutoff);

        assertEquals("The window is global: both old read non-favorites are counted",
                2, deleted);
        assertTrue(mRssDao.findRssItemsByChannelId(channel1.id).isEmpty());
        assertEquals("Only the unread row of channel 2 survives",
                1, mRssDao.findRssItemsByChannelId(channel2.id).size());
    }

    @Test
    public void deleteOldReadRssItems_includesPausedChannelItems() {
        RssChannel pausedChannel = createRssChannel("http://test.com/feed", "Paused feed");
        pausedChannel.isPaused = true;
        RssItem oldRead = createRssItem("old read", "desc",
                "http://test.com/old-read", createDate(2026, 1, 1));
        mRssDao.insertRssChannel(pausedChannel, oldRead);
        oldRead.isRead = true;
        mRssDao.updateRssItem(oldRead);

        long cutoff = System.currentTimeMillis();
        backdateUpdated(oldRead.id, cutoff - TimeUnit.DAYS.toMillis(30));

        int deleted = mRssDao.deleteOldReadRssItems(cutoff);

        assertEquals("Paused channels age under the same global window", 1, deleted);
        assertTrue("The aged row of the paused channel is gone",
                mRssDao.findRssItemsByChannelId(pausedChannel.id).isEmpty());
    }

    private RssChannel createRssChannel(String url, String feedName) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
        rssChannel.feedName = feedName;
        return rssChannel;
    }

    private RssItem findByTitle(List<RssItem> rssItems, String title) {
        for (RssItem rssItem : rssItems) {
            if (rssItem.title.equals(title)) {
                return rssItem;
            }
        }
        return null;
    }

    private RssItem createRssItem(String title, String description, String link, Date pubDate) {
        RssItem rssItem = new RssItem();
        rssItem.title = title;
        rssItem.description = description;
        rssItem.link = link;
        rssItem.pubDate = pubDate;
        return rssItem;
    }

    private Date createDate(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 12, 0, 0);
        return calendar.getTime();
    }
}
