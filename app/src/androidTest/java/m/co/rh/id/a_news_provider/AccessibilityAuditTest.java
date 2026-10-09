package m.co.rh.id.a_news_provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import m.co.rh.id.a_news_provider.app.MainActivity;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssChannelItemSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemListSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemSV;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.provider.IntegrationTestAppProviderModule;
import m.co.rh.id.a_news_provider.test.TestApplication;
import m.co.rh.id.anavigator.NavConfiguration;
import m.co.rh.id.anavigator.Navigator;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.anavigator.component.StatefulViewFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented tests for the TalkBack audit accessibility semantics on real
 * inflated views, wired through the app's real Provider.
 * <p>
 * Instead of driving the paged home list (whose async load makes timing
 * nondeterministic), each test hosts the real row components inside a test page
 * and injects them with the same {@code Navigator.injectRequired} call the
 * RecyclerView adapters use, then reads their accessibility node info directly
 * via {@code View#onInitializeAccessibilityNodeInfo}.
 * <p>
 * Assertions cover: the favorite star node (checkable/checked/content
 * description), the row state description (stateDescription on API 30+, content
 * description fallback below), the state-correct custom accessibility action
 * labels, that performing the custom actions flips the state in memory and in
 * the database, the drawer channel row actions and the 48dp touch target sizes.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityAuditTest {

    private static final long AWAIT_TIMEOUT_MILLIS = 20000;
    private static final long AWAIT_POLL_MILLIS = 100;
    private static final String ROUTE_ACCESSIBILITY_TEST_PAGE = "/accessibility_audit_test";
    private static final int DRAWER_ROW_UNREAD_COUNT = 5;

    private String mDbName;
    private Context mTargetContext;
    private TestApplication mTestApplication;
    private Provider mTestProvider;
    private Navigator mNavigator;
    private ActivityScenario<MainActivity> mMainActivityScenario;

    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDbName = "accessibilityAuditTest_" + System.nanoTime();
        // build the provider (and its database wiring) up front, deterministically for
        // every test, so tests that seed the database can use the DAO before the page
        // is launched
        mTestApplication = (TestApplication) mTargetContext.getApplicationContext();
        mTestProvider = Provider.createProvider(mTestApplication,
                new IntegrationTestAppProviderModule(mTestApplication, mDbName));
    }

    @After
    public void tearDown() {
        if (mMainActivityScenario != null) {
            mMainActivityScenario.close();
        }
        if (mTestProvider != null) {
            mTestProvider.dispose();
        }
        if (mNavigator != null && mTestApplication != null) {
            mTestApplication.unregisterActivityLifecycleCallbacks(mNavigator);
            mTestApplication.unregisterComponentCallbacks(mNavigator);
        }
        if (mTargetContext != null) {
            mTargetContext.deleteDatabase(mDbName);
        }
    }

    // ========================================================================
    // RssItemSV row semantics
    // ========================================================================

    @Test
    public void testRssItemRow_starNodeExposesToggleState() {
        RssItem readItem = createRssItem("read item", true, false);
        RssItem readFavoriteItem = createRssItem("read favorite item", true, true);
        RssItem unreadItem = createRssItem("unread item", false, false);
        RssItem unreadFavoriteItem = createRssItem("unread favorite item", false, true);
        RssItem[] items = new RssItem[]{readItem, readFavoriteItem, unreadItem, unreadFavoriteItem};
        TestSvPage page = launchBoundRows(items);
        assertEquals("All seeded rows must be built", items.length, page.getChildViews().size());

        for (int i = 0; i < items.length; i++) {
            View rowView = page.getChildViews().get(i);
            RssItem rssItem = items[i];
            ImageButton buttonFavorite = rowView.findViewById(R.id.button_favorite);
            assertNotNull("Star button missing on row " + i, buttonFavorite);

            AccessibilityNodeInfoCompat starNode = onMain(() -> obtainNodeInfo(buttonFavorite));
            assertTrue("Star must be exposed as a toggle (checkable) on row " + i,
                    starNode.isCheckable());
            assertEquals("Star checked state must match isFavorite on row " + i,
                    rssItem.isFavorite, starNode.isChecked());
            assertEquals("Star content description must be the toggle favorite label on row " + i,
                    mTargetContext.getString(R.string.menu_toggle_favorite),
                    starNode.getContentDescription().toString());

            if (i == 0) {
                awaitOnMain("Row never got laid out", () ->
                        rowView.getWidth() > 0 && rowView.getHeight() > 0);
                float minTouchSize = 48f * rowView.getResources()
                        .getDisplayMetrics().density;
                assertTrue("button_favorite width must be at least 48dp",
                        buttonFavorite.getWidth() >= minTouchSize);
                assertTrue("button_favorite height must be at least 48dp",
                        buttonFavorite.getHeight() >= minTouchSize);
            }
        }
    }

    @Test
    public void testRssItemRow_rowRootExposesStateDescription() {
        RssItem readItem = createRssItem("read item", true, false);
        RssItem readFavoriteItem = createRssItem("read favorite item", true, true);
        RssItem unreadItem = createRssItem("unread item", false, false);
        RssItem unreadFavoriteItem = createRssItem("unread favorite item", false, true);
        RssItem[] items = new RssItem[]{readItem, readFavoriteItem, unreadItem, unreadFavoriteItem};
        TestSvPage page = launchBoundRows(items);

        for (int i = 0; i < items.length; i++) {
            View rowView = page.getChildViews().get(i);
            RssItem rssItem = items[i];
            String expectedStateText = mTargetContext.getString(rssItem.isRead ?
                    R.string.state_read : R.string.state_unread);

            AccessibilityNodeInfoCompat rowNode = onMain(() -> obtainNodeInfo(rowView));
            String stateText = readStateDescription(rowNode);
            assertNotNull("Row " + i + " must expose a state description or a content "
                    + "description fallback", stateText);
            assertTrue("Row " + i + " state description must contain '" + expectedStateText
                    + "' but was: " + stateText, stateText.contains(expectedStateText));
        }
    }

    @Test
    public void testRssItemRow_rowRootExposesStateCorrectCustomActions() {
        RssItem readItem = createRssItem("read item", true, false);
        RssItem readFavoriteItem = createRssItem("read favorite item", true, true);
        RssItem unreadItem = createRssItem("unread item", false, false);
        RssItem unreadFavoriteItem = createRssItem("unread favorite item", false, true);
        RssItem[] items = new RssItem[]{readItem, readFavoriteItem, unreadItem, unreadFavoriteItem};
        TestSvPage page = launchBoundRows(items);

        for (int i = 0; i < items.length; i++) {
            View rowView = page.getChildViews().get(i);
            RssItem rssItem = items[i];
            String expectedReadLabel = mTargetContext.getString(rssItem.isRead ?
                    R.string.mark_as_unread : R.string.mark_as_read);
            String expectedFavoriteLabel = mTargetContext.getString(rssItem.isFavorite ?
                    R.string.favorite_remove : R.string.favorite_add);
            String inverseReadLabel = mTargetContext.getString(rssItem.isRead ?
                    R.string.mark_as_read : R.string.mark_as_unread);
            String inverseFavoriteLabel = mTargetContext.getString(rssItem.isFavorite ?
                    R.string.favorite_add : R.string.favorite_remove);

            List<AccessibilityActionCompat> actions =
                    onMain(() -> obtainNodeInfo(rowView).getActionList());
            assertNotNull("Row " + i + " must expose the state-correct read action '"
                    + expectedReadLabel + "'", findActionByLabel(actions, expectedReadLabel));
            assertNotNull("Row " + i + " must expose the state-correct favorite action '"
                    + expectedFavoriteLabel + "'",
                    findActionByLabel(actions, expectedFavoriteLabel));
            assertNull("Row " + i + " must not expose the inverse read action '"
                    + inverseReadLabel + "' (labels must be re-registered per state)",
                    findActionByLabel(actions, inverseReadLabel));
            assertNull("Row " + i + " must not expose the inverse favorite action '"
                    + inverseFavoriteLabel + "' (labels must be re-registered per state)",
                    findActionByLabel(actions, inverseFavoriteLabel));
        }
    }

    @Test
    public void testRssItemRow_markAsReadActionFlipsReadStateAndPersists() {
        RssItem unreadItem = createRssItem("unread item", false, false);
        TestSvPage page = launchBoundRows(unreadItem);
        View rowView = page.getChildViews().get(0);

        AccessibilityActionCompat markAsReadAction = onMain(() -> findActionByLabel(
                obtainNodeInfo(rowView).getActionList(),
                mTargetContext.getString(R.string.mark_as_read)));
        assertNotNull("Unread row must expose the mark as read action", markAsReadAction);

        Boolean performed = onMain(() ->
                rowView.performAccessibilityAction(markAsReadAction.getId(), null));
        assertTrue("Custom accessibility action must report it was performed", performed);
        assertTrue("Read state must flip in memory", unreadItem.isRead);

        awaitUntil("Mark as read must persist the read state to the database", () -> {
            List<RssItem> storedItems = mTestProvider.get(RssDao.class)
                    .findRssItemsByLink(unreadItem.link);
            return !storedItems.isEmpty() && storedItems.get(0).isRead;
        });

        awaitOnMain("Row must re-register the read action with the read-state label and "
                + "flip its state description", () -> {
            AccessibilityNodeInfoCompat rowNode = obtainNodeInfo(rowView);
            String stateText = readStateDescription(rowNode);
            return stateText != null
                    && stateText.contains(mTargetContext.getString(R.string.state_read))
                    && findActionByLabel(rowNode.getActionList(),
                    mTargetContext.getString(R.string.mark_as_unread)) != null
                    && findActionByLabel(rowNode.getActionList(),
                    mTargetContext.getString(R.string.mark_as_read)) == null;
        });
    }

    @Test
    public void testRssItemRow_addToFavoriteActionFlipsFavoriteStateAndPersists() {
        RssItem unfavoriteItem = createRssItem("unfavorite item", false, false);
        TestSvPage page = launchBoundRows(unfavoriteItem);
        View rowView = page.getChildViews().get(0);
        ImageButton buttonFavorite = rowView.findViewById(R.id.button_favorite);
        assertNotNull(buttonFavorite);

        AccessibilityActionCompat favoriteAddAction = onMain(() -> findActionByLabel(
                obtainNodeInfo(rowView).getActionList(),
                mTargetContext.getString(R.string.favorite_add)));
        assertNotNull("Unfavorited row must expose the add to favorites action",
                favoriteAddAction);

        Boolean performed = onMain(() ->
                rowView.performAccessibilityAction(favoriteAddAction.getId(), null));
        assertTrue("Custom accessibility action must report it was performed", performed);
        assertTrue("Favorite state must flip in memory", unfavoriteItem.isFavorite);

        awaitUntil("Add to favorites must persist the favorite state to the database", () -> {
            List<RssItem> storedItems = mTestProvider.get(RssDao.class)
                    .findRssItemsByLink(unfavoriteItem.link);
            return !storedItems.isEmpty() && storedItems.get(0).isFavorite;
        });

        awaitOnMain("Star must become checked and the favorite action must be re-registered "
                + "with the remove label", () -> {
            AccessibilityNodeInfoCompat starNode = obtainNodeInfo(buttonFavorite);
            AccessibilityNodeInfoCompat rowNode = obtainNodeInfo(rowView);
            return starNode.isChecked()
                    && findActionByLabel(rowNode.getActionList(),
                    mTargetContext.getString(R.string.favorite_remove)) != null
                    && findActionByLabel(rowNode.getActionList(),
                    mTargetContext.getString(R.string.favorite_add)) == null;
        });
    }

    // ========================================================================
    // RssChannelItemSV drawer row semantics
    // ========================================================================

    @Test
    public void testRssChannelRow_exposesDrawerActionsAndEditModeAction() {
        RssChannel rssChannel = createRssChannel();
        RssChannelItemSV channelItemSv = new RssChannelItemSV();
        TestSvPage page = launchTestPage(singleList(channelItemSv));

        TextView textName = page.getChildViews().get(0).findViewById(R.id.text_name);
        assertNotNull(textName);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                channelItemSv.setRssChannelCount(
                        new AbstractMap.SimpleEntry<>(rssChannel, DRAWER_ROW_UNREAD_COUNT)));
        awaitOnMain("Drawer row never rendered the channel name", () ->
                rssChannel.feedName.equals(textName.getText().toString()));

        View drawerRowView = page.getChildViews().get(0);
        List<AccessibilityActionCompat> actions =
                onMain(() -> obtainNodeInfo(drawerRowView).getActionList());
        String[] expectedLabels = new String[]{
                mTargetContext.getString(R.string.edit_channel),
                mTargetContext.getString(R.string.rename),
                mTargetContext.getString(R.string.delete),
                mTargetContext.getString(R.string.open_link),
                mTargetContext.getString(R.string.menu_mark_all_read),
                mTargetContext.getString(R.string.pause)
        };
        for (String expectedLabel : expectedLabels) {
            assertNotNull("Drawer row must expose the '" + expectedLabel + "' action",
                    findActionByLabel(actions, expectedLabel));
        }

        AccessibilityActionCompat editChannelAction = onMain(() -> findActionByLabel(
                obtainNodeInfo(drawerRowView).getActionList(),
                mTargetContext.getString(R.string.edit_channel)));
        Boolean performed = onMain(() ->
                drawerRowView.performAccessibilityAction(editChannelAction.getId(), null));
        assertTrue("Custom accessibility action must report it was performed", performed);

        View buttonRename = drawerRowView.findViewById(R.id.button_rename);
        View buttonDelete = drawerRowView.findViewById(R.id.button_delete);
        awaitOnMain("Edit channel action must switch the drawer row into edit mode", () ->
                buttonRename.getVisibility() == View.VISIBLE
                        && buttonDelete.getVisibility() == View.VISIBLE);
    }

    @Test
    public void testRssChannelRow_pauseActionFlipsStatePersistsAndRelabels() {
        RssChannel rssChannel = createRssChannel();
        mTestProvider.get(RssDao.class).insertRssChannel(rssChannel);
        RssChannelItemSV channelItemSv = new RssChannelItemSV();
        TestSvPage page = launchTestPage(singleList(channelItemSv));

        TextView textName = page.getChildViews().get(0).findViewById(R.id.text_name);
        assertNotNull(textName);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                channelItemSv.setRssChannelCount(
                        new AbstractMap.SimpleEntry<>(rssChannel, DRAWER_ROW_UNREAD_COUNT)));
        awaitOnMain("Drawer row never rendered the channel name", () ->
                rssChannel.feedName.equals(textName.getText().toString()));

        View drawerRowView = page.getChildViews().get(0);
        AccessibilityActionCompat pauseAction = onMain(() -> findActionByLabel(
                obtainNodeInfo(drawerRowView).getActionList(),
                mTargetContext.getString(R.string.pause)));
        assertNotNull("Unpaused drawer row must expose the state-correct pause action",
                pauseAction);

        Boolean performed = onMain(() ->
                drawerRowView.performAccessibilityAction(pauseAction.getId(), null));
        assertTrue("Custom accessibility action must report it was performed", performed);

        awaitUntil("Pause action must persist the paused state to the database", () -> {
            RssChannel stored = mTestProvider.get(RssDao.class)
                    .findRssChannelById(rssChannel.id);
            return stored != null && stored.isPaused;
        });

        // rebind the row with the persisted channel the same way the real adapter
        // does after the RssChangeNotifier event, the action must relabel to unpause
        RssChannel storedChannel = mTestProvider.get(RssDao.class)
                .findRssChannelById(rssChannel.id);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                channelItemSv.setRssChannelCount(
                        new AbstractMap.SimpleEntry<>(storedChannel, DRAWER_ROW_UNREAD_COUNT)));
        awaitOnMain("Drawer row must re-register the action with the unpause label", () ->
                findActionByLabel(obtainNodeInfo(drawerRowView).getActionList(),
                        mTargetContext.getString(R.string.unpause)) != null
                        && findActionByLabel(obtainNodeInfo(drawerRowView).getActionList(),
                        mTargetContext.getString(R.string.pause)) == null);
    }

    // ========================================================================
    // Minimum touch target sizes
    // ========================================================================

    @Test
    public void testRssItemList_sortOrderButtonMeetsMinimumTouchTargetSize() {
        // empty database: the item list renders without rows, the sort button is in the header
        TestSvPage page = launchTestPage(singleList(new RssItemListSV()));
        View listView = page.getChildViews().get(0);
        ImageButton buttonSortOrder = listView.findViewById(R.id.button_sort_order);
        assertNotNull(buttonSortOrder);

        awaitOnMain("Sort order button never got laid out", () ->
                buttonSortOrder.getWidth() > 0 && buttonSortOrder.getHeight() > 0);
        float minTouchSize = 48f * buttonSortOrder.getResources()
                .getDisplayMetrics().density;
        assertTrue("button_sort_order width must be at least 48dp",
                buttonSortOrder.getWidth() >= minTouchSize);
        assertTrue("button_sort_order height must be at least 48dp",
                buttonSortOrder.getHeight() >= minTouchSize);
    }

    // ========================================================================
    // helpers
    // ========================================================================

    /**
     * Seeds one channel with the given items, builds a real {@link RssItemSV} row
     * per item inside the test page and binds the rows. Returns once every row
     * has registered its state-correct custom accessibility actions.
     */
    private TestSvPage launchBoundRows(RssItem... rssItems) {
        RssChannel rssChannel = createRssChannel();
        mTestProvider.get(RssDao.class).insertRssChannel(rssChannel, rssItems);
        List<RssItemSV> rowSvs = new ArrayList<>();
        List<StatefulView<Activity>> testSvs = new ArrayList<>();
        for (RssItem ignored : rssItems) {
            RssItemSV rowSv = new RssItemSV();
            rowSvs.add(rowSv);
            testSvs.add(rowSv);
        }
        TestSvPage page = launchTestPage(testSvs);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < rowSvs.size(); i++) {
                rowSvs.get(i).setRssItem(rssItems[i]);
            }
        });
        for (int i = 0; i < rssItems.length; i++) {
            awaitRowBound(page.getChildViews().get(i), rssItems[i]);
        }
        return page;
    }

    private void awaitRowBound(View rowView, RssItem rssItem) {
        String expectedReadLabel = mTargetContext.getString(rssItem.isRead ?
                R.string.mark_as_unread : R.string.mark_as_read);
        String expectedFavoriteLabel = mTargetContext.getString(rssItem.isFavorite ?
                R.string.favorite_remove : R.string.favorite_add);
        awaitOnMain("Row for item " + rssItem.title + " never registered its "
                + "state-correct custom accessibility actions", () -> {
            List<AccessibilityActionCompat> actions = obtainNodeInfo(rowView).getActionList();
            return findActionByLabel(actions, expectedReadLabel) != null
                    && findActionByLabel(actions, expectedFavoriteLabel) != null;
        });
    }

    /**
     * Returns the row's announced state word: the node state description on
     * API 30+, or the content description fallback below API 30.
     */
    private static String readStateDescription(AccessibilityNodeInfoCompat rowNode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CharSequence stateDescription = rowNode.getStateDescription();
            return stateDescription == null ? null : stateDescription.toString();
        }
        CharSequence contentDescription = rowNode.getContentDescription();
        return contentDescription == null ? null : contentDescription.toString();
    }

    @SuppressWarnings("deprecation")
    private static AccessibilityNodeInfoCompat obtainNodeInfo(View view) {
        // obtain() is deprecated on newer APIs, but the public no-arg constructor of
        // AccessibilityNodeInfo only exists since API 33 while the app supports API 21+
        AccessibilityNodeInfo nodeInfo = AccessibilityNodeInfo.obtain();
        view.onInitializeAccessibilityNodeInfo(nodeInfo);
        return AccessibilityNodeInfoCompat.wrap(nodeInfo);
    }

    private static AccessibilityActionCompat findActionByLabel(
            List<AccessibilityActionCompat> actions, String label) {
        if (actions == null) {
            return null;
        }
        for (AccessibilityActionCompat action : actions) {
            CharSequence actionLabel = action.getLabel();
            if (actionLabel != null && label.equals(actionLabel.toString())) {
                return action;
            }
        }
        return null;
    }

    private static <T> List<T> singleList(T value) {
        List<T> list = new ArrayList<>();
        list.add(value);
        return list;
    }

    private RssChannel createRssChannel() {
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = "https://a11y.test.com/feed";
        rssChannel.title = "A11y Test Feed";
        rssChannel.feedName = "A11y Test Feed";
        return rssChannel;
    }

    private RssItem createRssItem(String title, boolean isRead, boolean isFavorite) {
        RssItem rssItem = new RssItem();
        rssItem.title = title;
        rssItem.link = "https://a11y.test.com/" + title.replace(' ', '-');
        rssItem.isRead = isRead;
        rssItem.isFavorite = isFavorite;
        return rssItem;
    }

    private TestSvPage launchTestPage(List<? extends StatefulView<Activity>> testSvs) {
        AtomicReference<TestSvPage> pageRef = new AtomicReference<>();
        CountDownLatch pageCreated = new CountDownLatch(1);
        Map<String, StatefulViewFactory<Activity, StatefulView>> navMap = new HashMap<>();
        navMap.put(ROUTE_ACCESSIBILITY_TEST_PAGE, (args, activity) -> {
            TestSvPage testSvPage = new TestSvPage(testSvs);
            pageRef.set(testSvPage);
            pageCreated.countDown();
            return testSvPage;
        });
        NavConfiguration.Builder<Activity, StatefulView> navBuilder =
                new NavConfiguration.Builder<>(ROUTE_ACCESSIBILITY_TEST_PAGE, navMap);
        navBuilder.setRequiredComponent(mTestProvider);
        NavConfiguration<Activity, StatefulView> navConfiguration = navBuilder.build();
        mNavigator = new Navigator(MainActivity.class, navConfiguration);
        mTestApplication.registerActivityLifecycleCallbacks(mNavigator);
        mTestApplication.registerComponentCallbacks(mNavigator);
        mTestApplication.setProvider(mTestProvider);
        mTestProvider.get(ProviderRegistry.class).register(INavigator.class, () -> mNavigator);

        mMainActivityScenario = ActivityScenario.launch(MainActivity.class);
        // event-driven wait: the latch is counted down by the nav factory when the
        // test page is created. The Navigator creates routes on the main thread
        // after a background load, so a cross-thread latch is the deterministic
        // wait for page creation
        try {
            if (!pageCreated.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                fail("Test page was not created");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Interrupted while waiting for test page");
        }
        return pageRef.get();
    }

    private <T> T onMain(Supplier<T> supplier) {
        AtomicReference<T> resultRef = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                resultRef.set(supplier.get()));
        return resultRef.get();
    }

    private void awaitOnMain(String failureMessage, Supplier<Boolean> condition) {
        awaitUntil(failureMessage, () -> Boolean.TRUE.equals(onMain(condition)));
    }

    private void awaitUntil(String failureMessage, Supplier<Boolean> condition) {
        long deadline = SystemClock.elapsedRealtime() + AWAIT_TIMEOUT_MILLIS;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            SystemClock.sleep(AWAIT_POLL_MILLIS);
        }
        fail(failureMessage);
    }

    /**
     * Test page that hosts row components and injects them the same way the
     * RecyclerView adapters do: {@code navigator.injectRequired(parent, child)}
     * followed by {@code child.buildView(activity, container)}.
     */
    public static class TestSvPage extends StatefulView<Activity> implements RequireNavigator {

        private final List<? extends StatefulView<Activity>> mTestSvs;
        private transient INavigator mNavigator;
        private transient List<View> mChildViews;

        public TestSvPage(List<? extends StatefulView<Activity>> testSvs) {
            mTestSvs = testSvs;
        }

        @Override
        public void provideNavigator(INavigator navigator) {
            mNavigator = navigator;
        }

        @Override
        protected View createView(Activity activity, ViewGroup container) {
            LinearLayout linearLayout = new LinearLayout(activity);
            linearLayout.setOrientation(LinearLayout.VERTICAL);
            linearLayout.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            mChildViews = new ArrayList<>();
            for (StatefulView<Activity> testSv : mTestSvs) {
                mNavigator.injectRequired(this, testSv);
                View childView = testSv.buildView(activity, linearLayout);
                linearLayout.addView(childView);
                mChildViews.add(childView);
            }
            return linearLayout;
        }

        public List<View> getChildViews() {
            return mChildViews;
        }

        @Override
        public void dispose(Activity activity) {
            super.dispose(activity);
            if (mTestSvs != null) {
                for (StatefulView<Activity> testSv : mTestSvs) {
                    testSv.dispose(activity);
                }
            }
        }
    }
}
