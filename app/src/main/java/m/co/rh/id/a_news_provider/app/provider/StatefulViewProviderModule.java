package m.co.rh.id.a_news_provider.app.provider;

import android.os.Handler;
import android.os.Looper;

import m.co.rh.id.a_news_provider.base.provider.DisposableHandler;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module specifically for stateful view lifecycle
 */
public class StatefulViewProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.register(Handler.class, () -> new DisposableHandler(Looper.getMainLooper()));
        providerRegistry.registerModule(new CommandProviderModule());
        providerRegistry.registerModule(new RxProviderModule());
    }
}
