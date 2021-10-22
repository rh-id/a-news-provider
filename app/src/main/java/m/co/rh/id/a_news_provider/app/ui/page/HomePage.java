package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.constants.Shortcuts;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.NewRssChannelSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssChannelListSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.RssItemListSV;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnBackPressed;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class HomePage extends StatefulView<Activity> implements RequireNavigator, NavOnBackPressed {
    private static final String TAG = HomePage.class.getName();

    private transient INavigator mNavigator;
    private AppBarSV mAppBarSV;
    private boolean mIsDrawerOpen;
    private boolean mIsNewRssChannelDialogShow;
    private RssItemListSV mRssItemListSV;
    private NewRssChannelSV mNewRssChannelSV;
    private RssChannelListSV mRssChannelListSV;
    private RssChannel mSelectedRssChannel;
    private transient long mLastBackPressMilis;
    private transient RxDisposer mRxDisposer;

    @Override
    public void provideNavigator(INavigator navigator) {
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(navigator);
        } else {
            mAppBarSV.provideNavigator(navigator);
        }
        mNavigator = navigator;
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        mRssItemListSV = new RssItemListSV();
        mNewRssChannelSV = new NewRssChannelSV();
        mRssChannelListSV = new RssChannelListSV();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.page_home, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        ILogger logger = provider.get(ILogger.class);
        SyncRssCmd syncRssCmd = provider.get(SyncRssCmd.class);
        View menuSettings = view.findViewById(R.id.menu_settings);
        menuSettings.setOnClickListener(view12 -> mNavigator.push(Routes.SETTINGS_PAGE));
        DrawerLayout drawerLayout = view.findViewById(R.id.drawer);
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(View drawerView) {
                mIsDrawerOpen = true;
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                mIsDrawerOpen = false;
            }
        });
        mAppBarSV.setMenu(R.menu.home, item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_sync_feed) {
                syncRssCmd.execute();
                return true;
            }
            return false;
        });
        mAppBarSV.setTitle(activity.getString(R.string.appbar_title_home));
        mAppBarSV.setNavigationOnClickListener(view1 -> {
            if (!drawerLayout.isOpen()) {
                drawerLayout.open();
            }
        });
        if (mIsDrawerOpen) {
            drawerLayout.open();
        }
        mRxDisposer.add("syncRssCmd.syncedRss",
                syncRssCmd.syncedRss()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModels -> {
                                    if (!rssModels.isEmpty()) {
                                        Toast.makeText(activity,
                                                activity.getString(R.string.feed_sync_success)
                                                , Toast.LENGTH_LONG).show();
                                    }
                                },
                                throwable ->
                                        logger.e(TAG,
                                                activity.getString(R.string.error_feed_sync_failed),
                                                throwable)
                        )
        );
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        if (mSelectedRssChannel != null) {
            rssChangeNotifier.selectRssChannel(mSelectedRssChannel);
        }
        mRxDisposer.add("rssChangeNotifier.selectedRssChannel",
                rssChangeNotifier.selectedRssChannel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssChannelOptional -> {
                            if (rssChannelOptional.isPresent()) {
                                if (drawerLayout.isOpen()) {
                                    drawerLayout.close();
                                }
                                mSelectedRssChannel = rssChannelOptional.get();
                            }
                        })
        );
        mRxDisposer.add("rssChangeNotifier.newRssModel",
                rssChangeNotifier.liveNewRssModel()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssModelOptional ->
                                rssModelOptional
                                        .ifPresent(rssModel ->
                                                logger.i(TAG,
                                                        activity.getString(
                                                                R.string.feed_added,
                                                                rssModel
                                                                        .getRssChannel()
                                                                        .feedName)))
                        ));
        DeviceStatusNotifier deviceStatusNotifier = provider.get(DeviceStatusNotifier.class);
        mRxDisposer.add("deviceStatusNotifier.onlineStatus",
                deviceStatusNotifier.onlineStatus().subscribe(isOnline -> {
                    if (!isOnline) {
                        Snackbar.make(container,
                                R.string.device_status_offline,
                                Snackbar.LENGTH_SHORT)
                                .setBackgroundTint(Color.RED)
                                .show();
                    }
                }));
        ViewGroup containerChannelList = view.findViewById(R.id.container_list_channel);
        containerChannelList.addView(mRssChannelListSV.buildView(activity, containerChannelList));

        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));

        ViewGroup containerListNews = view.findViewById(R.id.container_list_news);
        containerListNews.addView(mRssItemListSV.buildView(activity, container));
        if (mIsNewRssChannelDialogShow) {
            showNewRssChannelDialog(activity, container);
        }

        FloatingActionButton fab = view.findViewById(R.id.fab);
        fab.setOnClickListener(view1 -> showNewRssChannelDialog(activity, container));

        SwipeRefreshLayout swipeRefreshLayout = view.findViewById(R.id.container_swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(() -> mRssItemListSV.refresh());
        if (mRssItemListSV.observeRssItems() != null) {
            mRxDisposer.add("mRssItemListSV.observeRssItems",
                    mRssItemListSV.observeRssItems()
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(rssItems -> swipeRefreshLayout.setRefreshing(false))
            );
        }

        // Handle shortcut
        String intentAction = activity.getIntent().getAction();
        if (Shortcuts.NEW_RSS_CHANNEL_ACTION.equals(intentAction)) {
            fab.performClick();
        } else if (Intent.ACTION_SEND.equals(intentAction)) {
            String sharedText = activity.getIntent()
                    .getStringExtra(Intent.EXTRA_TEXT);
            mNewRssChannelSV.setFeedUrl(sharedText);
            fab.performClick();
        }
        return view;
    }

    private void prepareDisposer(Provider provider) {
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
        }
        mRxDisposer = provider.get(RxDisposer.class);
    }

    private void showNewRssChannelDialog(Activity activity, ViewGroup container) {
        View dialogView = mNewRssChannelSV.buildView(activity, container);
        MaterialAlertDialogBuilder alertBuilder = new MaterialAlertDialogBuilder(activity);
        alertBuilder.setView(dialogView);
        alertBuilder.setPositiveButton(R.string.add, (dialogInterface, i) -> {
            //leave blank
        });
        alertBuilder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
            mIsNewRssChannelDialogShow = false;
            mNewRssChannelSV.clearText(dialogView);
        });
        AlertDialog alertDialog = alertBuilder.create();
        alertDialog.show();
        mIsNewRssChannelDialogShow = true;
        Button positiveButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setOnClickListener(v -> {
            if (mNewRssChannelSV.isValid()) {
                mNewRssChannelSV.addNewFeed();
                alertDialog.dismiss();
                mIsNewRssChannelDialogShow = false;
            }
        });
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItemListSV.dispose(activity);
        mRssItemListSV = null;
        if (mSelectedRssChannel != null) {
            mSelectedRssChannel = null;
        }
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }

    @Override
    public void onBackPressed(View currentView, Activity activity, INavigator navigator) {
        DrawerLayout drawerLayout = currentView.findViewById(R.id.drawer);
        if (drawerLayout.isOpen()) {
            drawerLayout.close();
        } else {
            long currentMilis = System.currentTimeMillis();
            if ((currentMilis - mLastBackPressMilis) < 1000) {
                navigator.finishActivity(null);
            } else {
                mLastBackPressMilis = currentMilis;
                BaseApplication.of(activity)
                        .getProvider()
                        .get(ILogger.class)
                        .i(TAG, activity.getString(R.string.toast_back_press_exit));
            }
        }
    }
}
