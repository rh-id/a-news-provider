package m.co.rh.id.a_news_provider.base;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
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

    private SerialBehaviorSubject<Integer> mAutoMarkReadDays;
    private String mAutoMarkReadDaysKey;

    private SerialBehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;

    private SerialBehaviorSubject<Boolean> mOneHandMode;
    private String mOneHandModeKey;

    private SerialBehaviorSubject<Boolean> mDynamicColorsEnabled;
    private String mDynamicColorsEnabledKey;

    private SerialBehaviorSubject<Boolean> mMarkReadOnScroll;
    private String mMarkReadOnScrollKey;

    private SerialBehaviorSubject<Boolean> mSwipeArticleNavigationEnabled;
    private String mSwipeArticleNavigationEnabledKey;

    private boolean mShowCaseRssChannelList;
    private String mShowCaseRssChannelListKey;

    private boolean mShowCaseRssItemList;
    private String mShowCaseRssItemListKey;

    private boolean mDownloadImage;
    private String mDownloadImageKey;

    private int mNotificationPermissionRequestCount;
    private String mNotificationPermissionRequestCountKey;

    private boolean mNotificationPermissionDeniedBefore;
    private String mNotificationPermissionDeniedBeforeKey;

    public AppSharedPreferences(Provider provider) {
        mExecutorService = provider.get(ExecutorService.class);
        mSharedPreferences = provider.getContext().getSharedPreferences(
                SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        mPeriodicSyncInit = new SerialBehaviorSubject<>();
        mEnablePeriodicSync = new SerialBehaviorSubject<>();
        mPeriodicSyncRssHour = new SerialBehaviorSubject<>();
        mAutoMarkReadDays = new SerialBehaviorSubject<>();
        mSelectedTheme = new SerialBehaviorSubject<>();
        mOneHandMode = new SerialBehaviorSubject<>();
        mDynamicColorsEnabled = new SerialBehaviorSubject<>();
        mMarkReadOnScroll = new SerialBehaviorSubject<>();
        mSwipeArticleNavigationEnabled = new SerialBehaviorSubject<>();
        initValue();
    }

    private void initValue() {
        mPeriodicSyncInitKey = SHARED_PREFERENCES_NAME
                + ".periodicSyncInit";
        mEnablePeriodicSyncKey = SHARED_PREFERENCES_NAME
                + ".enablePeriodicSync";
        mPeriodicSyncRssHourKey = SHARED_PREFERENCES_NAME
                + ".periodicSyncRssHour";
        mAutoMarkReadDaysKey = SHARED_PREFERENCES_NAME
                + ".autoMarkReadDays";
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";
        mOneHandModeKey = SHARED_PREFERENCES_NAME
                + ".oneHandMode";
        mDynamicColorsEnabledKey = SHARED_PREFERENCES_NAME
                + ".dynamicColorsEnabled";
        mMarkReadOnScrollKey = SHARED_PREFERENCES_NAME
                + ".markReadOnScroll";
        mSwipeArticleNavigationEnabledKey = SHARED_PREFERENCES_NAME
                + ".swipeArticleNavigationEnabled";
        mShowCaseRssChannelListKey = SHARED_PREFERENCES_NAME
                + ".showCaseRssChannelList";
        mShowCaseRssItemListKey = SHARED_PREFERENCES_NAME
                + ".showCaseRssItemList";
        mDownloadImageKey = SHARED_PREFERENCES_NAME
                + ".downloadImage";
        mNotificationPermissionRequestCountKey = SHARED_PREFERENCES_NAME
                + ".notificationPermissionRequestCount";
        mNotificationPermissionDeniedBeforeKey = SHARED_PREFERENCES_NAME
                + ".notificationPermissionDeniedBefore";

        boolean enablePeriodicSync = mSharedPreferences.getBoolean(mEnablePeriodicSyncKey, true);
        enablePeriodicSync(enablePeriodicSync);
        int periodicSyncRssHour = mSharedPreferences.getInt(
                mPeriodicSyncRssHourKey, 6);
        periodicSyncRssHour(periodicSyncRssHour);
        int autoMarkReadDays = mSharedPreferences.getInt(
                mAutoMarkReadDaysKey, 0);
        autoMarkReadDays(autoMarkReadDays);
        boolean periodicSyncInit = mSharedPreferences.getBoolean(mPeriodicSyncInitKey, false);
        setPeriodicSyncInit(periodicSyncInit);

        int selectedTheme = mSharedPreferences.getInt(
                mSelectedThemeKey,
                -1);
        setSelectedTheme(selectedTheme);
        boolean oneHandMode = mSharedPreferences.getBoolean(mOneHandModeKey, false);
        oneHandMode(oneHandMode);
        boolean dynamicColorsEnabled = mSharedPreferences.getBoolean(mDynamicColorsEnabledKey, true);
        dynamicColorsEnabled(dynamicColorsEnabled);
        boolean markReadOnScroll = mSharedPreferences.getBoolean(mMarkReadOnScrollKey, false);
        markReadOnScroll(markReadOnScroll);
        // swipe article navigation defaults to ON: the left-edge swipe-right
        // (back) gesture has always been there, so the mirrored swipe-left
        // (next) starts enabled and users can turn the pair off in settings
        boolean swipeArticleNavigationEnabled = mSharedPreferences.getBoolean(
                mSwipeArticleNavigationEnabledKey, true);
        swipeArticleNavigationEnabled(swipeArticleNavigationEnabled);
        boolean showCaseRssChannelList = mSharedPreferences.getBoolean(mShowCaseRssChannelListKey, false);
        setShowCaseRssChannelList(showCaseRssChannelList);
        boolean showCaseRssItemList = mSharedPreferences.getBoolean(mShowCaseRssItemListKey, false);
        setShowCaseRssItemList(showCaseRssItemList);
        boolean downloadImage = mSharedPreferences.getBoolean(mDownloadImageKey, false);
        setDownloadImage(downloadImage);
        // assign directly (do NOT use the persisting setters) — a cold start must not
        // write these keys back to disk; they should only ever be written by an
        // actual permission ask/denial
        mNotificationPermissionRequestCount = mSharedPreferences.getInt(
                mNotificationPermissionRequestCountKey, 0);
        mNotificationPermissionDeniedBefore = mSharedPreferences.getBoolean(
                mNotificationPermissionDeniedBeforeKey, false);
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

    /**
     * Unread retention window in days: unread items older than this are automatically
     * marked as read at the end of each sync. 0 (default) turns the feature off.
     */
    private void autoMarkReadDays(int days) {
        mAutoMarkReadDays.onNext(days);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mAutoMarkReadDaysKey, days)
                        .commit());
    }

    public Integer getAutoMarkReadDays() {
        return mAutoMarkReadDays.getValue();
    }

    public void setAutoMarkReadDays(int days) {
        autoMarkReadDays(days);
    }

    public Flowable<Integer> getAutoMarkReadDaysFlow() {
        return Flowable.fromObservable(mAutoMarkReadDays.getSubject(), BackpressureStrategy.BUFFER);
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

    /**
     * Material You dynamic colors preference. Default ON, applied only on
     * Android 12 (S) and up where the system supports dynamic color.
     */
    private void dynamicColorsEnabled(boolean enabled) {
        mDynamicColorsEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mDynamicColorsEnabledKey, enabled)
                        .commit());
    }

    public boolean isDynamicColorsEnabled() {
        Boolean value = mDynamicColorsEnabled.getValue();
        return value != null && value;
    }

    public void setDynamicColorsEnabled(boolean enabled) {
        dynamicColorsEnabled(enabled);
    }

    public Flowable<Boolean> getIsDynamicColorsEnabledFlow() {
        return Flowable.fromObservable(mDynamicColorsEnabled.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * Opt-in setting (default off): when enabled, rss items in the main list are
     * marked as read once they are scrolled fully above the viewport.
     */
    private void markReadOnScroll(boolean markReadOnScroll) {
        mMarkReadOnScroll.onNext(markReadOnScroll);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mMarkReadOnScrollKey, markReadOnScroll)
                        .commit());
    }

    public boolean isMarkReadOnScroll() {
        Boolean value = mMarkReadOnScroll.getValue();
        return value != null && value;
    }

    public void setMarkReadOnScroll(boolean markReadOnScroll) {
        markReadOnScroll(markReadOnScroll);
    }

    public Flowable<Boolean> getIsMarkReadOnScrollFlow() {
        return Flowable.fromObservable(mMarkReadOnScroll.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * On by default: when enabled, the item detail page supports navigating to
     * the next/previous article of the home list by swiping from the screen
     * edges (swipe left = next article, swipe right = back). When disabled,
     * both swipe gestures are off and only the toolbar buttons navigate.
     */
    private void swipeArticleNavigationEnabled(boolean enabled) {
        mSwipeArticleNavigationEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mSwipeArticleNavigationEnabledKey, enabled)
                        .commit());
    }

    public boolean isSwipeArticleNavigationEnabled() {
        Boolean value = mSwipeArticleNavigationEnabled.getValue();
        return value != null && value;
    }

    public void setSwipeArticleNavigationEnabled(boolean enabled) {
        swipeArticleNavigationEnabled(enabled);
    }

    public Flowable<Boolean> getIsSwipeArticleNavigationEnabledFlow() {
        return Flowable.fromObservable(mSwipeArticleNavigationEnabled.getSubject(), BackpressureStrategy.BUFFER);
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

    /**
     * Number of times the {@code POST_NOTIFICATIONS} system dialog was ACTUALLY shown.
     * Both the passive homepage prompt and the explicit settings-toggle path increment
     * it right before showing the dialog. Used by NotificationPermissionPolicy as the
     * passive anti-nag budget.
     *
     * @param count the new request count
     */
    public void setNotificationPermissionRequestCount(int count) {
        mNotificationPermissionRequestCount = count;
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mNotificationPermissionRequestCountKey, count)
                        .commit());
    }

    public int getNotificationPermissionRequestCount() {
        return mNotificationPermissionRequestCount;
    }

    /**
     * True ONLY when a shown {@code POST_NOTIFICATIONS} system dialog was answered
     * with deny. Never inferred from the request count (the OS rationale flag cannot
     * distinguish permanent denial from a grant-then-revoke-in-settings state).
     *
     * @param denied true if a dialog was denied
     */
    public void setNotificationPermissionDeniedBefore(boolean denied) {
        mNotificationPermissionDeniedBefore = denied;
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mNotificationPermissionDeniedBeforeKey, denied)
                        .commit());
    }

    public boolean isNotificationPermissionDeniedBefore() {
        return mNotificationPermissionDeniedBefore;
    }
}
