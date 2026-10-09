package m.co.rh.id.a_news_provider.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.color.DynamicColors;

import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppNotificationHandler;
import m.co.rh.id.a_news_provider.app.provider.RxProviderModule;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class MainActivity extends AppCompatActivity {

    private BehaviorSubject<Boolean> mRebuildUi;
    private Provider mProvider;
    private AppSharedPreferences mAppSharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mProvider = Provider.createProvider(this, new RxProviderModule());
        mAppSharedPreferences = BaseApplication.of(this).getProvider()
                .get(AppSharedPreferences.class);
        mRebuildUi = BehaviorSubject.create();
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
                });
        super.onCreate(savedInstanceState);
        handleNotification(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotification(intent);
    }

    private void handleNotification(Intent intent) {
        Provider provider = BaseApplication.of(this).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        appNotificationHandler.processNotification(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // this is required to let navigator handle onActivityResult
        BaseApplication.of(this).getNavigator(this).onActivityResult(requestCode, resultCode, data);
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
        mProvider.dispose();
        mProvider = null;
        mRebuildUi.onComplete();
        mRebuildUi = null;
    }
}