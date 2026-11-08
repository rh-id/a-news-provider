package m.co.rh.id.a_news_provider.app;

import android.app.PictureInPictureParams;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.util.TypedValue;
import android.view.KeyEvent;

import androidx.activity.BackEventCompat;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.VideoSize;
import androidx.media3.session.MediaController;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.RxProviderModule;
import m.co.rh.id.a_news_provider.app.provider.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.provider.component.playback.MiniPlayerBarController;
import m.co.rh.id.a_news_provider.app.provider.component.playback.PlaybackManager;
import m.co.rh.id.a_news_provider.app.provider.component.playback.PlaybackState;
import m.co.rh.id.a_news_provider.app.provider.component.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.KeyEventHandler;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.app.util.VideoUrlPolicy;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavBackEvent;
import m.co.rh.id.anavigator.component.NavOnRouteChangedListener;
import m.co.rh.id.aprovider.Provider;

public class MainActivity extends AppCompatActivity {

    /**
     * System-allowed picture-in-picture aspect range (R1): below 0.418 and
     * above 2.39 the system rejects/thresholds the ratio.
     */
    private static final float MIN_PIP_ASPECT_RATIO = 0.418f;
    private static final float MAX_PIP_ASPECT_RATIO = 2.39f;

    private BehaviorSubject<Boolean> mRebuildUi;
    private Provider mProvider;
    private AppSharedPreferences mAppSharedPreferences;
    // podcast playback (issue #69 phase 2): the app-scoped playback singletons
    // come from the APPLICATION provider (in instrumented tests that is the
    // test provider, which registers all three per R2)
    private PlaybackManager mPlaybackManager;
    private MiniPlayerBarController mMiniPlayerBarController;
    private VideoUrlPolicy mVideoUrlPolicy;
    private INavigator mNavigator;
    private NavOnRouteChangedListener mPipRouteChangedListener;
    private CompositeDisposable mPlaybackDisposables;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mProvider = Provider.createProvider(this, new RxProviderModule());
        mAppSharedPreferences = BaseApplication.of(this).getProvider()
                .get(AppSharedPreferences.class);
        mRebuildUi = BehaviorSubject.create();
        resolvePlaybackSingletons();
        // apply Material You dynamic colors before super.onCreate installs
        // the theme; gated on both the user setting and device support
        if (mAppSharedPreferences.isDynamicColorsEnabled()
                && DynamicColors.isDynamicColorAvailable()) {
            getTheme().applyStyle(R.style.Theme_Anewsprovider_Overlay_DynamicColors, true);
        }
        // rebuild UI is expensive and error prone, avoid spam rebuild (especially due to day and night mode)
        mProvider.get(RxDisposer.class)
                .add("rebuildUI", mRebuildUi.debounce(100, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(aBoolean -> {
                            if (aBoolean) {
                                // mirrors the onCreate applyStyle gate: the overrides are
                                // skipped only when dynamic colors are actually active
                                boolean dynamicColorsActive = mAppSharedPreferences.isDynamicColorsEnabled()
                                        && DynamicColors.isDynamicColorAvailable();
                                if (dynamicColorsActive) {
                                    // the activity handles uiMode config changes without recreation,
                                    // so re-apply the overlay to re-resolve its DayNight variant
                                    // (light/dark) for the new configuration before views rebuild
                                    getTheme().applyStyle(R.style.Theme_Anewsprovider_Overlay_DynamicColors, true);
                                    // the window background drawable was instantiated under the previous
                                    // configuration and is never re-created by applyStyle, so re-resolve
                                    // it explicitly (the legacy branch below does the same via daynight_*)
                                    TypedValue windowBackground = new TypedValue();
                                    getTheme().resolveAttribute(android.R.attr.windowBackground, windowBackground, true);
                                    if (windowBackground.type >= TypedValue.TYPE_FIRST_COLOR_INT
                                            && windowBackground.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                                        getWindow().setBackgroundDrawable(new ColorDrawable(windowBackground.data));
                                    } else if (windowBackground.resourceId != 0) {
                                        getWindow().setBackgroundDrawableResource(windowBackground.resourceId);
                                    }
                                } else {
                                    // Switching to night mode didn't update window background for some reason?
                                    // seemed to occur on android 8 and below
                                    getWindow().setBackgroundDrawableResource(R.color.daynight_white_black);
                                }
                                BaseApplication.of(this).getNavigator(this).reBuildAllRoute();
                                // reBuildAllRoute setContentView'd a NEW NavViewLayout, wiping ALL
                                // android.R.id.content children - the mini-player bar (a sibling
                                // of the nav layout) went with them: re-attach a fresh one (R9)
                                if (mMiniPlayerBarController != null) {
                                    mMiniPlayerBarController.attachIfNeeded(this);
                                }
                                updatePictureInPictureParams();
                            }
                        })
                );
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this).onBackPressed();
                    }

                    @Override
                    public void handleOnBackStarted(@NonNull BackEventCompat backEvent) {
                        // forward gesture frames so the navigator can run its
                        // predictive pop animation; no-op on the navigator's
                        // side unless the top route is poppable
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this)
                                .onBackStarted(toNavBackEvent(backEvent));
                    }

                    @Override
                    public void handleOnBackProgressed(@NonNull BackEventCompat backEvent) {
                        // per-frame progress updates for the predictive pop
                        // animation (same no-op guarantee as onBackStarted)
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this)
                                .onBackProgressed(toNavBackEvent(backEvent));
                    }

                    @Override
                    public void handleOnBackCancelled() {
                        // gesture released before commit: tell the navigator
                        // to settle the pop animation back to the top route
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this).onBackCancelled();
                    }
                });
        super.onCreate(savedInstanceState);
        handleNotification(getIntent());
    }

    /**
     * Resolves the app-scoped playback singletons and wires the PiP recompute
     * (issue #69 phase 2, R1): the params are refreshed on EVERY playback
     * state emission, on EVERY route push/pop/restore (whether the top route
     * is a detail page decides auto-enter) and at the onPause/onResume
     * lifecycle boundaries (the moment auto-enter can actually trigger).
     */
    private void resolvePlaybackSingletons() {
        BaseApplication baseApplication = BaseApplication.of(this);
        if (baseApplication == null || baseApplication.getProvider() == null) {
            return;
        }
        Provider applicationProvider = baseApplication.getProvider();
        mPlaybackManager = applicationProvider.get(PlaybackManager.class);
        mMiniPlayerBarController = applicationProvider.get(MiniPlayerBarController.class);
        mVideoUrlPolicy = applicationProvider.get(VideoUrlPolicy.class);
        mNavigator = applicationProvider.get(INavigator.class);
        mPlaybackDisposables = new CompositeDisposable();
        mPlaybackDisposables.add(mPlaybackManager.state()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> updatePictureInPictureParams()));
        mPipRouteChangedListener = (previous, current) -> updatePictureInPictureParams();
        mNavigator.addOnRouteChangedListener(mPipRouteChangedListener);
    }

    private void handleNotification(Intent intent) {
        Provider provider = BaseApplication.of(this).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        appNotificationHandler.processNotification(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // mini-player bar (issue #69 phase 2): attach a FRESH bar on every
        // resume (R9 - theme attrs must re-resolve; the navigator wipes
        // android.R.id.content's children on rebuild and activity recreation)
        if (mMiniPlayerBarController != null) {
            mMiniPlayerBarController.attachIfNeeded(this);
        }
        // auto-enter PiP (R1) triggers exactly at the leave-the-app boundary -
        // the params must be freshly computed at this lifecycle edge too
        updatePictureInPictureParams();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // auto-enter PiP (R1): the last recompute happens HERE, immediately
        // before the system evaluates leaving the app into picture-in-picture
        updatePictureInPictureParams();
    }

    /**
     * Recomputes the picture-in-picture auto-enter params (issue #69 phase 2,
     * R1 - MainActivity-OWNED, never page-driven): auto-enter is enabled ONLY
     * while the current top route is a RssItemDetailPage AND the shared
     * session is playing a DIRECT video URL (hub URLs like YouTube have
     * nothing to render in the PiP window). The aspect ratio follows the
     * session controller's video size, clamped to the system-allowed
     * [0.418, 2.39] range. Safe at null/finishing: every dependency is
     * null-checked and the activity guards its own terminal state.
     * API 31+ only ({@code setAutoEnterEnabled} is S) - a no-op below.
     */
    private void updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        if (isFinishing() || isDestroyed()) {
            return;
        }
        PlaybackState state = mPlaybackManager != null
                ? mPlaybackManager.getCurrentState() : null;
        boolean playingDirectVideo = state != null && state.item != null
                && state.isVideo && state.isPlaying
                && mVideoUrlPolicy != null
                && mVideoUrlPolicy.isProbablyDirectVideoUrl(state.url);
        NavRoute currentRoute = mNavigator != null ? mNavigator.getCurrentRoute() : null;
        boolean topRouteIsDetailPage = currentRoute != null
                && currentRoute.getStatefulView() instanceof RssItemDetailPage;
        boolean autoEnter = playingDirectVideo && topRouteIsDetailPage;
        float aspectRatio = 16f / 9f;
        MediaController controller = mPlaybackManager != null
                ? mPlaybackManager.getControllerIfConnected() : null;
        if (controller != null) {
            VideoSize videoSize = controller.getVideoSize();
            if (videoSize.width > 0 && videoSize.height > 0) {
                aspectRatio = clampPipAspectRatio(
                        (float) videoSize.width / (float) videoSize.height);
            }
        }
        // Rational wants integer terms - thousandths are exact enough for the
        // clamped bounds and any sensor-derived video size
        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(Math.round(aspectRatio * 1000f), 1000))
                .setAutoEnterEnabled(autoEnter)
                .build();
        setPictureInPictureParams(params);
    }

    /**
     * Clamps a width/height ratio into the system-allowed PiP aspect range.
     */
    private static float clampPipAspectRatio(float aspectRatio) {
        return Math.max(MIN_PIP_ASPECT_RATIO,
                Math.min(MAX_PIP_ASPECT_RATIO, aspectRatio));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // this is required to let navigator handle onActivityResult
        BaseApplication.of(this).getNavigator(this).onActivityResult(requestCode, resultCode, data);
    }

    // note: dialogs (StatefulViewDialog) own their window, so while a dialog has
    // focus this Activity's onKeyDown does not fire and volume keys keep their
    // normal behavior — acceptable, no article navigation while a dialog is open
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // volume-key article navigation (issue #60): route to the top-of-stack page.
        // onKeyDown (not dispatchKeyEvent) so focused text fields keep priority;
        // repeatCount filter prevents held keys from machine-gunning navigation.
        if (mAppSharedPreferences.isVolumeKeyNavigationEnabled()
                && event.getRepeatCount() == 0
                && (keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            // getCurrentRoute() is a stack peek and returns null while the route stack
            // is empty (before the initial splash route is pushed, and around
            // reBuildAllRoute() on uiMode changes) — mirror HomePage.isTopRoute()
            NavRoute currentRoute = BaseApplication.of(this).getNavigator(this)
                    .getCurrentRoute();
            if (currentRoute == null) {
                return super.onKeyDown(keyCode, event);
            }
            StatefulView<?> sv = currentRoute.getStatefulView();
            if (sv instanceof KeyEventHandler) {
                boolean handled = ((KeyEventHandler) sv).onKeyDown(keyCode, event);
                if (handled) {
                    return true; // suppress the volume change
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // this is required to let navigator handle onRequestPermissionsResult
        // (without it, no page ever receives permission results,
        // e.g. RssItemDetailPage download-video and the notification permission prompts)
        BaseApplication.of(this).getNavigator(this)
                .onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // using AppCompatDelegate.setDefaultNightMode trigger this method
        // but not triggering Application.onConfigurationChanged
        mRebuildUi.onNext(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // playback wiring teardown (issue #69 phase 2): the singletons live on
        // in the application provider - only THIS activity's hooks go
        if (mPipRouteChangedListener != null && mNavigator != null) {
            mNavigator.removeOnRouteChangedListener(mPipRouteChangedListener);
        }
        if (mPlaybackDisposables != null) {
            mPlaybackDisposables.dispose();
        }
        // do not hold a dead activity's view through the bar controller
        if (mMiniPlayerBarController != null) {
            mMiniPlayerBarController.clearViewReference();
        }
        mPipRouteChangedListener = null;
        mPlaybackDisposables = null;
        mPlaybackManager = null;
        mMiniPlayerBarController = null;
        mVideoUrlPolicy = null;
        mNavigator = null;
        mProvider.dispose();
        mProvider = null;
        mRebuildUi.onComplete();
        mRebuildUi = null;
    }

    // maps the androidx gesture frame onto the navigator's library-owned event
    // carrier (a-navigator has no androidx.activity dependency, so it cannot
    // accept BackEventCompat directly)
    private static NavBackEvent toNavBackEvent(BackEventCompat backEvent) {
        return new NavBackEvent(backEvent.getTouchX(),
                backEvent.getTouchY(), backEvent.getProgress());
    }
}