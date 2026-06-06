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
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class RenameRssFeedCmd {
    private static final String TAG = RenameRssFeedCmd.class.getName();
    private final Context mAppContext;
    private final ExecutorService mExecutorService;
    private final RssDao mRssDao;
    private final RssChangeNotifier mRssChangeNotifier;
    private final ILogger mLogger;
    private PublishSubject<RssChannel> mRssChannelPublishSubject;
    private PublishSubject<String> mNameValidationPublishSubject;

    public RenameRssFeedCmd(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.get(ExecutorService.class);
        mRssDao = provider.get(RssDao.class);
        mRssChangeNotifier = provider.get(RssChangeNotifier.class);
        mLogger = provider.get(ILogger.class);
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
        mExecutorService.execute(() -> {
            if (!validName(newName)) {
                mLogger.e(TAG, mAppContext.getString(R.string.invalid_name));
            } else {
                try {
                    RssChannel rssChannel = mRssDao.findRssChannelById(channelId);
                    if (rssChannel != null) {
                        rssChannel.feedName = newName;
                        mRssDao.update(rssChannel);
                        mRssChannelPublishSubject.onNext(rssChannel);
                        mRssChangeNotifier.updatedRssChannel(rssChannel);
                    } else {
                        mLogger.e(TAG, mAppContext.getString(R.string.record_not_found));
                    }
                } catch (Throwable t) {
                    mLogger.e(TAG, t.getMessage(), t);
                }
            }
        });
    }

    public Flowable<RssChannel> liveRssChannel() {
        return Flowable.fromObservable(mRssChannelPublishSubject, BackpressureStrategy.BUFFER);
    }

    public Flowable<String> liveNameValidation() {
        return Flowable.fromObservable(mNameValidationPublishSubject, BackpressureStrategy.BUFFER);
    }
}
