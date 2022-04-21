package m.co.rh.id.a_news_provider.app.provider;

import m.co.rh.id.a_news_provider.app.provider.command.EditRssLinkCmd;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RenameRssFeedCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.command.SyncRssCmd;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

public class CommandProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(PagedRssItemsCmd.class, () -> new PagedRssItemsCmd(provider));
        providerRegistry.registerLazy(NewRssChannelCmd.class, () -> new NewRssChannelCmd(provider));
        providerRegistry.registerLazy(RenameRssFeedCmd.class, () -> new RenameRssFeedCmd(provider));
        providerRegistry.registerLazy(SyncRssCmd.class, () -> new SyncRssCmd(provider));
        providerRegistry.registerLazy(RssQueryCmd.class, () -> new RssQueryCmd(provider));
        providerRegistry.registerLazy(EditRssLinkCmd.class, () -> new EditRssLinkCmd(provider));
    }

    @Override
    public void dispose(Provider provider) {
        // Leave blank
    }
}
