package m.co.rh.id.a_news_provider.app.provider.command;

import android.content.Context;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class RenameRssFeedCmd {
    private final Context mAppContext;
    private final ProviderValue<ExecutorService> mExecutorService;
    private final ProviderValue<RssDao> mRssDao;
    private final ProviderValue<RssChangeNotifier> mRssChangeNotifier;
    private PublishSubject<RssChannel> mRssChannelPublishSubject;
    private PublishSubject<String> mNameValidationPublishSubject;

    public RenameRssFeedCmd(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mRssDao = provider.lazyGet(RssDao.class);
        mRssChangeNotifier = provider.lazyGet(RssChangeNotifier.class);
        mRssChannelPublishSubject = PublishSubject.create();
        mNameValidationPublishSubject = PublishSubject.create();
    }

    public boolean validName(String name) {
        boolean valid = true;
        if (name == null || name.isEmpty()) {
            valid = false;
            mNameValidationPublishSubject.onNext(mAppContext.getString(R.string.name_is_required));
        } else {
            mNameValidationPublishSubject.onNext("");
        }
        return valid;
    }

    public void execute(final long channelId, final String newName) {
        mExecutorService.get().execute(() -> {
            if (!validName(newName)) {
                mRssChannelPublishSubject.onError(new RuntimeException(mAppContext.getString(R.string.invalid_name)));
            } else {
                try {
                    RssChannel rssChannel = mRssDao.get().findRssChannelById(channelId);
                    if (rssChannel != null) {
                        rssChannel.feedName = newName;
                        mRssDao.get().update(rssChannel);
                        mRssChannelPublishSubject.onNext(rssChannel);
                        mRssChangeNotifier.get().updatedRssChannel(rssChannel);
                    } else {
                        mRssChannelPublishSubject.onError(new RuntimeException(mAppContext.getString(R.string.record_not_found)));
                    }
                } catch (Throwable t) {
                    mRssChannelPublishSubject.onError(t);
                }
            }
        });
    }

    public Flowable<RssChannel> liveRssChannel() {
        return Flowable.fromObservable(mRssChannelPublishSubject, BackpressureStrategy.BUFFER);
    }

    // validation message
    public Flowable<String> liveNameValidation() {
        return Flowable.fromObservable(mNameValidationPublishSubject, BackpressureStrategy.BUFFER);
    }
}
