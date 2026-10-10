package m.co.rh.id.a_news_provider.app.ui.page;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.AutoMarkReadMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.DownloadImageMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LicensesMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.LogMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.MarkReadOnScrollMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.OneHandModeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.RssSyncMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.SwipeArticleNavigationMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.ThemeMenuSV;
import m.co.rh.id.a_news_provider.app.ui.component.settings.VersionMenuSV;
import m.co.rh.id.a_news_provider.app.util.NotificationPermissionPolicy;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.aprovider.Provider;

public class SettingsPage extends StatefulView<Activity> implements RssSyncMenuSV.OnPeriodicSyncEnabledListener, NavOnRequestPermissionResult {

    private static final String TAG = SettingsPage.class.getName();
    private static final int REQUEST_CODE_NOTIFICATION_PERMISSION = 3;

    @NavInject
    private transient Provider mProvider; // global provider
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private ArrayList<StatefulView<Activity>> mStatefulViews;
    private RssSyncMenuSV mRssSyncMenuSV;

    // View related
    private transient View mRootView;

    public SettingsPage() {
        mAppBarSV = new AppBarSV();
        mStatefulViews = new ArrayList<>();
        mRssSyncMenuSV = new RssSyncMenuSV();
        mStatefulViews.add(mRssSyncMenuSV);
        AutoMarkReadMenuSV autoMarkReadMenuSV = new AutoMarkReadMenuSV();
        mStatefulViews.add(autoMarkReadMenuSV);
        MarkReadOnScrollMenuSV markReadOnScrollMenuSV = new MarkReadOnScrollMenuSV();
        mStatefulViews.add(markReadOnScrollMenuSV);
        SwipeArticleNavigationMenuSV swipeArticleNavigationMenuSV = new SwipeArticleNavigationMenuSV();
        mStatefulViews.add(swipeArticleNavigationMenuSV);
        ThemeMenuSV themeMenuSV = new ThemeMenuSV();
        mStatefulViews.add(themeMenuSV);
        OneHandModeMenuSV oneHandModeMenuSV = new OneHandModeMenuSV();
        mStatefulViews.add(oneHandModeMenuSV);
        DownloadImageMenuSV downloadImageMenuSV = new DownloadImageMenuSV();
        mStatefulViews.add(downloadImageMenuSV);
        LogMenuSV logMenuSV = new LogMenuSV();
        mStatefulViews.add(logMenuSV);
        LicensesMenuSV licensesMenuSV = new LicensesMenuSV();
        mStatefulViews.add(licensesMenuSV);
        VersionMenuSV versionMenuSV = new VersionMenuSV();
        mStatefulViews.add(versionMenuSV);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_settings;
        AppSharedPreferences appSharedPreferences = mProvider.get(AppSharedPreferences.class);
        if (appSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_settings;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        mRootView = view;
        mRssSyncMenuSV.setOnPeriodicSyncEnabledListener(this);
        mAppBarSV.setTitle(activity.getString(R.string.settings));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ViewGroup content = view.findViewById(R.id.content);
        for (StatefulView<Activity> statefulView : mStatefulViews) {
            LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            content.addView(statefulView.buildView(activity, content), lparams);
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        if (mStatefulViews != null && !mStatefulViews.isEmpty()) {
            for (StatefulView<Activity> statefulView : mStatefulViews) {
                statefulView.dispose(activity);
            }
            mStatefulViews.clear();
            mStatefulViews = null;
        }
        mRssSyncMenuSV = null;
        mRootView = null;
        mProvider = null;
    }

    @Override
    public void onPeriodicSyncEnabled() {
        Activity activity = mNavigator.getActivity();
        if (activity == null) {
            return;
        }
        AppSharedPreferences appSharedPreferences = mProvider.get(AppSharedPreferences.class);
        // re-arm FIRST: the explicit user action grants one fresh dialog ask even when
        // the passive anti-nag budget was already consumed (clamp down to at most 1)
        int count = appSharedPreferences.getNotificationPermissionRequestCount();
        appSharedPreferences.setNotificationPermissionRequestCount(Math.min(count, 1));
        decideNotificationPermission(activity, appSharedPreferences, true);
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_NOTIFICATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // granted: sync notifications will work from now on
                return;
            }
            AppSharedPreferences appSharedPreferences = mProvider.get(AppSharedPreferences.class);
            appSharedPreferences.setNotificationPermissionDeniedBefore(true);
            mProvider.get(ILogger.class).i(TAG,
                    activity.getString(R.string.error_permission_denied));
            if (!ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    Manifest.permission.POST_NOTIFICATIONS)) {
                // permanent denial (2 dialog denials): further dialogs silently no-op,
                // consume the passive budget and redirect the user to the system settings
                int count = appSharedPreferences.getNotificationPermissionRequestCount();
                appSharedPreferences.setNotificationPermissionRequestCount(Math.max(count, 2));
                showNotificationSettingsSnackbar(activity);
            }
        }
    }

    /**
     * Evaluates NotificationPermissionPolicy and acts on the decision.
     *
     * @param explicitRequest true when the ask comes from an explicit user action
     */
    private void decideNotificationPermission(Activity activity,
                                              AppSharedPreferences appSharedPreferences,
                                              boolean explicitRequest) {
        boolean notificationsEnabled = NotificationManagerCompat.from(activity)
                .areNotificationsEnabled();
        boolean deniedBefore = appSharedPreferences.isNotificationPermissionDeniedBefore();
        int count = appSharedPreferences.getNotificationPermissionRequestCount();
        boolean rationaleAvailable = ActivityCompat.shouldShowRequestPermissionRationale(activity,
                Manifest.permission.POST_NOTIFICATIONS);
        NotificationPermissionPolicy.Decision decision = NotificationPermissionPolicy.decide(
                Build.VERSION.SDK_INT, notificationsEnabled, deniedBefore,
                count, rationaleAvailable, explicitRequest);
        if (decision == NotificationPermissionPolicy.Decision.ASK_DIALOG) {
            // increment BEFORE showing so a process death mid-dialog is counted
            appSharedPreferences.setNotificationPermissionRequestCount(count + 1);
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_CODE_NOTIFICATION_PERMISSION);
        } else if (decision == NotificationPermissionPolicy.Decision.SHOW_SETTINGS_SNACKBAR) {
            // the dialog is a permanent dead end; no budget change here,
            // the ask budget was intentionally re-armed by the explicit action
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
