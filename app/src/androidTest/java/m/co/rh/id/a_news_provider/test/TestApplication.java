package m.co.rh.id.a_news_provider.test;

import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class TestApplication extends BaseApplication implements Configuration.Provider {

    private Provider mProvider;

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
                .setMinimumLoggingLevel(android.util.Log.INFO)
                .setExecutor(executorService)
                .setTaskExecutor(executorService)
                .build();
    }
}
