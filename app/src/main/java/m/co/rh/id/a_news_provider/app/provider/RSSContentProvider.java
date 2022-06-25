package m.co.rh.id.a_news_provider.app.provider;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.provider.DatabaseProviderModule;
import m.co.rh.id.aprovider.Provider;

public class RSSContentProvider extends AbstractRSSContentProvider {

    private static final String AUTHORITY = "m.co.rh.id.a_news_provider.app.provider.rssprovider";


    @NonNull
    @Override
    protected String getAuthority() {
        return AUTHORITY;
    }


    @NonNull
    @Override
    protected List<Feed> getFeeds() {
        final RssDao d = getDatabase();

        List<RssChannel> channels = d.loadAllRssChannel();

        ArrayList<Feed> feeds = new ArrayList<>();
        for (RssChannel channel : channels) {
            final Feed f = AbstractRSSContentProvider.feed(Long.toString(channel.id), channel.feedName);
            feeds.add(f);
        }

        return feeds;
    }


    @NonNull
    @Override
    protected List<Article> getArticles(String feedId) {
        final RssDao d = getDatabase();
        List<RssItem> items;
        if (feedId == null) {
            items = d.findRssItems();
        } else {
            items = d.findRssItemsByChannelId(Long.parseLong(feedId));
        }

        List<Article> articles = new ArrayList<>();
        for (RssItem item : items) {
            final Article article = AbstractRSSContentProvider.article(Long.toString(item.id), item.title, item.description);
            articles.add(article);
        }
        return articles;
    }


    @NonNull
    private RssDao getDatabase() {
        Provider mProvider = Provider.createProvider(getContext(), new DatabaseProviderModule());
        return mProvider.get(RssDao.class);
    }
    
}