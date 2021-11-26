package m.co.rh.id.a_news_provider.app.provider;

import android.app.Activity;
import android.content.Context;

import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module specifically for stateful view lifecycle
 */
public class StatefulViewProviderModule implements ProviderModule {

    private Provider mAppProvider;

    public StatefulViewProviderModule(Activity activity) {
        mAppProvider = BaseApplication.of(activity).getProvider();
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new CommandProviderModule(mAppProvider));
        providerRegistry.registerModule(new RxProviderModule());
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
