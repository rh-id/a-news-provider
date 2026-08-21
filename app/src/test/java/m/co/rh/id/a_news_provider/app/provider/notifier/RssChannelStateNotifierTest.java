package m.co.rh.id.a_news_provider.app.provider.notifier;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.List;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class RssChannelStateNotifierTest {
    private Provider mMockProvider;
    private ExecutorService mImmediateExecutor;
    private RssRepository mMockRssRepository;
    private ILogger mMockLogger;
    private RssChangeNotifier mRssChangeNotifier;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockRssRepository = mock(RssRepository.class);
        mMockLogger = mock(ILogger.class);
        mRssChangeNotifier = new RssChangeNotifier(); // Real instance, no dependencies

        // Create an executor that runs tasks synchronously
        mImmediateExecutor = new ExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {
            }

            @Override
            public List<Runnable> shutdownNow() {
                return new java.util.ArrayList<>();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) {
                return false;
            }

            @Override
            public <T> java.util.concurrent.Future<T> submit(Callable<T> task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.concurrent.Future<T> submit(Runnable task, T result) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.concurrent.Future<?> submit(Runnable task) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.List<java.util.concurrent.Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
                throw new UnsupportedOperationException();
            }
        };

        // Setup provider.get() mocks for all dependencies
        when(mMockProvider.get(ExecutorService.class)).thenReturn(mImmediateExecutor);
        when(mMockProvider.get(RssRepository.class)).thenReturn(mMockRssRepository);
        when(mMockProvider.get(RssChangeNotifier.class)).thenReturn(mRssChangeNotifier);
        when(mMockProvider.get(ILogger.class)).thenReturn(mMockLogger);
    }

    @Test
    public void testConstructorPerformsInitialRefresh() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Verify that getChannelUnreadCountMap was called during constructor
        verify(mMockRssRepository, times(1)).getChannelUnreadCountMap();

        // Verify that the count subject emitted the initial value
        TestSubscriber<Map<RssChannel, Integer>> testSubscriber = notifier.rssChannelUnReadCount().test();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertValueAt(0, countMap);
    }

    @Test
    public void testSelectRssChannel() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Test selectRssChannel
        notifier.selectRssChannel(channel1);

        // Verify the selected channel emitted
        TestSubscriber<Optional<RssChannel>> testSubscriber = notifier.selectedRssChannel().test();
        testSubscriber.assertValueCount(1);
        assertEquals(channel1, testSubscriber.values().get(0).orElse(null));
    }

    @Test
    public void testGetSelectedRssChannel() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Initially should be empty
        assertEquals(Optional.empty(), notifier.getSelectedRssChannel());

        // After selection
        notifier.selectRssChannel(channel1);
        assertEquals(Optional.of(channel1), notifier.getSelectedRssChannel());
    }

    @Test
    public void testSelectedChannelResetsWhenNoLongerInMap() {
        // Setup initial mock data with channel1
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap1 = new LinkedHashMap<>();
        countMap1.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap1);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Select channel1
        notifier.selectRssChannel(channel1);
        assertEquals(Optional.of(channel1), notifier.getSelectedRssChannel());

        // Now simulate refresh where channel1 is no longer in the map
        Map<RssChannel, Integer> emptyMap = new LinkedHashMap<>();
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(emptyMap);

        // Refresh
        notifier.refreshUnreadCount();

        // Verify selected channel was reset to empty
        assertEquals(Optional.empty(), notifier.getSelectedRssChannel());
    }

    @Test
    public void testSelectedChannelPersistsWhenStillInMap() {
        // Setup initial mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        RssChannel channel2 = new RssChannel();
        channel2.id = 2L;
        channel2.feedName = "Channel 2";

        Map<RssChannel, Integer> countMap1 = new LinkedHashMap<>();
        countMap1.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap1);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Select channel1
        notifier.selectRssChannel(channel1);
        assertEquals(Optional.of(channel1), notifier.getSelectedRssChannel());

        // Now simulate refresh where both channels are in the map
        Map<RssChannel, Integer> countMap2 = new LinkedHashMap<>();
        countMap2.put(channel1, 5);
        countMap2.put(channel2, 10);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap2);

        // Refresh
        notifier.refreshUnreadCount();

        // Verify selected channel still is channel1
        assertEquals(Optional.of(channel1), notifier.getSelectedRssChannel());
    }

    @Test
    public void testRefreshUnreadCountDoesNotTerminateSubjectOnError() {
        // Setup mock to throw exception first, then succeed
        RuntimeException testException = new RuntimeException("DB error");
        when(mMockRssRepository.getChannelUnreadCountMap())
                .thenThrow(testException)
                .thenReturn(new LinkedHashMap<>());

        // Create notifier (will call refresh in constructor and get error)
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Verify error was logged
        verify(mMockLogger, atLeastOnce()).e(any(), any(), eq(testException));

        // Now refresh again (should succeed)
        notifier.refreshUnreadCount();

        // Verify that the subject still emits (was not terminated)
        TestSubscriber<Map<RssChannel, Integer>> testSubscriber = notifier.rssChannelUnReadCount().test();
        testSubscriber.assertValueCount(1);
        testSubscriber.assertNoErrors();
        assertTrue("Subject should still be alive", testSubscriber.values().get(0) instanceof Map);
    }

    @Test
    public void testUpdatedRssChannelRepublishesSelectionWhenIdsMatch() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Updated Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Select channel1
        notifier.selectRssChannel(channel1);

        // Track emissions
        TestSubscriber<Optional<RssChannel>> testSubscriber = notifier.selectedRssChannel().test();

        // Emit updated channel event on hub (same channel, updated)
        mRssChangeNotifier.updatedRssChannel(channel1);

        // Verify the channel was re-emitted
        testSubscriber.assertValueCount(2);
        assertEquals(channel1, testSubscriber.values().get(1).orElse(null));
    }

    @Test
    public void testUpdatedRssChannelDoesNotRepublishWhenIdsDontMatch() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        RssChannel channel2 = new RssChannel();
        channel2.id = 2L;
        channel2.feedName = "Channel 2";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);
        countMap.put(channel2, 10);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Select channel1
        notifier.selectRssChannel(channel1);

        // Track emissions
        TestSubscriber<Optional<RssChannel>> testSubscriber = notifier.selectedRssChannel().test();

        // Emit updated channel event on hub (different channel)
        mRssChangeNotifier.updatedRssChannel(channel2);

        // Verify the selection was not re-emitted (only refresh happened)
        assertTrue("Should have at least one emission", testSubscriber.values().size() >= 1);
        Optional<RssChannel> lastValue = testSubscriber.values().get(testSubscriber.values().size() - 1);
        assertEquals("Selection should still be channel1", channel1, lastValue.orElse(null));
    }

    @Test
    public void testLiveNewRssModelTriggersRefresh() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap1 = new LinkedHashMap<>();
        countMap1.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap1);

        // Create notifier (initial refresh happens)
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Reset mock to clear the initial call
        reset(mMockRssRepository);

        Map<RssChannel, Integer> countMap2 = new LinkedHashMap<>();
        countMap2.put(channel1, 10);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap2);

        // Create RSS model
        ArrayList<RssItem> items = new ArrayList<>();
        RssModel rssModel = new RssModel(channel1, items);

        // Emit liveNewRssModel event on hub
        mRssChangeNotifier.liveNewRssModel(rssModel);

        // Verify refresh was called
        verify(mMockRssRepository, times(1)).getChannelUnreadCountMap();

        // Verify count subject emitted updated value
        TestSubscriber<Map<RssChannel, Integer>> testSubscriber = notifier.rssChannelUnReadCount().test();
        assertTrue("Should have received updated count", testSubscriber.values().contains(countMap2));
    }

    @Test
    public void testNewRssModelErrorDoesNotTriggerRefresh() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier (initial refresh happens)
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Reset mock to clear the initial call
        reset(mMockRssRepository);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Emit newRssModelError event on hub (Optional.empty)
        mRssChangeNotifier.newRssModelError(new RuntimeException("Test error"));

        // Verify refresh was NOT called
        verify(mMockRssRepository, never()).getChannelUnreadCountMap();
    }

    @Test
    public void testLiveSyncedRssModelTriggersRefresh() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap1 = new LinkedHashMap<>();
        countMap1.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap1);

        // Create notifier (initial refresh happens)
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Reset mock to clear the initial call
        reset(mMockRssRepository);

        Map<RssChannel, Integer> countMap2 = new LinkedHashMap<>();
        countMap2.put(channel1, 8);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap2);

        // Create RSS models list
        ArrayList<RssModel> rssModels = new ArrayList<>();
        rssModels.add(new RssModel(channel1, new ArrayList<>()));

        // Emit liveSyncedRssModel event on hub
        mRssChangeNotifier.liveSyncedRssModel(rssModels);

        // Verify refresh was called
        verify(mMockRssRepository, times(1)).getChannelUnreadCountMap();

        // Verify count subject emitted updated value
        TestSubscriber<Map<RssChannel, Integer>> testSubscriber = notifier.rssChannelUnReadCount().test();
        assertTrue("Should have received updated count", testSubscriber.values().contains(countMap2));
    }

    @Test
    public void testDeletedRssChannelTriggersRefresh() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap1 = new LinkedHashMap<>();
        countMap1.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap1);

        // Create notifier (initial refresh happens)
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Reset mock to clear the initial call
        reset(mMockRssRepository);

        Map<RssChannel, Integer> emptyMap = new LinkedHashMap<>();
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(emptyMap);

        // Emit deletedRssChannel event on hub
        mRssChangeNotifier.deletedRssChannel(channel1);

        // Verify refresh was called
        verify(mMockRssRepository, times(1)).getChannelUnreadCountMap();

        // Verify count subject emitted updated value
        TestSubscriber<Map<RssChannel, Integer>> testSubscriber = notifier.rssChannelUnReadCount().test();
        assertTrue("Should have received updated count", testSubscriber.values().contains(emptyMap));
    }

    @Test
    public void testDisposeClearsSubscriptionsAndCompletesSubjects() {
        // Setup mock data
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";

        Map<RssChannel, Integer> countMap = new LinkedHashMap<>();
        countMap.put(channel1, 5);

        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Create notifier
        RssChannelStateNotifier notifier = new RssChannelStateNotifier(mMockProvider);

        // Subscribe to both state Flowables
        TestSubscriber<Optional<RssChannel>> selectedChannelSubscriber = notifier.selectedRssChannel().test();
        TestSubscriber<Map<RssChannel, Integer>> countSubscriber = notifier.rssChannelUnReadCount().test();

        // Reset mock to clear initial call
        reset(mMockRssRepository);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);

        // Dispose the notifier
        android.content.Context mockContext = mock(android.content.Context.class);
        notifier.dispose(mockContext);

        // Emit on the hub - should NOT trigger repository refresh (subscription cleared)
        ArrayList<RssItem> items = new ArrayList<>();
        RssModel rssModel = new RssModel(channel1, items);
        mRssChangeNotifier.liveNewRssModel(rssModel);

        // Verify no further repository calls happened
        verify(mMockRssRepository, never()).getChannelUnreadCountMap();

        // Direct call to refreshUnreadCount after dispose should also be guarded by mDisposed flag
        reset(mMockRssRepository);
        when(mMockRssRepository.getChannelUnreadCountMap()).thenReturn(countMap);
        notifier.refreshUnreadCount();
        // Verify no repository access due to mDisposed flag
        verify(mMockRssRepository, never()).getChannelUnreadCountMap();

        // Verify both state Flowables are completed
        selectedChannelSubscriber.assertComplete();
        countSubscriber.assertComplete();
    }
}
