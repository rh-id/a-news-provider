package m.co.rh.id.a_news_provider;

import android.app.Application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RedirectDuplicateChecker;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.provider.parser.OpmlParser;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.util.UrlNormalizer;
import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.provider.BaseProviderModule;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.a_news_provider.test.FakeWorkManager;
import m.co.rh.id.a_news_provider.test.NoOpLogger;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;

/**
 * Instrumented test for OpmlParser duplicate skipping against a real Room database:
 * outlines already stored in the database and in-file duplicate outlines must be
 * skipped without invoking NewRssChannelCmd.execute; only genuinely new feeds are
 * queued, exactly once.
 */
@RunWith(AndroidJUnit4.class)
public class OpmlParserTest {
    private TestApplication mTestApplication;
    private OpmlTestProviderModule mProviderModule;
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
    public void parse_skipsDatabaseAndInFileDuplicatesAndQueuesOnlyTheNewFeed() throws IOException {
        mDbName = "opmlDuplicateSkip";
        mProviderModule = new OpmlTestProviderModule(mTestApplication, mDbName,
                new FakeWorkManager());
        mTestProvider = Provider.createProvider(mTestApplication, mProviderModule);

        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);

        OpmlParser opmlParser = mTestProvider.get(OpmlParser.class);
        mOpmlFile = createOpmlFile();

        opmlParser.parse(mOpmlFile);

        List<String> executedUrls = mProviderModule.getCountingCmd().executedUrls;
        assertEquals("Only the genuinely new feed must be queued",
                1, executedUrls.size());
        assertEquals("https://fresh.example.com/rss", executedUrls.get(0));
    }

    private File createOpmlFile() throws IOException {
        File opmlFile = new File(mTestApplication.getFilesDir(), "opml_duplicate_test.opml");
        String opml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<opml version=\"2.0\">\n" +
                "    <head>\n" +
                "        <title>Duplicate test</title>\n" +
                "    </head>\n" +
                "    <body>\n" +
                "        <outline type=\"rss\" text=\"db duplicate\" xmlUrl=\"https://example.com/feed\"/>\n" +
                "        <outline type=\"rss\" text=\"in-file duplicate\" xmlUrl=\"https://example.com/feed\"/>\n" +
                "        <outline type=\"rss\" text=\"fresh feed\" xmlUrl=\"https://fresh.example.com/rss\"/>\n" +
                "    </body>\n" +
                "</opml>\n";
        try (FileOutputStream outputStream = new FileOutputStream(opmlFile)) {
            outputStream.write(opml.getBytes(StandardCharsets.UTF_8));
        }
        return opmlFile;
    }

    private static class CountingNewRssChannelCmd extends NewRssChannelCmd {
        private final List<String> executedUrls = new ArrayList<>();

        CountingNewRssChannelCmd(Provider provider) {
            super(provider);
        }

        @Override
        public Single<String> execute(String url) {
            // record only - OpmlParser pre-checks duplicates before calling execute
            executedUrls.add(url);
            return Single.just(url);
        }
    }

    /**
     * Same wiring as the production AppProviderModule for everything OpmlParser touches
     * (base, database, notifier, repository, normalizer, parser) but with the real
     * CommandProviderModule left out and a counting NewRssChannelCmd registered instead.
     * The counting cmd is the ONLY registration of its type, so no duplicate-skip probe
     * ever resolves it during module installation - it is constructed on first use, at
     * parse time, when every dependency is registered.
     * <p>
     * A {@link RedirectDuplicateChecker} with a FAKE resolver ({@code url -> null},
     * never touches the network) is registered because the counting cmd's
     * {@code super(provider)} constructor resolves it - the counting cmd overrides
     * execute(), so the probe never actually runs.
     */
    private static class OpmlTestProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final WorkManager mWorkManager;
        private CountingNewRssChannelCmd mCountingCmd;

        OpmlTestProviderModule(Application application, String dbName, WorkManager workManager) {
            mApplication = application;
            mDbName = dbName;
            mWorkManager = workManager;
        }

        CountingNewRssChannelCmd getCountingCmd() {
            return mCountingCmd;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerModule(new BaseProviderModule());
            providerRegistry.registerModule(new DatabaseProviderModule(mDbName));

            providerRegistry.registerLazy(WorkManager.class, () -> mWorkManager);
            providerRegistry.registerLazy(RedirectDuplicateChecker.class, () ->
                    new RedirectDuplicateChecker(mApplication,
                            provider.get(RssDao.class),
                            new UrlNormalizer(),
                            new NoOpLogger(),
                            url -> null));
            providerRegistry.registerLazy(NewRssChannelCmd.class, () -> {
                if (mCountingCmd == null) {
                    mCountingCmd = new CountingNewRssChannelCmd(provider);
                }
                return mCountingCmd;
            });
            // for rss
            providerRegistry.registerLazy(RssChangeNotifier.class, () -> new RssChangeNotifier());
            providerRegistry.registerLazy(RssChannelStateNotifier.class,
                    () -> new RssChannelStateNotifier(provider));
            providerRegistry.registerLazy(RssRepository.class, () -> new RssRepository(provider));
            providerRegistry.registerLazy(UrlNormalizer.class, UrlNormalizer::new);
            providerRegistry.registerLazy(OpmlParser.class, () -> new OpmlParser(provider));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
