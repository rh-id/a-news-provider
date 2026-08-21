package m.co.rh.id.a_news_provider.test;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class TestApplication extends BaseApplication implements Configuration.Provider {

    private static final String TAG = "TestApplication";

    private Provider mProvider;

    @Override
    public void onCreate() {
        super.onCreate();

        // Install RxJava undeliverable error handler BEFORE any provider teardown can occur
        // This prevents RejectedExecutionException during executor shutdown from crashing the test process
        RxJavaPlugins.setErrorHandler(throwable -> {
            // Log but never throw - RxJava routes undeliverable exceptions here
            try {
                Log.e(TAG, "Undeliverable Rx exception", throwable);
            } catch (Throwable ignored) {
                // Logging might fail during teardown; ignore to prevent recursive errors
            }
        });
    }

    public void setProvider(Provider provider) {
        mProvider = provider;
    }

    @Override
    public Provider getProvider() {
        return mProvider;
    }

    @Override
    public INavigator getNavigator(Activity activity) {
        return mProvider.get(INavigator.class);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        ExecutorService executorService = mProvider.get(ScheduledExecutorService.class);

        return new Configuration.Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .setExecutor(executorService)
                .setTaskExecutor(executorService)
                .build();
    }
}
