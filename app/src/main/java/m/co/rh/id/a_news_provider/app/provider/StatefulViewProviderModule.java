package m.co.rh.id.a_news_provider.app.provider;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module specifically for stateful view lifecycle
 */
public class StatefulViewProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerModule(new CommandProviderModule());
        providerRegistry.registerModule(new RxProviderModule());
    }

    @Override
    public void dispose(Provider provider) {
        // leave blank
    }
}
