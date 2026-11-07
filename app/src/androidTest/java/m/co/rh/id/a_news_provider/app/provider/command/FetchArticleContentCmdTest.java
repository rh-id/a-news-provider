package m.co.rh.id.a_news_provider.app.provider.command;

import android.app.Application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.observers.TestObserver;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.ArticleContentDao;
import m.co.rh.id.a_news_provider.base.entity.ArticleContent;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.a_news_provider.test.util.ProviderDbRule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link FetchArticleContentCmd} against a real Room
 * database (no mocking framework - Mockito is unreliable on ART), following
 * {@link PauseRssChannelCmdTest}. Moved from the unit-test source set per
 * maintainer decision; the 8 scenarios are preserved 1:1, only the assertion
 * style changed from Mockito verifies to real database/notifier state.
 * <p>
 * Covers the fetch-extract-cache-emit success path, the cached-content skip,
 * the forced refresh, the empty-cached-content refetch, the network failure
 * path (false, no DAO write, no event), the empty-extraction failure, the
 * invalid-link guards and the failed cached-lookup path. The extraction step
 * is REAL (the NetworkProviderModule-registered {@code ArticleExtractor}) and
 * so is every persisted row.
 * <p>
 * The network boundary is replaced WITHOUT Mockito: the package-private
 * {@link TestFetchArticleContentCmd} test subclass overrides the command's
 * package-private {@code fetchArticleBytes} seam - a mocked RequestQueue
 * cannot deliver into a blocking RequestFuture on any thread, and a real
 * network hit would be non-deterministic (matches the no-blocking-waits
 * testing convention). {@link FetchCmdOverrideProviderModule} registers that
 * command (and optionally a failing {@code ArticleContentDao}) FIRST and sets
 * {@code setSkipSameType(true)}, so it wins over the real registrations the
 * wrapped {@link IntegrationTestAppProviderModule} makes; the direct-executor
 * registration makes command execution fully synchronous - assertions need no
 * waiting.
 */
@RunWith(AndroidJUnit4.class)
public class FetchArticleContentCmdTest {

    private static final String LINK = "http://test.com/1";

    /**
     * Small but complete article: enough paragraph text for the real
     * ArticleExtractor to locate an article body (the extraction step is real,
     * not stubbed - it is deterministic and pure).
     */
    private static final String ARTICLE_HTML =
            "<html><body><article><h1>Test Article</h1>"
                    + "<p>This is the first paragraph of the test article carrying "
                    + "plenty of text for the extractor scoring.</p>"
                    + "<p>This is the second paragraph holding the rest of the "
                    + "article body text for the test.</p>"
                    + "</article></body></html>";

    /**
     * Too-short page: the real extractor returns null, treated as failure.
     */
    private static final String TOO_SHORT_HTML =
            "<html><body><p>Sorry</p></body></html>";

    @Rule
    public final ProviderDbRule mDbRule = new ProviderDbRule();

    private TestApplication mTestApplication;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    /**
     * Creates a provider whose test command returns the given fixture bytes
     * from the (overridden) network seam.
     */
    private Provider createProvider(String dbName, byte[] fixtureBytes) {
        return createProvider(dbName, fixtureBytes, null, null);
    }

    /**
     * Creates a provider with an optional seam failure and an optional
     * {@code ArticleContentDao} override (used by the failed-lookup scenario).
     */
    private Provider createProvider(String dbName, byte[] fixtureBytes,
                                    Exception seamFailure,
                                    ArticleContentDao daoOverride) {
        FetchCmdOverrideProviderModule module = new FetchCmdOverrideProviderModule(
                mTestApplication, dbName, fixtureBytes, seamFailure, daoOverride);
        return mDbRule.create(mTestApplication, module, dbName);
    }

    private TestFetchArticleContentCmd getCommand(Provider provider) {
        return (TestFetchArticleContentCmd) provider.get(FetchArticleContentCmd.class);
    }

