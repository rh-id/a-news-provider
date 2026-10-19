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

    /**
     * "Open links in" mode (issue #35): open article and feed links in the
     * device default browser via a raw {@code ACTION_VIEW} intent. This is the
     * default so existing users keep the exact behavior they always had.
     */
    public static final int MODE_DEFAULT_BROWSER = 0;
    /**
     * "Open links in" mode (issue #35): open links in a Custom Tabs session of a
     * browser that declares Custom Tabs support. When no such browser is
     * installed the launcher falls back to {@link #MODE_DEFAULT_BROWSER} at click
     * time (see app/util/OpenLinkPolicy).
     */
    public static final int MODE_CUSTOM_TABS = 1;

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

    private SerialBehaviorSubject<Integer> mKeepReadItemsDays;
    private String mKeepReadItemsDaysKey;

    private SerialBehaviorSubject<Integer> mSelectedTheme;
    private String mSelectedThemeKey;

    private SerialBehaviorSubject<Boolean> mOneHandMode;
    private String mOneHandModeKey;

    private SerialBehaviorSubject<Boolean> mDynamicColorsEnabled;
    private String mDynamicColorsEnabledKey;

    private SerialBehaviorSubject<Boolean> mMarkReadOnScroll;
    private String mMarkReadOnScrollKey;

    private SerialBehaviorSubject<Boolean> mCollapseDuplicateStories;
    private String mCollapseDuplicateStoriesKey;

    private SerialBehaviorSubject<Boolean> mSwipeArticleNavigationEnabled;
    private String mSwipeArticleNavigationEnabledKey;

    private SerialBehaviorSubject<Boolean> mVolumeKeyNavigationEnabled;
    private String mVolumeKeyNavigationEnabledKey;

    private SerialBehaviorSubject<Integer> mReadingFontSize;
    private String mReadingFontSizeKey;

    private SerialBehaviorSubject<Integer> mReadingFontFamily;
    private String mReadingFontFamilyKey;

    private SerialBehaviorSubject<Integer> mReadingLineSpacing;
    private String mReadingLineSpacingKey;

    private SerialBehaviorSubject<Boolean> mReadingJustification;
    private String mReadingJustificationKey;

    private SerialBehaviorSubject<Integer> mOpenLinksMode;
    private String mOpenLinksModeKey;

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
        mKeepReadItemsDays = new SerialBehaviorSubject<>();
        mSelectedTheme = new SerialBehaviorSubject<>();
        mOneHandMode = new SerialBehaviorSubject<>();
        mDynamicColorsEnabled = new SerialBehaviorSubject<>();
        mMarkReadOnScroll = new SerialBehaviorSubject<>();
        mCollapseDuplicateStories = new SerialBehaviorSubject<>();
        mSwipeArticleNavigationEnabled = new SerialBehaviorSubject<>();
        mVolumeKeyNavigationEnabled = new SerialBehaviorSubject<>();
        mReadingFontSize = new SerialBehaviorSubject<>();
        mReadingFontFamily = new SerialBehaviorSubject<>();
        mReadingLineSpacing = new SerialBehaviorSubject<>();
        mReadingJustification = new SerialBehaviorSubject<>();
        mOpenLinksMode = new SerialBehaviorSubject<>();
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
        mKeepReadItemsDaysKey = SHARED_PREFERENCES_NAME
                + ".keepReadItemsDays";
        mSelectedThemeKey = SHARED_PREFERENCES_NAME
                + ".selectedTheme";
        mOneHandModeKey = SHARED_PREFERENCES_NAME
                + ".oneHandMode";
        mDynamicColorsEnabledKey = SHARED_PREFERENCES_NAME
                + ".dynamicColorsEnabled";
        mMarkReadOnScrollKey = SHARED_PREFERENCES_NAME
                + ".markReadOnScroll";
        mCollapseDuplicateStoriesKey = SHARED_PREFERENCES_NAME
                + ".collapseDuplicateStories";
        mSwipeArticleNavigationEnabledKey = SHARED_PREFERENCES_NAME
                + ".swipeArticleNavigationEnabled";
        mVolumeKeyNavigationEnabledKey = SHARED_PREFERENCES_NAME
                + ".volumeKeyNavigationEnabled";
        mReadingFontSizeKey = SHARED_PREFERENCES_NAME
                + ".readingFontSize";
        mReadingFontFamilyKey = SHARED_PREFERENCES_NAME
                + ".readingFontFamily";
        mReadingLineSpacingKey = SHARED_PREFERENCES_NAME
                + ".readingLineSpacing";
        mReadingJustificationKey = SHARED_PREFERENCES_NAME
                + ".readingJustification";
        mOpenLinksModeKey = SHARED_PREFERENCES_NAME
                + ".openLinksMode";
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
        int keepReadItemsDays = mSharedPreferences.getInt(
                mKeepReadItemsDaysKey, 0);
        keepReadItemsDays(keepReadItemsDays);
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
        // collapse duplicate stories defaults to ON (issue #45): the same story
        // syndicated by several feeds used to repeat once per feed in the
        // all-channels list, and collapsing is the expected news-reader behavior;
        // users who prefer the raw per-feed rows can switch it off in settings
        boolean collapseDuplicateStories = mSharedPreferences.getBoolean(
                mCollapseDuplicateStoriesKey, true);
        collapseDuplicateStories(collapseDuplicateStories);
        // swipe article navigation defaults to ON: the left-edge swipe-right
        // (back) gesture has always been there, so the mirrored swipe-left
        // (next) starts enabled and users can turn the pair off in settings
        boolean swipeArticleNavigationEnabled = mSharedPreferences.getBoolean(
                mSwipeArticleNavigationEnabledKey, true);
        swipeArticleNavigationEnabled(swipeArticleNavigationEnabled);
        // volume key navigation defaults to ON (issue #60): a default-OFF toggle
        // would effectively hide the feature from users, so it starts enabled for
        // discoverability (same default-ON precedent as swipe-article-navigation
        // above); it only takes over the volume keys on the article detail page
        // and can be switched off in settings
        boolean volumeKeyNavigationEnabled = mSharedPreferences.getBoolean(
                mVolumeKeyNavigationEnabledKey, true);
        volumeKeyNavigationEnabled(volumeKeyNavigationEnabled);
        // reading typography: all four store an INDEX into the arrays on
        // app/util/ReadingTypography (never a raw sp/px/multiplier value), so one
        // array stays the single source of truth. :base cannot reference the :app
        // constants, so the defaults are repeated as literals here and must be kept
        // in sync with DEFAULT_FONT_SIZE_INDEX / DEFAULT_LINE_SPACING_INDEX.
        readingFontSize(mSharedPreferences.getInt(mReadingFontSizeKey, 2));
        readingFontFamily(mSharedPreferences.getInt(mReadingFontFamilyKey, 0));
        readingLineSpacing(mSharedPreferences.getInt(mReadingLineSpacingKey, 1));
        readingJustification(mSharedPreferences.getBoolean(mReadingJustificationKey, false));
        // "Open links in" defaults to the raw ACTION_VIEW behavior (0); existing
        // users therefore see no change when upgrading (issue #35)
        openLinksMode(mSharedPreferences.getInt(mOpenLinksModeKey, MODE_DEFAULT_BROWSER));
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

    /**
     * Read-item retention window in days (issue #37): read, non-favorite items that
     * no longer appear in their feed are deleted at the end of a sync once their
     * last-seen stamp is older than this. 0 (default) turns the feature off - with
     * upsert retention on, the archive would otherwise grow forever. Unread items
     * and favorites are never deleted; paused feeds age under the same window.
     */
    private void keepReadItemsDays(int days) {
        mKeepReadItemsDays.onNext(days);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mKeepReadItemsDaysKey, days)
                        .commit());
    }

    public Integer getKeepReadItemsDays() {
        return mKeepReadItemsDays.getValue();
    }

    public void setKeepReadItemsDays(int days) {
        keepReadItemsDays(days);
    }

    public Flowable<Integer> getKeepReadItemsDaysFlow() {
        return Flowable.fromObservable(mKeepReadItemsDays.getSubject(), BackpressureStrategy.BUFFER);
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
     * Cross-feed duplicate story collapsing (issue #45): when on, the all-channels
     * home list groups rss items sharing a link into ONE row (the newest copy,
     * badged with the total copy count) instead of repeating the story once per
     * feed. Default ON. Deliberately re-read at every list load instead of
     * observed reactively: the grouped and flat queries produce different keyset
     * cursors, so switching modes mid-window requires a fresh load, which the next
     * list load (or a REFRESH) provides naturally.
     */
    private void collapseDuplicateStories(boolean collapse) {
        mCollapseDuplicateStories.onNext(collapse);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mCollapseDuplicateStoriesKey, collapse)
                        .commit());
    }

    public boolean isCollapseDuplicateStories() {
        Boolean value = mCollapseDuplicateStories.getValue();
        return value == null || value;
    }

    public void setCollapseDuplicateStories(boolean collapse) {
        collapseDuplicateStories(collapse);
    }

    public Flowable<Boolean> getIsCollapseDuplicateStoriesFlow() {
        return Flowable.fromObservable(mCollapseDuplicateStories.getSubject(), BackpressureStrategy.BUFFER);
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

    /**
     * Volume-key article navigation (issue #60), default ON: when enabled, the
     * item detail page navigates to the next/previous article with the volume
     * keys (volume down = next article, volume up = previous). On by default for
     * discoverability (a default-OFF toggle would effectively hide the feature),
     * mirroring the swipe-article-navigation default ON; it only takes over the
     * volume keys on the article detail page and can be switched off in settings.
     */
    private void volumeKeyNavigationEnabled(boolean enabled) {
        mVolumeKeyNavigationEnabled.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mVolumeKeyNavigationEnabledKey, enabled)
                        .commit());
    }

    public boolean isVolumeKeyNavigationEnabled() {
        Boolean value = mVolumeKeyNavigationEnabled.getValue();
        return value != null && value;
    }

    public void setVolumeKeyNavigationEnabled(boolean enabled) {
        volumeKeyNavigationEnabled(enabled);
    }

    public Flowable<Boolean> getIsVolumeKeyNavigationEnabledFlow() {
        return Flowable.fromObservable(mVolumeKeyNavigationEnabled.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * Article body text size, as an index into the app's font-size steps. Default
     * 2 (the 18sp the detail layouts already ship with).
     */
    private void readingFontSize(int index) {
        mReadingFontSize.onNext(index);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mReadingFontSizeKey, index)
                        .commit());
    }

    public Integer getReadingFontSize() {
        return mReadingFontSize.getValue();
    }

    public void setReadingFontSize(int index) {
        readingFontSize(index);
    }

    public Flowable<Integer> getReadingFontSizeFlow() {
        return Flowable.fromObservable(mReadingFontSize.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * Article body font family, as an index into the app's family list. Default 0
     * (the platform sans the detail layouts already ship with).
     */
    private void readingFontFamily(int index) {
        mReadingFontFamily.onNext(index);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mReadingFontFamilyKey, index)
                        .commit());
    }

    public Integer getReadingFontFamily() {
        return mReadingFontFamily.getValue();
    }

    public void setReadingFontFamily(int index) {
        readingFontFamily(index);
    }

    public Flowable<Integer> getReadingFontFamilyFlow() {
        return Flowable.fromObservable(mReadingFontFamily.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * Article body line spacing, as an index into the app's line-spacing steps.
     * Default 1 (a 1.0 multiplier, i.e. untouched leading).
     */
    private void readingLineSpacing(int index) {
        mReadingLineSpacing.onNext(index);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mReadingLineSpacingKey, index)
                        .commit());
    }

    public Integer getReadingLineSpacing() {
        return mReadingLineSpacing.getValue();
    }

    public void setReadingLineSpacing(int index) {
        readingLineSpacing(index);
    }

    public Flowable<Integer> getReadingLineSpacingFlow() {
        return Flowable.fromObservable(mReadingLineSpacing.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * Whether the article body is justified (both edges flush). Opt-in: default
     * false, because a narrow column without hyphenation opens whitespace rivers.
     * Only takes effect on API 26+, where justification exists at all; the pref is
     * still persisted on older devices so a later upgrade activates it.
     */
    private void readingJustification(boolean enabled) {
        mReadingJustification.onNext(enabled);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putBoolean(mReadingJustificationKey, enabled)
                        .commit());
    }

    public boolean isReadingJustification() {
        Boolean value = mReadingJustification.getValue();
        return value != null && value;
    }

    public void setReadingJustification(boolean enabled) {
        readingJustification(enabled);
    }

    public Flowable<Boolean> getIsReadingJustificationFlow() {
        return Flowable.fromObservable(mReadingJustification.getSubject(), BackpressureStrategy.BUFFER);
    }

    /**
     * "Open links in" preference (issue #35): {@link #MODE_DEFAULT_BROWSER} (the
     * pre-existing raw ACTION_VIEW behavior, default) or {@link #MODE_CUSTOM_TABS}.
     * Read at CLICK time by the link launcher, never cached while binding views.
     */
    private void openLinksMode(int mode) {
        mOpenLinksMode.onNext(mode);
        mExecutorService.execute(() ->
                mSharedPreferences.edit().putInt(mOpenLinksModeKey, mode)
                        .commit());
    }

    public int getOpenLinksMode() {
        Integer value = mOpenLinksMode.getValue();
        return value == null ? MODE_DEFAULT_BROWSER : value;
    }

    public void setOpenLinksMode(int mode) {
        openLinksMode(mode);
    }

    public Flowable<Integer> getOpenLinksModeFlow() {
        return Flowable.fromObservable(mOpenLinksMode.getSubject(), BackpressureStrategy.BUFFER);
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
