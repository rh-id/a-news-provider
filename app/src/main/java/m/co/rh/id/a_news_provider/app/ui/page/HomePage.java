package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
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
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnActivityResult;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements Externalizable, RequireComponent<Provider>, NavOnBackPressed<Activity>, Toolbar.OnMenuItemClickListener, SwipeRefreshLayout.OnRefreshListener, DrawerLayout.DrawerListener, View.OnClickListener, AppBarSV.OnMenuCreated, NavOnActivityResult<Activity> {
    private static final String TAG = HomePage.class.getName();
    private static final int REQUEST_CODE_IMPORT_OPML = 1;
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

    // component
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient RssChannelStateNotifier mRssChannelStateNotifier;
    private transient SyncRssCmd mSyncRssCmd;
    private transient OpmlCmd mOpmlCmd;
    private transient MarkAllReadCmd mMarkAllReadCmd;

    // View related
    private transient DrawerLayout mDrawerLayout;
    private transient Runnable mOnNavigationClicked;

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
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = inflateLayout(activity, container);
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
                                    if (!rssModels.isEmpty()) {
                                        Toast.makeText(context,
                                                feedSyncSuccess
                                                , Toast.LENGTH_LONG).show();
                                    }
                                },
                                throwable ->
                                        mSvProvider.get(ILogger.class)
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
                                                mSvProvider.get(ILogger.class)
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
                                        mSvProvider.get(ILogger.class)
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

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(this);
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
        mDrawerLayout = null;
        mOnNavigationClicked = null;
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
                mSvProvider.get(ILogger.class).i(TAG,
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
        } else if (id == R.id.menu_export_opml) {
            Context context = mSvProvider.getContext();
            mRxDisposer.add("asyncExportOpml", mOpmlCmd.exportOpml()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(file -> UiUtils.shareFile(context, file, context.getString(R.string.share_opml)),
                            throwable -> mSvProvider.get(ILogger.class)
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
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        super.writeExternal(objectOutput);
        objectOutput.writeObject(mAppBarSV);
        objectOutput.writeBoolean(mIsDrawerOpen);
        objectOutput.writeObject(mRssItemListSV);
        objectOutput.writeObject(mRssChannelListSV);
        objectOutput.writeBoolean(mLastOnlineStatus != null && mLastOnlineStatus);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws ClassNotFoundException, IOException {
        super.readExternal(objectInput);
        mAppBarSV = (AppBarSV) objectInput.readObject();
        mIsDrawerOpen = objectInput.readBoolean();
        mRssItemListSV = (RssItemListSV) objectInput.readObject();
        mRssChannelListSV = (RssChannelListSV) objectInput.readObject();
        mLastOnlineStatus = objectInput.readBoolean();
    }
}