    @Test
    public void execute_success_extractsUpsertsAndEmits() {
        Provider provider = createProvider("fetchCmdSuccess",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(true);
        assertEquals("the overridden seam must be hit exactly once",
                1, cmd.getFetchAttempts());
        // the extraction is real: the persisted row holds extracted HTML
        ArticleContent stored = articleContentDao.findArticleContentByLink(LINK);
        assertNotNull("the fetched content must be cached under the item link",
                stored);
        assertTrue("the cached content must be the extracted article body",
                stored.content.contains("<p>"));
        assertTrue("the cache write must stamp the fetch time",
                stored.fetchedDateTime > 0);
        subscriber.assertValueCount(1);
        assertEquals("the event must carry the fetched link",
                LINK, subscriber.values().get(0));
    }

    @Test
    public void execute_withCachedContent_skipsFetchAndDoesNotEmit() {
        Provider provider = createProvider("fetchCmdCachedSkip",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8));
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        articleContentDao.upsertArticleContent(
                new ArticleContent(LINK, "<p>cached body</p>", 1L));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(true);
        assertEquals("cached content must serve without a fetch attempt",
                0, cmd.getFetchAttempts());
        subscriber.assertValueCount(0);
        // the cached row must be served untouched: no refetch, no rewrite
        ArticleContent stored = articleContentDao.findArticleContentByLink(LINK);
        assertEquals("<p>cached body</p>", stored.content);
        assertEquals(1L, stored.fetchedDateTime);
    }

    @Test
    public void executeForce_withCachedContent_refetchesAndEmits() {
        Provider provider = createProvider("fetchCmdForceRefetch",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8));
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        articleContentDao.upsertArticleContent(
                new ArticleContent(LINK, "<p>cached body</p>", 1L));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.executeForce(createRssItem(LINK)).test();

