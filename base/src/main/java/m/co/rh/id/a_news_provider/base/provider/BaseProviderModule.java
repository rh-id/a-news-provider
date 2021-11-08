package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.alogger.AndroidLogger;
import m.co.rh.id.alogger.CompositeLogger;
import m.co.rh.id.alogger.FileLogger;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.alogger.ToastLogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for base configuration
 */
public class BaseProviderModule implements ProviderModule {
    private static final String TAG = BaseProviderModule.class.getName();

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        // thread pool to be used throughout this app lifecycle
        providerRegistry.registerAsync(ExecutorService.class, () -> {
            ThreadPoolExecutor threadPoolExecutor =
                    new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(),
                            Integer.MAX_VALUE,
                            10, TimeUnit.SECONDS, new SynchronousQueue<>());
            threadPoolExecutor.allowCoreThreadTimeOut(true);
            threadPoolExecutor.prestartAllCoreThreads();
            return threadPoolExecutor;
        });
        providerRegistry.register(ScheduledExecutorService.class, Executors.newSingleThreadScheduledExecutor());
        providerRegistry.register(Handler.class, new Handler(Looper.getMainLooper()));
        providerRegistry.register(FileProvider.class, new FileProvider(context));
        providerRegistry.registerAsync(ILogger.class, () -> {
            ILogger defaultLogger = new AndroidLogger(ILogger.ERROR);
            List<ILogger> loggerList = new ArrayList<>();
            loggerList.add(defaultLogger);
            try {
                ILogger fileLogger = new FileLogger(ILogger.VERBOSE,
                        provider.get(FileProvider.class).getLogFile());
                loggerList.add(fileLogger);
            } catch (IOException e) {
                defaultLogger.e(TAG, "Error creating file logger", e);
            }
            try {
                ILogger toastLogger = new ToastLogger(ILogger.INFO, context);
                loggerList.add(toastLogger);
            } catch (Throwable throwable) {
                defaultLogger.e(TAG, "Error creating toast logger", throwable);
            }

            return new CompositeLogger(loggerList);
        });
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}
