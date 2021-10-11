package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for base configuration
 */
public class BaseProviderModule implements ProviderModule {

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
        providerRegistry.registerLazy(Handler.class, () -> new Handler(Looper.getMainLooper()));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}
