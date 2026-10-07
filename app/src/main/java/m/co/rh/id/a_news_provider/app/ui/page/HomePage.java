package m.co.rh.id.a_news_provider.app.ui.page;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.constants.Shortcuts;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.MarkAllReadCmd;
import m.co.rh.id.a_news_provider.app.provider.command.OpmlCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChannelStateNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.NewRssChannelSVDialog;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssChannelListSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemListSV;
import m.co.rh.id.a_news_provider.app.util.NotificationPermissionPolicy;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.base.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavActivityLifecycle;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnBackPressed<Activity>, Toolbar.OnMenuItemClickListener, SwipeRefreshLayout.OnRefreshListener, DrawerLayout.DrawerListener, View.OnClickListener, AppBarSV.OnMenuCreated, NavOnActivityResult<Activity>, NavOnRequestPermissionResult, NavActivityLifecycle<Activity> {
    private static final String TAG = HomePage.class.getName();
    private static final int REQUEST_CODE_IMPORT_OPML = 1;
    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 2;
    private static final long BACK_PRESS_EXIT_TIMEOUT_MILLIS = 1000L;
    private static final int ONLINE_STATUS_DEBOUNCE_SECONDS = 1;

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private transient Runnable mPendingDialogCmd;
    @NavInject
    private RssItemListSV mRssItemListSV;
    @NavInject
    private RssChannelListSV mRssChannelListSV;
    private Boolean mLastOnlineStatus;
    private transient long mLastBackPressMilis;
    // true while the POST_NOTIFICATIONS system dialog is expected to be on screen,
    // prevents the resume hook from double-asking before the result is dispatched
    private transient boolean mNotificationPermissionInFlight;
    // passive notification prompt is shown at most once per app session
    private transient boolean mNotificationPermissionPromptedThisSession;
    // intentionally survives dispose/state restore, holds only Long/String keys so it is harmless,
    // keeps the new-item baseline stable across configuration changes
    private final Map<Long, Set<String>> mSyncedRssItemLinksBaseline = new HashMap<>();

    // component
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient RssChannelStateNotifier mRssChannelStateNotifier;
    private transient SyncRssCmd mSyncRssCmd;
    private transient OpmlCmd mOpmlCmd;
    private transient MarkAllReadCmd mMarkAllReadCmd;
    private transient ILogger mLogger;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient Runnable mOnNavigationClicked;
    private transient View mContainerListNews;
    private transient TextView mTextA11yStatus;
    private transient View mRootView;

    public HomePage() {
        mAppBarSV = new AppBarSV(R.menu.home);
        mRssItemListSV = new RssItemListSV();
        mRssChannelListSV = new RssChannelListSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        mRssChangeNotifier = mSvProvider.get(RssChangeNotifier.class);
        mRssChannelStateNotifier = mSvProvider.get(RssChannelStateNotifier.class);
        mSyncRssCmd = mSvProvider.get(SyncRssCmd.class);
        mOpmlCmd = mSvProvider.get(OpmlCmd.class);
        mMarkAllReadCmd = mSvProvider.get(MarkAllReadCmd.class);
        mLogger = mSvProvider.get(ILogger.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = inflateLayout(activity, container);
        mRootView = view;
        setupDrawer(view);
        setupAppBar(view, activity);
        SwipeRefreshLayout swipeRefreshLayout = setupSwipeRefresh(view);
        subscribeEvents(container, swipeRefreshLayout);
        attachChildViews(view, activity, container);
        FloatingActionButton fab = view.findViewById(R.id.fab);
        handleLaunchIntent(activity, fab);
        return view;
    }

    private View inflateLayout(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_home;
        if (mAppSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_home;
        }
        return activity.getLayoutInflater().inflate(layoutId, container, false);
    }

    private void setupDrawer(View view) {
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(this);
        View menuDonation = view.findViewById(R.id.menu_donation);
        menuDonation.setOnClickListener(this);
        mDrawerLayout = view.findViewById(R.id.drawer);
        mDrawerLayout.addDrawerListener(this);
        if (mOnNavigationClicked == null) {
            mOnNavigationClicked = () -> {
                if (!mDrawerLayout.isOpen()) {
                    mDrawerLayout.open();
                }
            };
        }
        if (mIsDrawerOpen) {
            mDrawerLayout.open();
        }
    }

    private void setupAppBar(View view, Activity activity) {
        mAppBarSV.setMenuItemListener(this);
        mAppBarSV.setOnMenuCreated(this);
        mAppBarSV.setTitle(activity.getString(R.string.home));
        mAppBarSV.setNavigationOnClick(mOnNavigationClicked);
    }

    private SwipeRefreshLayout setupSwipeRefresh(View view) {
        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.container_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(this);
        return swipeRefreshLayout;
    }

    private void subscribeEvents(ViewGroup container, SwipeRefreshLayout swipeRefreshLayout) {
        Context context = mSvProvider.getContext();
        String feedSyncSuccess = context.getString(R.string.feed_sync_success);
        String feedSyncError = context.getString(R.string.error_feed_sync_failed);

        mRxDisposer.add("syncRssCmd.syncedRss",
                mSyncRssCmd.syncedRss()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModels -> {
                                    int newItemsCount = countNewSyncedItems(rssModels);
                                    if (!rssModels.isEmpty()) {
                                        Toast.makeText(context,
                                                feedSyncSuccess
                                                , Toast.LENGTH_LONG).show();
                                    }
                                    if (newItemsCount > 0) {
                                        announceNewItems(newItemsCount);
                                    }
                                },
                                throwable ->
                                        mLogger
                                                .e(TAG, feedSyncError, throwable)
                        )
        );

        mRxDisposer.add("rssChannelStateNotifier.selectedRssChannel",
                mRssChannelStateNotifier
                        .selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional -> {
                            if (mDrawerLayout.isOpen()) {
                                mDrawerLayout.close();
                            }
                        })
        );

        mRxDisposer.add("rssChangeNotifier.newRssModel",
                mRssChangeNotifier
                        .liveNewRssModel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModelOptional ->
                                rssModelOptional
                                        .ifPresent(rssModel ->
                                                mLogger
                                                        .i(TAG,
                                                                context.getString(
                                                                        R.string.feed_added,
                                                                        rssModel
                                                                                .getRssChannel()
                                                                                .feedName)))
                        ));

        mRxDisposer.add("rssChangeNotifier.itemsMarkedRead.toast",
                mRssChangeNotifier.getItemsMarkedRead()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional ->
                                        Toast.makeText(context,
                                                context.getString(R.string.marked_all_as_read)
                                                , Toast.LENGTH_SHORT).show(),
                                throwable ->
                                        mLogger
                                                .e(TAG, context.getString(
                                                        R.string.error_message, throwable.getMessage()), throwable)
                        )
        );

        mRxDisposer.add("deviceStatusNotifier.onlineStatus",
                mSvProvider.get(DeviceStatusNotifier.class)
                        .onlineStatus()
                        .distinctUntilChanged()
                        .debounce(ONLINE_STATUS_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(isOnline -> {
                            if (!isOnline) {
                                Snackbar.make(container,
                                                R.string.device_status_offline,
                                                Snackbar.LENGTH_LONG)
                                        .setBackgroundTint(Color.RED)
                                        .setTextColor(Color.WHITE)
                                        .show();
                            } else if (mLastOnlineStatus != null && !mLastOnlineStatus) {
                                Snackbar.make(container,
                                                R.string.device_status_online,
                                                Snackbar.LENGTH_SHORT)
                                        .setBackgroundTint(ContextCompat.getColor(context, R.color.green_500))
                                        .setTextColor(Color.WHITE)
                                        .show();
                            }
                            mLastOnlineStatus = isOnline;
                        },
                        throwable -> {}));

        if (mRssItemListSV.getLoadingFlow() != null) {
            mRxDisposer.add("mRssItemListSV.isLoading",
                    mRssItemListSV.getLoadingFlow()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(swipeRefreshLayout::setRefreshing)
            );
        }
    }

    private void attachChildViews(View view, Activity activity, ViewGroup container) {
        ViewGroup containerChannelList = view.findViewById(R.id.container_list_channel);
        containerChannelList.addView(mRssChannelListSV.buildView(activity, containerChannelList));

        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));

        ViewGroup containerListNews = view.findViewById(R.id.container_list_news);
        containerListNews.addView(mRssItemListSV.buildView(activity, container));
        mContainerListNews = containerListNews;

        mTextA11yStatus = view.findViewById(R.id.text_a11y_status);

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(this);
    }

    /**
     * Track synced item links per channel against the last synced baseline
     *
     * @param rssModels synced RSS models, each containing the full item list of a channel
     * @return count of item links not present in the baseline
     */
    private int countNewSyncedItems(List<RssModel> rssModels) {
        int newItemsCount = 0;
        for (RssModel rssModel : rssModels) {
            RssChannel rssChannel = rssModel.getRssChannel();
            if (rssChannel == null) {
                continue;
            }
            Set<String> baseline = mSyncedRssItemLinksBaseline.get(rssChannel.id);
            if (baseline == null) {
                // first emission establishes the baseline, no announcement
                baseline = new HashSet<>();
                for (RssItem rssItem : rssModel.getRssItems()) {
                    if (rssItem.link != null && !rssItem.link.isEmpty()) {
                        baseline.add(rssItem.link);
                    }
                }
                mSyncedRssItemLinksBaseline.put(rssChannel.id, baseline);
            } else {
                for (RssItem rssItem : rssModel.getRssItems()) {
                    if (rssItem.link != null && !rssItem.link.isEmpty()
                            && baseline.add(rssItem.link)) {
                        newItemsCount++;
                    }
                }
            }
        }
        return newItemsCount;
    }

    private void announceNewItems(int newItemsCount) {
        View containerListNews = mContainerListNews;
        TextView textA11yStatus = mTextA11yStatus;
        if (containerListNews == null || textA11yStatus == null) {
            return;
        }
        // guard against announcements from the periodic background sync while app is backgrounded
        if (!containerListNews.isAttachedToWindow() || !containerListNews.hasWindowFocus()) {
            return;
        }
        String message = mSvProvider.getContext().getResources()
                .getQuantityString(R.plurals.new_items_available, newItemsCount, newItemsCount);
        // text change on the live region view makes TalkBack announce it politely;
        // clear first so an identical count after a previous sync still counts as a change
        // (TalkBack suppresses repeated identical live region announcements)
        textA11yStatus.setText("");
        textA11yStatus.post(() -> textA11yStatus.setText(message));
    }

    private void handleLaunchIntent(Activity activity, FloatingActionButton fab) {
        Intent intent = activity.getIntent();
        String intentAction = intent.getAction();
        if (Shortcuts.NEW_RSS_CHANNEL_ACTION.equals(intentAction)) {
            fab.performClick();
        } else if (Intent.ACTION_SEND.equals(intentAction)) {
            String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
            mNavigator.push((args, activity1) ->
                    new NewRssChannelSVDialog(), NewRssChannelSVDialog.
                    Args.newArgs(sharedText));
        } else if (Intent.ACTION_VIEW.equals(intentAction)) {
            mOpmlCmd.importOpml(intent.getData());
        }
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mPendingDialogCmd = null;
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItemListSV.dispose(activity);
        mRssItemListSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mLogger = null;
        mDrawerLayout = null;
        mContainerListNews = null;
        mTextA11yStatus = null;
        mOnNavigationClicked = null;
        mRootView = null;
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        if (mDrawerLayout.isOpen()) {
            mDrawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < BACK_PRESS_EXIT_TIMEOUT_MILLIS) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                mLogger.i(TAG,
                        activity.getString(R.string.toast_back_press_exit));
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_sync_feed) {
            mSyncRssCmd.execute();
            return true;
        } else if (id == R.id.menu_mark_all_read) {
            mMarkAllReadCmd.execute(null);
            return true;
        } else if (id == R.id.menu_search) {
            mNavigator.push(Routes.SEARCH_RSS_PAGE);
            return true;
        } else if (id == R.id.menu_export_opml) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("asyncExportOpml", mOpmlCmd.exportOpml()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(file -> UiUtils.shareFile(context, file, context.getString(R.string.share_opml)),
                            throwable -> mLogger
                                    .e(TAG, context.getString(R.string.error_exporting_opml),
                                            throwable)));
        } else if (id == R.id.menu_import_opml) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                Activity activity = mNavigator.getActivity();
                String chooserMessage = activity.getString(R.string.menu_import_opml);
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent = Intent.createChooser(intent, chooserMessage);
                activity.startActivityForResult(intent, REQUEST_CODE_IMPORT_OPML);
            }
        }
        return false;
    }

    @Override
    public void onRefresh() {
        mRssItemListSV.refresh();
    }

    @Override
    public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
        // Leave blank
    }

    @Override
    public void onDrawerOpened(@NonNull View drawerView) {
        mIsDrawerOpen = true;
        if (!mAppSharedPreferences.isShowCaseRssChannelList()) {
            mRxDisposer
                    .add("onDrawerOpened_countRssItems",
                            mSvProvider.get(RssQueryCmd.class).countRssItem()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((integer, throwable) -> {
                                        if (throwable == null && integer > 0) {
                                            Activity activity = mNavigator.getActivity();
                                            UiUtils.showRssChannelListShowCase(activity, drawerView);
                                            mAppSharedPreferences.setShowCaseRssChannelList(true);
                                        }
                                    }));
        }
    }

    @Override
    public void onDrawerClosed(@NonNull View drawerView) {
        mIsDrawerOpen = false;
    }

    @Override
    public void onDrawerStateChanged(int newState) {
        // Leave blank
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.fab) {
            mNavigator.push((args, activity1) ->
                    new NewRssChannelSVDialog());
        } else if (id == R.id.menu_settings) {
            mNavigator.push(Routes.SETTINGS_PAGE);
        } else if (id == R.id.menu_donation) {
            mNavigator.push(Routes.DONATIONS_PAGE);
        }
    }

    @Override
    public void onMenuCreated(Menu menu) {
        MenuItem importOpml = menu.findItem(R.id.menu_import_opml);
        importOpml.setVisible(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT);
    }

    @Override
    public void onActivityResult(View currentView, Activity activity, INavigator INavigator, int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_IMPORT_OPML) {
            if (resultCode == Activity.RESULT_OK) {
                mOpmlCmd.importOpml(data.getData());
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            mNotificationPermissionInFlight = false;
            if (grantResults.length == 0) {
                // cancelled request (no user answer): never count it as a denial
                return;
            }
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // granted: sync notifications will work from now on
                return;
            }
            mAppSharedPreferences.setNotificationPermissionDeniedBefore(true);
            mLogger.i(TAG,
                    activity.getString(R.string.error_permission_denied));
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.POST_NOTIFICATIONS)) {
                // permanent denial (2 dialog denials): further dialogs silently no-op,
                // consume the passive budget and redirect the user to the system settings
                int count = mAppSharedPreferences.getNotificationPermissionRequestCount();
                mAppSharedPreferences.setNotificationPermissionRequestCount(Math.max(count, 2));
                showNotificationSettingsSnackbar(activity);
            }
        }
    }

    @Override
    public void onNavActivityResumed(Activity activity) {
        // dispatched to every route on the stack, only act when HomePage is the top route
        if (!isTopRoute()) {
            return;
        }
        // ask at most once per app session, and never while a dialog is still in flight
        if (mNotificationPermissionPromptedThisSession || mNotificationPermissionInFlight) {
            return;
        }
        if (mRootView == null) {
            // view not (yet) built
            return;
        }
        // cheap synchronous pre-check: once the passive budget is exhausted,
        // decide() always returns DO_NOTHING — skip the db query entirely
        // (this also keeps the settings snackbar from repeating after its
        // budget consumption)
        if (mAppSharedPreferences.getNotificationPermissionRequestCount() >= 2) {
            return;
        }
        // gate behind an async precondition: only prompt when at least 1 rss item exists
        mRxDisposer.add("onNavActivityResumed_countRssItems",
                mSvProvider.get(RssQueryCmd.class).countRssItem()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((integer, throwable) -> {
                            if (throwable != null) {
                                mLogger.e(TAG,
                                        throwable.getMessage(), throwable);
                                return;
                            }
                            if (integer == null || integer <= 0) {
                                return;
                            }
                            // route may have changed while the db query ran
                            if (isTopRoute() && !mNotificationPermissionPromptedThisSession
                                    && !mNotificationPermissionInFlight) {
                                decideNotificationPermission(false);
                            }
                        }));
    }

    @SuppressWarnings("rawtypes")
    private boolean isTopRoute() {
        if (mNavigator == null) {
            return false;
        }
        NavRoute currentRoute = mNavigator.getCurrentRoute();
        if (currentRoute == null) {
            return false;
        }
        StatefulView statefulView = currentRoute.getStatefulView();
        return statefulView == this;
    }

    /**
     * Evaluates NotificationPermissionPolicy and acts on the decision.
     *
     * @param explicitRequest true when the ask comes from an explicit user action
     */
    private void decideNotificationPermission(boolean explicitRequest) {
        Activity activity = mNavigator.getActivity();
        if (activity == null) {
            return;
        }
        boolean notificationsEnabled = NotificationManagerCompat.from(activity)
                .areNotificationsEnabled();
        boolean deniedBefore = mAppSharedPreferences.isNotificationPermissionDeniedBefore();
        int count = mAppSharedPreferences.getNotificationPermissionRequestCount();
        boolean rationaleAvailable = ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.POST_NOTIFICATIONS);
        NotificationPermissionPolicy.Decision decision = NotificationPermissionPolicy.decide(
                Build.VERSION.SDK_INT, notificationsEnabled, deniedBefore,
                count, rationaleAvailable, explicitRequest);
        if (decision == NotificationPermissionPolicy.Decision.ASK_DIALOG) {
            mNotificationPermissionPromptedThisSession = true;
            mNotificationPermissionInFlight = true;
            // increment BEFORE showing so a process death mid-dialog is counted
            mAppSharedPreferences.setNotificationPermissionRequestCount(count + 1);
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_NOTIFICATION_PERMISSION);
        } else if (decision == NotificationPermissionPolicy.Decision.SHOW_SETTINGS_SNACKBAR) {
            mNotificationPermissionPromptedThisSession = true;
            // the dialog is a permanent dead end, consume the remaining passive budget
            mAppSharedPreferences.setNotificationPermissionRequestCount(Math.max(count, 2));
            showNotificationSettingsSnackbar(activity);
        }
        // DO_NOTHING: leave blank
    }

    private void showNotificationSettingsSnackbar(Activity activity) {
        View rootView = mRootView;
        if (rootView == null) {
            return;
        }
        Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
        Snackbar.make(rootView,
                        R.string.notification_permission_disabled_hint,
                        Snackbar.LENGTH_LONG)
                .setAction(R.string.notification_permission_open_settings,
                        view -> activity.startActivity(settingsIntent))
                .show();
    }
}
