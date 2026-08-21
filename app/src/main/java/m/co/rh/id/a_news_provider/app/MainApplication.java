package m.co.rh.id.a_news_provider.app;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import m.co.rh.id.a_news_provider.app.provider.AppProviderModule;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class MainApplication extends BaseApplication implements Configuration.Provider {

    private static final String TAG = MainApplication.class.getName();

    private Provider mProvider;

    @Override
    public void onCreate() {
        super.onCreate();
        mProvider = Provider.createProvider(this, new AppProviderModule(this));

        // Install RxJava undeliverable error handler BEFORE any provider teardown can occur
        // This prevents RejectedExecutionException during executor shutdown from crashing the process
        RxJavaPlugins.setErrorHandler(throwable -> {
            // Log but never throw - RxJava routes undeliverable exceptions here
            try {
                mProvider.get(ILogger.class).e(TAG, "Undeliverable Rx exception", throwable);
            } catch (Throwable t) {
                // ILogger might fail during teardown; fall back to android log
                Log.e(TAG, "Undeliverable Rx exception", throwable);
            }
        });

        final Thread.UncaughtExceptionHandler defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            mProvider.get(ILogger.class)
                    .e(TAG, "App crash: " + throwable.getMessage(), throwable);
            mProvider.dispose();
            if (defaultExceptionHandler != null) {
                defaultExceptionHandler.uncaughtException(thread, throwable);
            } else {
                System.exit(99);
            }
        });
    }

    @Override
    public Provider getProvider() {
        return mProvider;
    }

    public INavigator getNavigator(Activity activity) {
        if (activity instanceof MainActivity) {
            return mProvider.get(INavigator.class);
        }
        return null;
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
