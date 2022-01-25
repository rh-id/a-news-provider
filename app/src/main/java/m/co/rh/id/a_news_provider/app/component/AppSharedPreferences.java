package m.co.rh.id.a_news_provider.app.component;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.a_news_provider.app.workmanager.ConstantsWork;
import m.co.rh.id.a_news_provider.app.workmanager.PeriodicRssSyncWorker;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class AppSharedPreferences {
    private static final String SHARED_PREFERENCES_NAME = "RssSharedPreferences";
    private ProviderValue<ExecutorService> mExecutorService;
    private ProviderValue<Handler> mHandler;
    private ProviderValue<WorkManager> mWorkManager;
    private ProviderValue<INavigator> mNavigator;
    private SharedPreferences mSharedPreferences;

    private boolean mPeriodicSyncInit;
    private String mPeriodicSyncInitKey;

    private boolean mEnablePeriodicSync;
    private String mEnablePeriodicSyncKey;

    private int mPeriodicSyncRssHour;
    private String mPeriodicSyncRssHourKey;

    private int mSelectedTheme;
    private String mSelectedThemeKey;

    private boolean mOneHandMode;
    private String mOneHandModeKey;

    private boolean mShowCaseRssChannelList;
    private String mShowCaseRssChannelListKey;

    private boolean mShowCaseRssItemList;
    private String mShowCaseRssItemListKey;

    public AppSharedPreferences(Provider provider, Context context) {
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mHandler = provider.lazyGet(Handler.class);
        mWorkManager = provider.lazyGet(WorkManager.class);
        mNavigator = provider.lazyGet(INavigator.class);
        mSharedPreferences = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        initValue();
    }

    private void initValue() {
        mPeriodicSyncInitKey = SHARED_PREFERENCES_NAME
                + ".periodicSyncInit";
        mEnablePeriodicSyncKey = SHARED_PREFERENCES_NAME
                + ".enablePeriodicSync";
        mPeriodicSyncRssHourKey = SHARED_PREFERENCES_NAME
                + ".periodicSyncRssHour";
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";
        mOneHandModeKey = SHARED_PREFERENCES_NAME
                + ".oneHandMode";
        mShowCaseRssChannelListKey = SHARED_PREFERENCES_NAME
                + ".showCaseRssChannelList";
        mShowCaseRssItemListKey = SHARED_PREFERENCES_NAME
                + ".showCaseRssItemList";

        boolean periodicSyncInit = mSharedPreferences.getBoolean(mPeriodicSyncInitKey, false);
        periodicSyncInit(periodicSyncInit);
        boolean enablePeriodicSync = mSharedPreferences.getBoolean(mEnablePeriodicSyncKey, true);
        enablePeriodicSync(enablePeriodicSync);
        int periodicSyncRssHour = mSharedPreferences.getInt(
                mPeriodicSyncRssHourKey, 6);
        periodicSyncRssHour(periodicSyncRssHour);
        if (!isPeriodicSyncInit()) {
            initPeriodicSync();
            periodicSyncInit(true);
        }
        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        setSelectedTheme(selectedTheme);
        boolean oneHandMode = mSharedPreferences.getBoolean(mOneHandModeKey, false);
        oneHandMode(oneHandMode);
        boolean showCaseRssChannelList = mSharedPreferences.getBoolean(mShowCaseRssChannelListKey, false);
        setShowCaseRssChannelList(showCaseRssChannelList);
        boolean showCaseRssItemList = mSharedPreferences.getBoolean(mShowCaseRssItemListKey, false);
        setShowCaseRssItemList(showCaseRssItemList);
    }

    private void initPeriodicSync() {
        if (getPeriodicSyncRssHour() > 0 && isEnablePeriodicSync()) {
            PeriodicWorkRequest.Builder rssSyncBuilder = new PeriodicWorkRequest.Builder(PeriodicRssSyncWorker.class,
                    mPeriodicSyncRssHour, TimeUnit.HOURS);
            rssSyncBuilder.setConstraints(new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build());
            PeriodicWorkRequest periodicWorkRequest = rssSyncBuilder.build();
            mWorkManager.get().enqueueUniquePeriodicWork(ConstantsWork.UNIQUE_PERIODIC_RSS_SYNC,
                    ExistingPeriodicWorkPolicy.REPLACE
                    , periodicWorkRequest);
        } else {
            mWorkManager.get().cancelUniqueWork(ConstantsWork.UNIQUE_PERIODIC_RSS_SYNC);
        }
    }

    private void enablePeriodicSync(boolean b) {
        mEnablePeriodicSync = b;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mEnablePeriodicSyncKey, b)
                        .commit());
    }

    public boolean isEnablePeriodicSync() {
        return mEnablePeriodicSync;
    }

    private void periodicSyncRssHour(int hour) {
        mPeriodicSyncRssHour = hour;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putInt(mPeriodicSyncRssHourKey, hour)
                        .commit());
    }

    public int getPeriodicSyncRssHour() {
        return mPeriodicSyncRssHour;
    }

    public void setPeriodicSyncRssHour(int hour) {
        periodicSyncRssHour(hour);
        initPeriodicSync();
    }

    public boolean isPeriodicSyncInit() {
        return mPeriodicSyncInit;
    }

    private void periodicSyncInit(boolean b) {
        mPeriodicSyncInit = b;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mPeriodicSyncInitKey, b)
                        .commit());
    }

    public void setEnablePeriodicSync(boolean checked) {
        enablePeriodicSync(checked);
        initPeriodicSync();
    }

    private void selectedTheme(int setting) {
        mSelectedTheme = setting;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putInt(mSelectedThemeKey, setting)
                        .commit());
    }

    public void setSelectedTheme(int setting) {
        selectedTheme(setting);
        mHandler.get().post(() ->
                AppCompatDelegate.setDefaultNightMode(setting));
    }

    public int getSelectedTheme() {
        return mSelectedTheme;
    }

    private void oneHandMode(boolean oneHandMode) {
        mOneHandMode = oneHandMode;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mOneHandModeKey, oneHandMode)
                        .commit());
    }

    public boolean isOneHandMode() {
        return mOneHandMode;
    }

    public void setOneHandMode(boolean oneHandMode) {
        oneHandMode(oneHandMode);
        mHandler.get().post(() -> {
            // refresh all route after one hand mode changed
            mNavigator.get().reBuildAllRoute();
        });
    }

    public void setShowCaseRssChannelList(boolean show) {
        mShowCaseRssChannelList = show;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mShowCaseRssChannelListKey, show)
                        .commit());
    }

    public boolean isShowCaseRssChannelList() {
        return mShowCaseRssChannelList;
    }

    public void setShowCaseRssItemList(boolean show) {
        mShowCaseRssItemList = show;
        mExecutorService.get().execute(() ->
                mSharedPreferences.edit().putBoolean(mShowCaseRssItemListKey, show)
                        .commit());
    }

    public boolean isShowCaseRssItemList() {
        return mShowCaseRssItemList;
    }
}