        testObserver.assertValue(true);
        assertEquals("the forced refresh must hit the seam exactly once",
                1, cmd.getFetchAttempts());
        assertTrue("the cached row must be overwritten with the fresh extraction",
                articleContentDao.findArticleContentByLink(LINK)
                        .content.contains("first paragraph"));
        subscriber.assertValueCount(1);
        assertEquals(LINK, subscriber.values().get(0));
    }

    @Test
    public void execute_withEmptyCachedContent_refetches() {
        // an empty cached row counts as missing content: the fetch must run
        Provider provider = createProvider("fetchCmdEmptyCached",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8));
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        articleContentDao.upsertArticleContent(
                new ArticleContent(LINK, "", 1L));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(true);
        assertEquals(1, cmd.getFetchAttempts());
        assertTrue("the empty cached row must be replaced with extracted content",
                articleContentDao.findArticleContentByLink(LINK)
                        .content.contains("<p>"));
        subscriber.assertValueCount(1);
        assertEquals(LINK, subscriber.values().get(0));
    }

    @Test
    public void execute_whenFetchFails_returnsFalseWithoutWriteOrEvent() {
        Provider provider = createProvider("fetchCmdFetchFailure", null,
                new ExecutionException(new IllegalStateException("offline")), null);
        TestFetchArticleContentCmd cmd = getCommand(provider);
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(false);
        assertEquals(1, cmd.getFetchAttempts());
        assertNull("a failed fetch must not write a cache row",
                articleContentDao.findArticleContentByLink(LINK));
        subscriber.assertValueCount(0);
    }

    @Test
    public void execute_whenExtractionEmpty_returnsFalseWithoutWriteOrEvent() {
        Provider provider = createProvider("fetchCmdExtractionEmpty",
                TOO_SHORT_HTML.getBytes(StandardCharsets.UTF_8));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(false);
        assertEquals(1, cmd.getFetchAttempts());
        assertNull("an empty extraction must not write a cache row",
                articleContentDao.findArticleContentByLink(LINK));
        subscriber.assertValueCount(0);
    }

    @Test
    public void execute_withInvalidLinks_returnsFalseWithoutAnyInteraction() {
        Provider provider = createProvider("fetchCmdInvalidLinks",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8));
        TestFetchArticleContentCmd cmd = getCommand(provider);
        ArticleContentDao articleContentDao = provider.get(ArticleContentDao.class);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        RssItem nullLinkItem = createRssItem(null);
        RssItem emptyLinkItem = createRssItem("");
        RssItem nonHttpLinkItem = createRssItem("mailto:someone@test.com");

        cmd.execute(nullLinkItem).test().assertValue(false);
        cmd.execute(emptyLinkItem).test().assertValue(false);
        cmd.execute(nonHttpLinkItem).test().assertValue(false);

        assertEquals("invalid links must never reach the network seam",
                0, cmd.getFetchAttempts());
        subscriber.assertValueCount(0);
        assertNull(articleContentDao.findArticleContentByLink(""));
        assertNull(articleContentDao.findArticleContentByLink("mailto:someone@test.com"));
    }

    @Test
    public void execute_whenCachedLookupFails_returnsFalse() {
        FailingLookupDao failingLookupDao = new FailingLookupDao();
        Provider provider = createProvider("fetchCmdLookupFailure",
                ARTICLE_HTML.getBytes(StandardCharsets.UTF_8), null, failingLookupDao);
        TestFetchArticleContentCmd cmd = getCommand(provider);
        TestSubscriber<String> subscriber = provider.get(RssChangeNotifier.class)
                .getArticleContentUpdated().test();

        TestObserver<Boolean> testObserver =
                cmd.execute(createRssItem(LINK)).test();

        testObserver.assertValue(false);
        // the lookup throws before any write: the failing DAO's counting upsert
        // records (and would loudly fail on) any accidental write attempt
        assertEquals("a failed cached lookup must not attempt a write",
                0, failingLookupDao.upsertAttempts);
        subscriber.assertValueCount(0);
    }

    private static RssItem createRssItem(String link) {
        RssItem rssItem = new RssItem();
        rssItem.title = "Test article";
        rssItem.link = link;
        rssItem.description = "teaser";
        return rssItem;
    }

    /**
     * Package-private test subclass replacing the network boundary without a
     * mocking framework: {@code fetchArticleBytes} is the command's seam, and
     * the override either returns the configured fixture bytes (real extraction
     * then runs on them) or throws the configured failure (network error
     * simulation). The attempt counter backs the cached-skip and invalid-link
     * assertions.
     */
    static class TestFetchArticleContentCmd extends FetchArticleContentCmd {
        private final byte[] mFixtureBytes;
        private final Exception mSeamFailure;
        private int mFetchAttempts;

        TestFetchArticleContentCmd(Provider provider, byte[] fixtureBytes,
                                   Exception seamFailure) {
            super(provider);
            mFixtureBytes = fixtureBytes;
            mSeamFailure = seamFailure;
        }

        int getFetchAttempts() {
            return mFetchAttempts;
        }

        @Override
        byte[] fetchArticleBytes(String link) throws Exception {
            mFetchAttempts++;
            if (mSeamFailure != null) {
                throw mSeamFailure;
            }
            return mFixtureBytes;
        }
    }

    /**
     * Hand-written {@code ArticleContentDao} whose lookup always fails (the
     * "db locked" scenario, previously a Mockito thenThrow): reads throw, the
     * counting upsert records - and loudly fails on - any write attempt, so
     * the test can assert none happened.
     */
    static class FailingLookupDao extends ArticleContentDao {
        int upsertAttempts;

        @Override
        public ArticleContent findArticleContentByLink(String link) {
            throw new IllegalStateException("db locked");
        }

        @Override
        public void upsertArticleContent(ArticleContent articleContent) {
            upsertAttempts++;
            throw new IllegalStateException(
                    "no write may be attempted after a failed lookup");
        }

        @Override
        public int deleteOrphanArticleContent() {
            return 0;
        }
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers the test
     * command (and optionally the failing DAO) before it: DefaultProvider
     * throws on duplicate registrations, and {@code setSkipSameType(true)}
     * keeps the FIRST registration - so {@link TestFetchArticleContentCmd}
     * shadows CommandProviderModule's real command (and the DAO override the
     * Room DAO), while everything else (real Room, real ArticleExtractor, real
     * RssChangeNotifier) comes from the wrapped module. The real mechanism that
     * makes this safe: the command is registered with {@code registerLazy},
     * which defers instantiation until the first {@code get()} - by then every
     * module is registered and the ctor's provider.get calls resolve
     * (eager {@code register} would construct the command mid-registration,
     * before the executor/module it depends on exists). The direct-executor
     * registration makes command execution fully synchronous: assertions need
     * no waiting.
     */
    private static class FetchCmdOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;
        private final byte[] mFixtureBytes;
        private final Exception mSeamFailure;
        private final ArticleContentDao mDaoOverride;

        FetchCmdOverrideProviderModule(Application application, String dbName,
                                       byte[] fixtureBytes, Exception seamFailure,
                                       ArticleContentDao daoOverride) {
            mApplication = application;
            mDbName = dbName;
            mFixtureBytes = fixtureBytes;
            mSeamFailure = seamFailure;
            mDaoOverride = daoOverride;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            // registerLazy, NOT register: DefaultProvider.register instantiates
            // the singleton IMMEDIATELY, while neither the ExecutorService nor
            // the wrapped module below is registered yet - the command ctor's
            // first provider.get would throw ProviderNullPointerException.
            // registerLazy defers construction to the first get(), after all
            // modules are in (the NewRssChannelCmdTest precedent for
            // provider-dependent doubles)
            providerRegistry.registerLazy(FetchArticleContentCmd.class,
                    () -> new TestFetchArticleContentCmd(provider, mFixtureBytes,
                            mSeamFailure));
            if (mDaoOverride != null) {
                providerRegistry.register(ArticleContentDao.class,
                        () -> mDaoOverride);
            }
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.setSkipSameType(true);
            providerRegistry.registerModule(new IntegrationTestAppProviderModule(mApplication, mDbName));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
