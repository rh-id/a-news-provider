package m.co.rh.id.a_news_provider.app.provider.command;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.AppDatabase;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.aprovider.Provider;

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
 */
@RunWith(AndroidJUnit4.class)
public class PauseRssChannelCmdTest {

    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private String mDbName;

    @Before
    public void setUp() {
        mTestApplication = (TestApplication) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getApplicationContext();
    }

    private void createProvider(String dbName) {
        mDbName = dbName;
        mTestProvider = Provider.createProvider(mTestApplication,
                new IntegrationTestAppProviderModule(mTestApplication, dbName));
    }

    @After
    public void tearDown() {
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
    public void testExecutePausePersistsAndNotifies() throws InterruptedException {
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

        assertTrue("Pause must emit an updatedRssChannel event",
                awaitValues(subscriber, 1, 10, TimeUnit.SECONDS));
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
    public void testExecuteUnpauseRestoresNotPaused() throws InterruptedException {
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
        assertTrue(awaitValues(subscriber, 1, 10, TimeUnit.SECONDS));
        cmd.execute(existingChannel.id, false);
        assertTrue("Unpause must emit a second updatedRssChannel event",
                awaitValues(subscriber, 2, 10, TimeUnit.SECONDS));
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
    public void testExecuteUnknownChannelIdDoesNotNotifyOrCrash() throws InterruptedException {
        createProvider("pauseCmdUnknownChannelId");
        RssDao rssDao = mTestProvider.get(RssDao.class);

        TestSubscriber<Optional<RssChannel>> subscriber = mTestProvider
                .get(RssChangeNotifier.class).updatedRssChannel().test();
        mTestProvider.get(PauseRssChannelCmd.class).execute(999, true);

        // no record exists - the command logs record_not_found and must stay silent
        assertFalse("Unknown channel id must not emit any event",
                awaitValues(subscriber, 1, 2, TimeUnit.SECONDS));
        subscriber.assertValueCount(0);
        assertNull(rssDao.findRssChannelById(999));
    }

    /**
     * Waits until the subscriber has received at least {@code count} values or the
     * timeout elapses. RxJava 3.1.12 has no awaitCount(count, timeout, unit) overload
     * and its awaitCount(int) blocks indefinitely, so this polls instead.
     */
    private static boolean awaitValues(TestSubscriber<?> subscriber, int count,
                                       long timeout, TimeUnit unit)
            throws InterruptedException {
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (subscriber.values().size() < count && System.nanoTime() < deadlineNanos) {
            TimeUnit.MILLISECONDS.sleep(50);
        }
        return subscriber.values().size() >= count;
    }
}
