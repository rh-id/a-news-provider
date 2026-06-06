package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.aprovider.Provider;

public class RssQueryCmd {
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;

    public RssQueryCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
    }

    public Single<RssChannel> getRssChannelById(long id) {
        return Single.fromCallable(() ->
                mRssDao
                        .findRssChannelById(id))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<RssItem> getRssItemById(long id) {
        return Single.fromCallable(() ->
                mRssDao
                        .findRssItemById(id))
                .subscribeOn(Schedulers.from(mExecutorService));
    }

    public Single<Integer> countRssItem() {
        return Single.fromCallable(mRssDao::countRssItem)
                .subscribeOn(Schedulers.from(mExecutorService));
    }
}
