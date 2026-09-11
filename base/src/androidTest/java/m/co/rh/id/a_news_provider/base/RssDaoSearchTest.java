package m.co.rh.id.a_news_provider.base;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class RssDaoSearchTest {
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

        List<RssItem> results = mRssDao.searchRssItemsWithLimit(
                "bitcoin", null, null, null, 100);

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

        List<RssItem> results = mRssDao.searchRssItemsWithLimit(
                "50\\% off", null, null, null, 100);

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

        List<RssItem> results = mRssDao.searchRssItemsWithLimit(
                "t_le", null, null, null, 100);

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

        List<RssItem> unreadResults = mRssDao.searchRssItemsWithLimit(
                "news", null, 0, null, 100);
        assertEquals(2, unreadResults.size());
        assertEquals("favorite news", unreadResults.get(0).title);
        assertEquals("unread news", unreadResults.get(1).title);

        List<RssItem> readResults = mRssDao.searchRssItemsWithLimit(
                "news", null, 1, null, 100);
        assertEquals(1, readResults.size());
        assertEquals("read news", readResults.get(0).title);

        List<RssItem> favoriteResults = mRssDao.searchRssItemsWithLimit(
                "news", null, null, 1, 100);
        assertEquals(1, favoriteResults.size());
        assertEquals("favorite news", favoriteResults.get(0).title);

        List<RssItem> unreadFavoriteResults = mRssDao.searchRssItemsWithLimit(
                "news", null, 0, 1, 100);
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

        List<RssItem> newestResults = mRssDao.searchRssItemsWithLimit(
                "bitcoin", null, null, null, 3);
        assertEquals(3, newestResults.size());
        assertEquals("bitcoin news 5", newestResults.get(0).title);
        assertEquals("bitcoin news 4", newestResults.get(1).title);
        assertEquals("bitcoin news 3", newestResults.get(2).title);

        List<RssItem> oldestResults = mRssDao.searchRssItemsWithLimitAsc(
                "bitcoin", null, null, null, 3);
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

        List<RssItem> allResults = mRssDao.searchRssItemsWithLimit(
                "bitcoin", null, null, null, 100);
        assertEquals(2, allResults.size());
        assertEquals("bitcoin in feed 2", allResults.get(0).title);
        assertEquals("bitcoin in feed 1", allResults.get(1).title);

        List<RssItem> channel1Results = mRssDao.searchRssItemsWithLimit(
                "bitcoin", channel1.id, null, null, 100);
        assertEquals(1, channel1Results.size());
        assertEquals("bitcoin in feed 1", channel1Results.get(0).title);
        assertTrue(mRssDao.searchRssItemsWithLimit(
                "bitcoin", channel2.id, null, null, 100).size() == 1);
    }

    private RssChannel createRssChannel(String url, String feedName) {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
        rssChannel.feedName = feedName;
        return rssChannel;
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
