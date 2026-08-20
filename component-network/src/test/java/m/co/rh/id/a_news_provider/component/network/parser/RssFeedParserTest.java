package m.co.rh.id.a_news_provider.component.network.parser;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

import static org.junit.Assert.*;

public class RssFeedParserTest {
    private RssFeedParser parser;
    private ILogger mockLogger;

    @Before
    public void setUp() {
        mockLogger = new ILogger() {
            @Override
            public void v(String tag, String message) {
                // Silent for tests
            }

            @Override
            public void v(String tag, String message, Throwable throwable) {
                // Silent for tests
            }

            @Override
            public void d(String tag, String message) {
                // Silent for tests
            }

            @Override
            public void d(String tag, String message, Throwable throwable) {
                // Silent for tests
            }

            @Override
            public void i(String tag, String message) {
                // Silent for tests
            }

            @Override
            public void i(String tag, String message, Throwable throwable) {
                // Silent for tests
            }

            @Override
            public void w(String tag, String message) {
                // Silent for tests
            }

            @Override
            public void w(String tag, String message, Throwable throwable) {
                // Silent for tests
            }

            @Override
            public void e(String tag, String message) {
                // Silent for tests
            }

            @Override
            public void e(String tag, String message, Throwable throwable) {
                // Silent for tests
            }

            @Override
            public void setLogLevel(int logLevel) {
                // Silent for tests
            }
        };
        parser = new RssFeedParser(new Provider() {
            @Override
            public <I> I get(Class<I> type) {
                return type.cast(mockLogger);
            }

            @Override
            public <I> I tryGet(Class<I> type) {
                return null;
            }

            @Override
            public <I> ProviderValue<I> lazyGet(Class<I> type) {
                return null;
            }

            @Override
            public <I> ProviderValue<I> tryLazyGet(Class<I> type) {
                return null;
            }

            @Override
            public Context getContext() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void dispose() {
            }
        });
    }

    @Test
    public void testParseRss2Feed() throws IOException, XmlPullParserException {
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <description>Test Description</description>\n" +
                "    <link>http://test.com</link>\n" +
                "    <image>\n" +
                "      <url>http://test.com/image.jpg</url>\n" +
                "    </image>\n" +
                "    <item>\n" +
                "      <title>Item 1</title>\n" +
                "      <description>Description 1</description>\n" +
                "      <link>http://test.com/item1</link>\n" +
                "      <pubDate>Mon, 06 Sep 2010 00:01:00 +0000</pubDate>\n" +
                "      <enclosure url=\"http://test.com/video1.mp4\" type=\"video/mp4\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        assertNotNull(model);
        RssChannel channel = model.getRssChannel();
        assertEquals("Test Feed", channel.title);
        assertEquals("Test Feed", channel.feedName);
        assertEquals("Test Description", channel.description);
        assertEquals("http://test.com", channel.link);
        assertEquals("http://test.com/image.jpg", channel.imageUrl);
        assertEquals("http://test.com/feed", channel.url);

        assertEquals(1, model.getRssItems().size());
        RssItem item = model.getRssItems().get(0);
        assertEquals("Item 1", item.title);
        assertEquals("Description 1", item.description);
        assertEquals("http://test.com/item1", item.link);
        assertEquals("http://test.com/video1.mp4", item.mediaVideo);
        assertNull(item.mediaImage);
        assertNotNull(item.pubDate);
    }

    @Test
    public void testParseAtomFeed() throws IOException, XmlPullParserException {
        String atom = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n" +
                "  <title>Atom Feed</title>\n" +
                "  <link href=\"http://test.com\"/>\n" +
                "  <entry>\n" +
                "    <title>Atom Entry</title>\n" +
                "    <summary>Atom Summary</summary>\n" +
                "    <content>Atom Content</content>\n" +
                "    <link href=\"http://test.com/atom-entry\"/>\n" +
                "    <updated>2010-09-06T00:01:00Z</updated>\n" +
                "  </entry>\n" +
                "</feed>";

        RssModel model = parser.parse(atom, "http://test.com/feed");

        assertNotNull(model);
        RssChannel channel = model.getRssChannel();
        assertEquals("Atom Feed", channel.title);
        assertEquals("Atom Feed", channel.feedName);
        assertEquals("http://test.com", channel.link);
        assertEquals("http://test.com/feed", channel.url);

        assertEquals(1, model.getRssItems().size());
        RssItem item = model.getRssItems().get(0);
        assertEquals("Atom Entry", item.title);
        assertEquals("Atom Content", item.description); // Content overrides summary
        assertEquals("http://test.com/atom-entry", item.link);
        assertNotNull(item.pubDate);
    }

