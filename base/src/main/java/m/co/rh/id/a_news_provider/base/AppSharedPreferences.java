package m.co.rh.id.a_news_provider.base;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import m.co.rh.id.a_news_provider.base.rx.SerialBehaviorSubject;
import m.co.rh.id.aprovider.Provider;

public class AppSharedPreferences {
    private static final String SHARED_PREFERENCES_NAME = "RssSharedPreferences";
    private ExecutorService mExecutorService;
    private SharedPreferences mSharedPreferences;

    private SerialBehaviorSubject<Boolean> mPeriodicSyncInit;
    private String mPeriodicSyncInitKey;

    private SerialBehaviorSubject<Boolean> mEnablePeriodicSync;
    private String mEnablePeriodicSyncKey;

    private SerialBehaviorSubject<Integer> mPeriodicSyncRssHour;
    private String mPeriodicSyncRssHourKey;

    private SerialBehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;

    private SerialBehaviorSubject<Boolean> mOneHandMode;
    private String mOneHandModeKey;

    private boolean mShowCaseRssChannelList;
    private String mShowCaseRssChannelListKey;

    private boolean mShowCaseRssItemList;
    private String mShowCaseRssItemListKey;

    private boolean mDownloadImage;
    private String mDownloadImageKey;

    public AppSharedPreferences(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mPeriodicSyncInit = new SerialBehaviorSubject<>();
        mEnablePeriodicSync = new SerialBehaviorSubject<>();
        mPeriodicSyncRssHour = new SerialBehaviorSubject<>();
        mSelectedTheme = new SerialBehaviorSubject<>();
        mOneHandMode = new SerialBehaviorSubject<>();
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
        mDownloadImageKey = SHARED_PREFERENCES_NAME
                + ".downloadImage";

        boolean enablePeriodicSync = mSharedPreferences.getBoolean(mEnablePeriodicSyncKey, true);
        enablePeriodicSync(enablePeriodicSync);
        int periodicSyncRssHour = mSharedPreferences.getInt(
                mPeriodicSyncRssHourKey, 6);
        periodicSyncRssHour(periodicSyncRssHour);
        boolean periodicSyncInit = mSharedPreferences.getBoolean(mPeriodicSyncInitKey, false);
        setPeriodicSyncInit(periodicSyncInit);

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                -1);
        setSelectedTheme(selectedTheme);
        boolean oneHandMode = mSharedPreferences.getBoolean(mOneHandModeKey, false);
        oneHandMode(oneHandMode);
        boolean showCaseRssChannelList = mSharedPreferences.getBoolean(mShowCaseRssChannelListKey, false);
        setShowCaseRssChannelList(showCaseRssChannelList);
        boolean showCaseRssItemList = mSharedPreferences.getBoolean(mShowCaseRssItemListKey, false);
        setShowCaseRssItemList(showCaseRssItemList);
        boolean downloadImage = mSharedPreferences.getBoolean(mDownloadImageKey, false);
        setDownloadImage(downloadImage);
    }

    private void enablePeriodicSync(boolean b) {
        mEnablePeriodicSync.onNext(b);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mEnablePeriodicSyncKey, b)
                        .commit());
    }

    public Boolean isEnablePeriodicSync() {
        return mEnablePeriodicSync.getValue();
    }

    private void periodicSyncRssHour(int hour) {
        mPeriodicSyncRssHour.onNext(hour);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mPeriodicSyncRssHourKey, hour)
                        .commit());
    }

    public Integer getPeriodicSyncRssHour() {
        return mPeriodicSyncRssHour.getValue();
    }

    public void setPeriodicSyncRssHour(int hour) {
        periodicSyncRssHour(hour);
    }

    public Flowable<Integer> getPeriodicSyncRssHourFlow() {
        return Flowable.fromObservable(mPeriodicSyncRssHour.getSubject(), BackpressureStrategy.BUFFER);
    }

    public boolean isPeriodicSyncInit() {
        Boolean value = mPeriodicSyncInit.getValue();
        return value != null && value;
    }

    public Flowable<Boolean> isPeriodicSyncInitFlow() {
        return Flowable.fromObservable(mPeriodicSyncInit.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setPeriodicSyncInit(boolean b) {
        mPeriodicSyncInit.onNext(b);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mPeriodicSyncInitKey, b)
                        .commit());
    }

    public void setEnablePeriodicSync(boolean checked) {
        enablePeriodicSync(checked);
    }

    public Flowable<Boolean> getIsEnablePeriodicSyncFlow() {
        return Flowable.fromObservable(mEnablePeriodicSync.getSubject(), BackpressureStrategy.BUFFER);
    }

    private void selectedTheme(int setting) {
        mSelectedTheme.onNext(setting);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mSelectedThemeKey, setting)
                        .commit());
    }

    public void setSelectedTheme(int setting) {
        selectedTheme(setting);
    }

    public int getSelectedTheme() {
        Integer value = mSelectedTheme.getValue();
        return value == null ? -1 : value;
    }

    public Flowable<Integer> getSelectedThemeFlow() {
        return Flowable.fromObservable(mSelectedTheme.getSubject(), BackpressureStrategy.BUFFER);
    }

    private void oneHandMode(boolean oneHandMode) {
        mOneHandMode.onNext(oneHandMode);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mOneHandModeKey, oneHandMode)
                        .commit());
    }

    public boolean isOneHandMode() {
        Boolean value = mOneHandMode.getValue();
        return value != null && value;
    }

    public void setOneHandMode(boolean oneHandMode) {
        oneHandMode(oneHandMode);
    }

    public Flowable<Boolean> getIsOneHandModeFlow() {
        return Flowable.fromObservable(mOneHandMode.getSubject(), BackpressureStrategy.BUFFER);
    }

    public void setShowCaseRssChannelList(boolean show) {
        mShowCaseRssChannelList = show;
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mShowCaseRssChannelListKey, show)
                        .commit());
    }

    public boolean isShowCaseRssChannelList() {
        return mShowCaseRssChannelList;
    }

    public void setShowCaseRssItemList(boolean show) {
        mShowCaseRssItemList = show;
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mShowCaseRssItemListKey, show)
                        .commit());
    }

    public boolean isShowCaseRssItemList() {
        return mShowCaseRssItemList;
    }

    public void setDownloadImage(boolean download) {
        mDownloadImage = download;
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mDownloadImageKey, download)
                        .commit());
    }

    public boolean isDownloadImage() {
        return mDownloadImage;
    }
}
