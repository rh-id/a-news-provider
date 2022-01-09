package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class RssQueryCmd {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssDao> mRssDao;

    public RssQueryCmd(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssDao = provider.lazyGet(RssDao.class);
    }

    public Single<RssChannel> getRssChannelById(long id) {
        return Single.fromFuture(mExecutorService.get().submit(() ->
                mRssDao.get()
                        .findRssChannelById(id))
        );
    }

    public Single<RssItem> getRssItemById(long id) {
        return Single.fromFuture(mExecutorService.get().submit(() ->
                mRssDao.get()
                        .findRssItemById(id))
        );
    }
}
