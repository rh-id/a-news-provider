package m.co.rh.id.a_news_provider.app.provider.notifier;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;

import static org.junit.Assert.*;

public class RssChangeNotifierTest {

    @Test
    public void testLiveNewRssModelEmitsValue() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<Optional<RssModel>> testSubscriber = notifier.liveNewRssModel().test();
        
        RssChannel channel = new RssChannel();
        channel.id = 1L;
        channel.feedName = "Test Channel";
        
        ArrayList<RssItem> items = new ArrayList<>();
        RssModel rssModel = new RssModel(channel, items);
        
        notifier.liveNewRssModel(rssModel);
        
        testSubscriber.assertValueCount(1);
        assertEquals(rssModel, testSubscriber.values().get(0).orElse(null));
    }

    @Test
    public void testLiveNewRssModelEmitsNull() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<Optional<RssModel>> testSubscriber = notifier.liveNewRssModel().test();
        
        notifier.liveNewRssModel(null);
        
        testSubscriber.assertValueCount(1);
        assertEquals(Optional.empty(), testSubscriber.values().get(0));
    }

    @Test
    public void testNewRssModelErrorEmitsEmpty() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<Optional<RssModel>> testSubscriber = notifier.liveNewRssModel().test();
        
        Throwable error = new RuntimeException("Test error");
        notifier.newRssModelError(error);
        
        testSubscriber.assertValueCount(1);
        assertEquals(Optional.empty(), testSubscriber.values().get(0));
    }

    @Test
    public void testLiveSyncedRssModelEmitsValues() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<List<RssModel>> testSubscriber = notifier.liveSyncedRssModel().test();
        
        List<RssModel> rssModels = new ArrayList<>();
        RssChannel channel = new RssChannel();
        channel.id = 1L;
        channel.feedName = "Test Channel";
        ArrayList<RssItem> items = new ArrayList<>();
        RssModel model1 = new RssModel(channel, items);
        rssModels.add(model1);
        
        notifier.liveSyncedRssModel(rssModels);
        
        testSubscriber.assertValueCount(1);
        assertEquals(rssModels, testSubscriber.values().get(0));
    }

    @Test
    public void testUpdatedRssChannelEmitsValue() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        // Use reflection to access the private subject for testing
        java.lang.reflect.Field field = RssChangeNotifier.class.getDeclaredField("mUpdatedRssChannelPublishSubject");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        PublishSubject<Optional<RssChannel>> subject = (PublishSubject<Optional<RssChannel>>) field.get(notifier);
        
        TestSubscriber<Optional<RssChannel>> testSubscriber = Flowable.fromObservable(
                subject, BackpressureStrategy.BUFFER
        ).test();
        
        RssChannel channel = new RssChannel();
        channel.id = 1L;
        channel.feedName = "Test Channel";
        
        notifier.updatedRssChannel(channel);
        
        testSubscriber.assertValueCount(1);
        assertEquals(channel, testSubscriber.values().get(0).orElse(null));
    }

    @Test
    public void testUpdatedRssChannelEmitsNull() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        // Use reflection to access the private subject for testing
        java.lang.reflect.Field field = RssChangeNotifier.class.getDeclaredField("mUpdatedRssChannelPublishSubject");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        PublishSubject<Optional<RssChannel>> subject = (PublishSubject<Optional<RssChannel>>) field.get(notifier);
        
        TestSubscriber<Optional<RssChannel>> testSubscriber = Flowable.fromObservable(
                subject, BackpressureStrategy.BUFFER
        ).test();
        
        notifier.updatedRssChannel(null);
        
        testSubscriber.assertValueCount(1);
        assertEquals(Optional.empty(), testSubscriber.values().get(0));
    }

    @Test
    public void testUpdatedRssItemEmitsValue() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<RssItem> testSubscriber = notifier.getUpdatedRssItem().test();
        
        RssItem item = new RssItem();
        item.id = 1L;
        item.title = "Test Item";
        
        notifier.updatedRssItem(item);
        
        testSubscriber.assertValueCount(1);
        assertEquals(item, testSubscriber.values().get(0));
    }

    @Test
    public void testConstructorCreatesAllSubjects() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();

        java.lang.reflect.Field addedField = RssChangeNotifier.class.getDeclaredField("mAddedRssModelPublishSubject");
        addedField.setAccessible(true);
        assertNotNull("Added RSS model subject should be created", addedField.get(notifier));

        java.lang.reflect.Field updatedField = RssChangeNotifier.class.getDeclaredField("mUpdatedRssChannelPublishSubject");
        updatedField.setAccessible(true);
        assertNotNull("Updated RSS channel subject should be created", updatedField.get(notifier));

        java.lang.reflect.Field syncedField = RssChangeNotifier.class.getDeclaredField("mSyncedRssModelPublishSubject");
        syncedField.setAccessible(true);
        assertNotNull("Synced RSS model subject should be created", syncedField.get(notifier));

        java.lang.reflect.Field itemField = RssChangeNotifier.class.getDeclaredField("mUpdatedRssItemSubject");
        itemField.setAccessible(true);
        assertNotNull("Updated RSS item subject should be created", itemField.get(notifier));

        java.lang.reflect.Field deletedField = RssChangeNotifier.class.getDeclaredField("mDeletedRssChannelPublishSubject");
        deletedField.setAccessible(true);
        assertNotNull("Deleted RSS channel subject should be created", deletedField.get(notifier));
    }

    @Test
    public void testMultipleEmittedValues() {
        RssChangeNotifier notifier = new RssChangeNotifier();
        
        TestSubscriber<Optional<RssModel>> testSubscriber = notifier.liveNewRssModel().test();
        
        RssChannel channel1 = new RssChannel();
        channel1.id = 1L;
        channel1.feedName = "Channel 1";
        ArrayList<RssItem> items1 = new ArrayList<>();
        RssModel model1 = new RssModel(channel1, items1);
        
        RssChannel channel2 = new RssChannel();
        channel2.id = 2L;
        channel2.feedName = "Channel 2";
        ArrayList<RssItem> items2 = new ArrayList<>();
        RssModel model2 = new RssModel(channel2, items2);
        
        notifier.liveNewRssModel(model1);
        notifier.liveNewRssModel(model2);
        notifier.newRssModelError(new RuntimeException("Error"));
        
        testSubscriber.assertValueCount(3);
        assertEquals(model1, testSubscriber.values().get(0).orElse(null));
        assertEquals(model2, testSubscriber.values().get(1).orElse(null));
        assertEquals(Optional.empty(), testSubscriber.values().get(2));
    }

    @Test
    public void testDeletedRssChannelEmitsValue() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();

        // Use reflection to access the private subject for testing
        java.lang.reflect.Field field = RssChangeNotifier.class.getDeclaredField("mDeletedRssChannelPublishSubject");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        PublishSubject<Optional<RssChannel>> subject = (PublishSubject<Optional<RssChannel>>) field.get(notifier);

        TestSubscriber<Optional<RssChannel>> testSubscriber = Flowable.fromObservable(
                subject, BackpressureStrategy.BUFFER
        ).test();

        RssChannel channel = new RssChannel();
        channel.id = 1L;
        channel.feedName = "Test Channel";

        notifier.deletedRssChannel(channel);

        testSubscriber.assertValueCount(1);
        assertEquals(channel, testSubscriber.values().get(0).orElse(null));
    }

    @Test
    public void testDeletedRssChannelEmitsNull() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();

        // Use reflection to access the private subject for testing
        java.lang.reflect.Field field = RssChangeNotifier.class.getDeclaredField("mDeletedRssChannelPublishSubject");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        PublishSubject<Optional<RssChannel>> subject = (PublishSubject<Optional<RssChannel>>) field.get(notifier);

        TestSubscriber<Optional<RssChannel>> testSubscriber = Flowable.fromObservable(
                subject, BackpressureStrategy.BUFFER
        ).test();

        notifier.deletedRssChannel(null);

        testSubscriber.assertValueCount(1);
        assertEquals(Optional.empty(), testSubscriber.values().get(0));
    }

    @Test
    public void testDeletedRssChannelFlowable() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();

        TestSubscriber<Optional<RssChannel>> testSubscriber = notifier.deletedRssChannel().test();

        RssChannel channel = new RssChannel();
        channel.id = 1L;
        channel.feedName = "Test Channel";

        notifier.deletedRssChannel(channel);

        testSubscriber.assertValueCount(1);
        assertEquals(channel, testSubscriber.values().get(0).orElse(null));
    }

    @Test
    public void testConstructorCreatesDeletedSubject() throws Exception {
        RssChangeNotifier notifier = new RssChangeNotifier();

        java.lang.reflect.Field deletedField = RssChangeNotifier.class.getDeclaredField("mDeletedRssChannelPublishSubject");
        deletedField.setAccessible(true);
        assertNotNull("Deleted RSS channel subject should be created", deletedField.get(notifier));
    }
}
