package m.co.rh.id.a_news_provider;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented tests for {@link OpmlParser#parse(File)} as a pure parser: every
 * rss-type outline's non-empty xmlUrl is collected in file order, raw spelling,
 * duplicates included - DB duplicate checks, normalization and queueing moved to
 * RssService#addNewFeeds and are no longer the parser's job.
 */
@RunWith(AndroidJUnit4.class)
public class OpmlParserTest {
    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private File mOpmlFile;
    private String mDbName;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    @After
    public void tearDown() {
        if (mOpmlFile != null && mOpmlFile.exists()) {
            mOpmlFile.delete();
        }
        if (mTestProvider != null) {
            try {
                // close the Room instance before deleting its file so the delete
                // cannot race an open database handle
                mTestProvider.get(AppDatabase.class).close();
            } catch (Throwable ignored) {
                // database may never have been opened
            }
            mTestProvider.dispose();
        }
        if (mDbName != null) {
            mTestApplication.deleteDatabase(mDbName);
        }
    }

    @Test
    public void parse_returnsEveryRawUrlInFileOrderDuplicatesIncluded() throws IOException {
        createProvider("opmlParseRaw");
        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        mOpmlFile = createOpmlFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <head>\n" +
                "        <title>Raw test</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <outline type=\"rss\" text=\"first\" xmlUrl=\"https://example.com/feed\"/>\n" +
                "        <outline type=\"rss\" text=\"duplicate\" xmlUrl=\"https://example.com/feed\"/>\n" +
                "        <outline type=\"rss\" text=\"fresh feed\" xmlUrl=\"https://fresh.example.com/rss\"/>\n" +
                "    </body>\n" +
                "</opml>\n");

        List<String> rssUrls = opmlParser.parse(mOpmlFile);

        assertEquals("The parser must collect all outlines, duplicates included",
                3, rssUrls.size());
        assertEquals("https://example.com/feed", rssUrls.get(0));
        assertEquals("https://example.com/feed", rssUrls.get(1));
        assertEquals("https://fresh.example.com/rss", rssUrls.get(2));
    }

    @Test
    public void parse_collectsRssOutlinesNestedInsideContainerOutlines() throws IOException {
        createProvider("opmlParseNested");
        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        mOpmlFile = createOpmlFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <body>\n" +
                "        <outline text=\"Folder\">\n" +
                "            <outline type=\"rss\" text=\"Nested feed\" xmlUrl=\"https://nested.example.com/rss\"/>\n" +
                "        </outline>\n" +
                "    </body>\n" +
                "</opml>\n");

        List<String> rssUrls = opmlParser.parse(mOpmlFile);

        assertEquals("Rss outlines nested in a non-rss container outline must be collected",
                1, rssUrls.size());
        assertEquals("https://nested.example.com/rss", rssUrls.get(0));
    }

    @Test
    public void parse_ignoresNonRssOutlines() throws IOException {
        createProvider("opmlParseNonRss");
        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        mOpmlFile = createOpmlFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <body>\n" +
                "        <outline text=\"Html page\" xmlUrl=\"https://html.example.com/page\"/>\n" +
                "        <outline type=\"link\" text=\"Link page\" xmlUrl=\"https://link.example.com/page\"/>\n" +
                "    </body>\n" +
                "</opml>\n");

        List<String> rssUrls = opmlParser.parse(mOpmlFile);

        assertEquals("Outlines without the rss type attribute must be ignored",
                0, rssUrls.size());
    }

    @Test
    public void parse_ignoresEmptyOrMissingXmlUrl() throws IOException {
        createProvider("opmlParseEmptyUrl");
        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        mOpmlFile = createOpmlFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <body>\n" +
                "        <outline type=\"rss\" text=\"Empty url\" xmlUrl=\"\"/>\n" +
                "        <outline type=\"rss\" text=\"Missing url\"/>\n" +
                "        <outline type=\"rss\" text=\"Real feed\" xmlUrl=\"https://real.example.com/rss\"/>\n" +
                "    </body>\n" +
                "</opml>\n");

        List<String> rssUrls = opmlParser.parse(mOpmlFile);

        assertEquals("Only the outline with a non-empty xmlUrl must be collected",
                1, rssUrls.size());
        assertEquals("https://real.example.com/rss", rssUrls.get(0));
    }

    @Test
    public void parse_malformedXmlAfterValidOutlineReturnsPartialList() throws IOException {
        createProvider("opmlParseMalformed");
        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        // the file ends in the middle of the second outline - the parser must log
        // the error and return the outline parsed so far instead of throwing
        mOpmlFile = createOpmlFile("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <body>\n" +
                "        <outline type=\"rss\" text=\"good\" xmlUrl=\"https://good.example.com/rss\"/>\n" +
                "        <outline type=\"rss\" text=\"broken\" xmlUrl=\"https://broken");

        List<String> rssUrls = opmlParser.parse(mOpmlFile);

        assertEquals("The outline parsed before the malformed XML must be returned",
                1, rssUrls.size());
        assertEquals("https://good.example.com/rss", rssUrls.get(0));
    }

    private void createProvider(String dbName) {
        mDbName = dbName;
        mTestProvider = Provider.createProvider(mTestApplication,
                new OpmlTestProviderModule(dbName));
    }

    private File createOpmlFile(String opml) throws IOException {
        File opmlFile = new File(mTestApplication.getFilesDir(), "opml_parse_test.opml");
        try (FileOutputStream outputStream = new FileOutputStream(opmlFile)) {
            outputStream.write(opml.getBytes(StandardCharsets.UTF_8));
        }
        return opmlFile;
    }

    /**
     * Same wiring as the production AppProviderModule for everything OpmlParser
     * touches: base (FileHelper, ILogger, ExecutorService), the database module -
     * OpmlParser's constructor resolves RssDao lazily even though parse() itself
     * no longer touches the database - plus the normalizer and the parser. The
     * command module is left out: queueing the parsed feeds moved to
     * RssService#addNewFeeds.
     */
    private static class OpmlTestProviderModule implements ProviderModule {
        private final String mDbName;

        OpmlTestProviderModule(String dbName) {
            mDbName = dbName;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerModule(new BaseProviderModule());
            providerRegistry.registerModule(new DatabaseProviderModule(mDbName));

            providerRegistry.registerLazy(UrlNormalizer.class, UrlNormalizer::new);
            providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
