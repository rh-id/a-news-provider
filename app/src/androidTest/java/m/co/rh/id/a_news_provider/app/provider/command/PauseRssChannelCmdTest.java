package m.co.rh.id.a_news_provider.app.provider.command;

import android.app.Application;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.a_news_provider.test.util.DirectExecutorService;
import m.co.rh.id.a_news_provider.test.util.ProviderDbRule;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link PauseRssChannelCmd} against a real Room database
 * (no mocking framework - Mockito is unreliable on ART), following
 * {@link NewRssChannelCmdTest}. Asserts that pausing/unpausing persists the
 * is_paused state and that the RssChangeNotifier emits the updated channel so
 * the UI refreshes. Unpausing round-trips back to false. An unknown channel id
 * must neither crash nor emit (the command logs record_not_found instead).
 * <p>
 * The {@link SyncExecutorOverrideProviderModule} below registers a direct
 * executor FIRST, so command execution is fully synchronous: execute() returns
 * only after the DAO write and the notifier emission, and assertions need no
 * waiting.
 */
@RunWith(AndroidJUnit4.class)
public class PauseRssChannelCmdTest {

    @Rule
    public final ProviderDbRule mDbRule = new ProviderDbRule();

    private TestApplication mTestApplication;
    private Provider mTestProvider;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    private void createProvider(String dbName) {
        // the direct-executor override module makes command execution fully synchronous
        mTestProvider = mDbRule.create(mTestApplication,
                new SyncExecutorOverrideProviderModule(mTestApplication, dbName), dbName);
    }

    @Test
    public void testExecutePausePersistsAndNotifies() {
        createProvider("pauseCmdPersistsAndNotifies");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);
        assertFalse(existingChannel.isPaused);

        TestSubscriber<Optional<RssChannel>> subscriber = mTestProvider
                .get(RssChangeNotifier.class).updatedRssChannel().test();
        mTestProvider.get(PauseRssChannelCmd.class)
                .execute(existingChannel.id, true);

        subscriber.assertValueCount(1);
        Optional<RssChannel> emitted = subscriber.values().get(0);
        assertTrue("The emitted event must carry the updated channel",
                emitted.isPresent());
        assertTrue("The emitted channel must be paused", emitted.get().isPaused);
        // the DAO write happens before the notifier emission - already persisted
        RssChannel stored = rssDao.findRssChannelById(existingChannel.id);
        assertNotNull(stored);
        assertTrue("Pause must persist the is_paused state", stored.isPaused);
    }

    @Test
    public void testExecuteUnpauseRestoresNotPaused() {
        createProvider("pauseCmdUnpauseRestores");
        RssDao rssDao = mTestProvider.get(RssDao.class);
        RssChannel existingChannel = new RssChannel();
        existingChannel.url = "https://example.com/feed";
        existingChannel.feedName = "Example feed";
        rssDao.insertRssChannel(existingChannel);

        PauseRssChannelCmd cmd = mTestProvider.get(PauseRssChannelCmd.class);
        TestSubscriber<Optional<RssChannel>> subscriber = mTestProvider
                .get(RssChangeNotifier.class).updatedRssChannel().test();
        cmd.execute(existingChannel.id, true);
        cmd.execute(existingChannel.id, false);
        subscriber.assertValueCount(2);
        Optional<RssChannel> emitted = subscriber.values().get(1);
        assertTrue(emitted.isPresent());
        assertFalse("The second emission must carry the unpaused channel",
                emitted.get().isPaused);
        RssChannel stored = rssDao.findRssChannelById(existingChannel.id);
        assertNotNull(stored);
        assertFalse("Unpause must restore the not-paused state", stored.isPaused);
    }

    @Test
    public void testExecuteUnknownChannelIdDoesNotNotifyOrCrash() {
        createProvider("pauseCmdUnknownChannelId");
        RssDao rssDao = mTestProvider.get(RssDao.class);

        TestSubscriber<Optional<RssChannel>> subscriber = mTestProvider
                .get(RssChangeNotifier.class).updatedRssChannel().test();
        mTestProvider.get(PauseRssChannelCmd.class).execute(999, true);

        // no record exists - the command logs record_not_found and must stay silent.
        // execute() runs inline, so by the time it returns any emission would
        // already be visible
        subscriber.assertValueCount(0);
        assertNull(rssDao.findRssChannelById(999));
    }

    /**
     * Wraps {@link IntegrationTestAppProviderModule} and registers the direct
     * executor FIRST: DefaultProvider throws on duplicate registrations, and with
     * setSkipSameType(true) it keeps the FIRST registration - so the direct
     * executor wins and the wrapped module's real threaded executor registration
     * is skipped. Commands then run inline on the test thread: execute() returns
     * only after the DAO write and the notifier emission, so assertions need no
     * waiting.
     */
    private static class SyncExecutorOverrideProviderModule implements ProviderModule {
        private final Application mApplication;
        private final String mDbName;

        SyncExecutorOverrideProviderModule(Application application, String dbName) {
            mApplication = application;
            mDbName = dbName;
        }

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.register(ExecutorService.class, DirectExecutorService::new);
            providerRegistry.setSkipSameType(true);
            providerRegistry.registerModule(new IntegrationTestAppProviderModule(mApplication, mDbName));
        }

        @Override
        public void dispose(Provider provider) {
        }
    }
}