    @Test
    public void testParseRdfFeed() throws IOException, XmlPullParserException {
        String rdf = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                "  <channel>\n" +
                "    <title>RDF Feed</title>\n" +
                "    <description>RDF Description</description>\n" +
                "    <link>http://test.com</link>\n" +
                "  </channel>\n" +
                "  <item>\n" +
                "    <title>RDF Item</title>\n" +
                "    <link>http://test.com/rdf-item</link>\n" +
                "    <description>RDF Item Description</description>\n" +
                "  </item>\n" +
                "</rdf:RDF>";

        RssModel model = parser.parse(rdf, "http://test.com/feed");

        assertNotNull(model);
        RssChannel channel = model.getRssChannel();
        assertEquals("RDF Feed", channel.title);
        assertEquals("RDF Feed", channel.feedName);
        assertEquals("RDF Description", channel.description);
        assertEquals("http://test.com", channel.link);
        assertEquals("http://test.com/feed", channel.url);

        assertEquals(1, model.getRssItems().size());
        RssItem item = model.getRssItems().get(0);
        assertEquals("RDF Item", item.title);
        assertEquals("RDF Item Description", item.description);
        assertEquals("http://test.com/rdf-item", item.link);
    }

    @Test
    public void testParseWithEnclosureVideo() throws IOException, XmlPullParserException {
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Video Item</title>\n" +
                "      <link>http://test.com/video</link>\n" +
                "      <enclosure url=\"http://test.com/video.mp4\" type=\"video/mp4\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("Video Item", item.title);
        assertEquals("http://test.com/video", item.link);
        assertEquals("http://test.com/video.mp4", item.mediaVideo);
        assertNull(item.mediaImage);
    }

    @Test
    public void testParseWithEnclosureImage() throws IOException, XmlPullParserException {
        // Test that image enclosure routes to mediaImage
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Default Image</title>\n" +
                "      <link>http://test.com/item</link>\n" +
                "      <enclosure url=\"http://test.com/default.jpg\" type=\"image/jpeg\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("http://test.com/default.jpg", item.mediaImage);
        assertNull(item.mediaVideo);
    }

    @Test(expected = XmlPullParserException.class)
    public void testParseUnknownRootTag() throws IOException, XmlPullParserException {
        String invalid = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<unknown>\n" +
                "  <title>Unknown</title>\n" +
                "</unknown>";

        parser.parse(invalid, "http://test.com/feed");
    }

    @Test(expected = XmlPullParserException.class)
    public void testParseMalformedXml() throws IOException, XmlPullParserException {
        String malformed = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <channel>\n" +
                "    <title>Unclosed tag";

        parser.parse(malformed, "http://test.com/feed");
    }

    @Test
    public void testParseEmptyFeed() throws IOException, XmlPullParserException {
        String empty = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <channel>\n" +
                "    <title>Empty Feed</title>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(empty, "http://test.com/feed");

        assertNotNull(model);
        assertEquals("Empty Feed", model.getRssChannel().title);
        assertTrue(model.getRssItems().isEmpty());
    }

    @Test
    public void testParseRssWithoutChannel() throws IOException, XmlPullParserException {
        // Test that RSS feed without channel child returns null
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\">\n" +
                "  <notchannel>Some other element</notchannel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        // When no channel is found, rssModel remains null
        assertNull(model);
    }

    @Test
    public void testParseWithMediaContentVideo() throws IOException, XmlPullParserException {
        // media:content classifies video only via application/x-shockwave-flash or the medium attribute; video/mp4 MIME routing exists only for enclosure
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Media Content Video</title>\n" +
                "      <link>http://test.com/video</link>\n" +
                "      <media:content url=\"http://test.com/media_video.mp4\" type=\"application/x-shockwave-flash\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("Media Content Video", item.title);
        assertEquals("http://test.com/media_video.mp4", item.mediaVideo);
        assertNull(item.mediaImage);
    }

    @Test
    public void testParseWithMediaContentImage() throws IOException, XmlPullParserException {
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Media Content Image</title>\n" +
                "      <link>http://test.com/image</link>\n" +
                "      <media:content url=\"http://test.com/media_image.jpg\" type=\"image/jpeg\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("Media Content Image", item.title);
        assertEquals("http://test.com/media_image.jpg", item.mediaImage);
        assertNull(item.mediaVideo);
    }

    @Test
    public void testParseWithMediaContentEmptyMimeTypeMediumVideo() throws IOException, XmlPullParserException {
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Media Content Medium Video</title>\n" +
                "      <link>http://test.com/video</link>\n" +
                "      <media:content url=\"http://test.com/medium_video.mp4\" medium=\"video\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("Media Content Medium Video", item.title);
        assertEquals("http://test.com/medium_video.mp4", item.mediaVideo);
        assertNull(item.mediaImage);
    }

    @Test
    public void testParseWithMediaContentImageAndThumbnail() throws IOException, XmlPullParserException {
        String rss = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                "<rss version=\"2.0\" xmlns:media=\"http://search.yahoo.com/mrss/\">\n" +
                "  <channel>\n" +
                "    <title>Test Feed</title>\n" +
                "    <item>\n" +
                "      <title>Media Content Image and Thumbnail</title>\n" +
                "      <link>http://test.com/item</link>\n" +
                "      <media:content url=\"http://test.com/original.jpg\" type=\"image/jpeg\"/>\n" +
                "      <media:thumbnail url=\"http://test.com/thumbnail.jpg\"/>\n" +
                "    </item>\n" +
                "  </channel>\n" +
                "</rss>";

        RssModel model = parser.parse(rss, "http://test.com/feed");

        RssItem item = model.getRssItems().get(0);
        assertEquals("Media Content Image and Thumbnail", item.title);
        assertEquals("http://test.com/thumbnail.jpg", item.mediaImage);
        assertNull(item.mediaVideo);
    }
}