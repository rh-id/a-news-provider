package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.aprovider.Provider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NewRssChannelCmdTest {

    private static final String TEST_URL = "https://example.com/feed";

    private Provider mMockProvider;
    private Context mMockContext;
    private WorkManager mMockWorkManager;
    private RssChangeNotifier mMockRssChangeNotifier;
    private ExecutorService mMockExecutorService;
    private RssDao mMockRssDao;

    private NewRssChannelCmd mNewRssChannelCmd;

    @Before
    public void setUp() {
        mMockProvider = mock(Provider.class);
        mMockContext = mock(Context.class);
        mMockWorkManager = mock(WorkManager.class);
        mMockRssChangeNotifier = mock(RssChangeNotifier.class);
        mMockExecutorService = mock(ExecutorService.class);
        mMockRssDao = mock(RssDao.class);

        when(mMockProvider.getContext()).thenReturn(mMockContext);
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);

        when(mMockProvider.get(WorkManager.class))
                .thenReturn(mMockWorkManager);

        when(mMockProvider.get(RssChangeNotifier.class))
                .thenReturn(mMockRssChangeNotifier);

        when(mMockProvider.get(ExecutorService.class))
                .thenReturn(mMockExecutorService);

        when(mMockProvider.get(RssDao.class))
                .thenReturn(mMockRssDao);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(mMockExecutorService).execute(any(Runnable.class));

        mNewRssChannelCmd = new NewRssChannelCmd(mMockProvider);
    }

    @Test
    public void testDuplicateFeedEmitsExistingChannelAndDoesNotEnqueueWorker() {
        RssChannel existingChannel = new RssChannel();
        existingChannel.feedName = "Existing Feed";
        existingChannel.url = TEST_URL;

        when(mMockRssDao.findRssChannelByUrl(TEST_URL))
                .thenReturn(existingChannel);

        TestSubscriber<RssChannel> subscriber =
                mNewRssChannelCmd.getDuplicateFeed().test();

        mNewRssChannelCmd.enqueueIfNotDuplicate(TEST_URL);

        subscriber.assertValue(existingChannel);

        verify(mMockRssDao).findRssChannelByUrl(TEST_URL);

        verify(mMockWorkManager, never())
                .enqueue(any(OneTimeWorkRequest.class));
    }

    @Test
    public void testNewFeedEnqueuesWorkerAndDoesNotEmitDuplicate() {
        when(mMockRssDao.findRssChannelByUrl(TEST_URL))
                .thenReturn(null);

        TestSubscriber<RssChannel> duplicateSubscriber =
                mNewRssChannelCmd.getDuplicateFeed().test();

        TestSubscriber<String> enqueuedSubscriber =
                mNewRssChannelCmd.getFeedEnqueued().test();

        mNewRssChannelCmd.enqueueIfNotDuplicate(TEST_URL);

        duplicateSubscriber.assertNoValues();

        enqueuedSubscriber.assertValue(TEST_URL);

        verify(mMockWorkManager)
                .enqueue(any(OneTimeWorkRequest.class));
    }
}