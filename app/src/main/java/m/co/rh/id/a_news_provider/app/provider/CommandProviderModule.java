package m.co.rh.id.a_news_provider.app.provider;

import android.content.Context;

import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class CommandProviderModule implements ProviderModule {

    private Provider mAppProvider;

    public CommandProviderModule(Provider appProvider) {
        mAppProvider = appProvider;
    }

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(PagedRssItemsCmd.class, () -> new PagedRssItemsCmd(mAppProvider));
        providerRegistry.registerLazy(NewRssChannelCmd.class, () -> new NewRssChannelCmd(mAppProvider, context));
        providerRegistry.registerLazy(RenameRssFeedCmd.class, () -> new RenameRssFeedCmd(mAppProvider, context));
        providerRegistry.registerLazy(SyncRssCmd.class, () -> new SyncRssCmd(mAppProvider, context));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // leave blank
    }
}
