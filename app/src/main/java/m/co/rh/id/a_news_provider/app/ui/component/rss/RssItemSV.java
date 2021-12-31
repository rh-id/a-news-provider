package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements RequireNavigator, View.OnClickListener {

    private static final String TAG = RssItemSV.class.getName();
    private RssItem mRssItem;
    private transient BehaviorSubject<RssItem> mRssItemBehaviorSubject;
    private transient Provider mSvProvider;
    private transient INavigator mNavigator;
    private DateFormat mDateFormat;

    public RssItemSV() {
        mDateFormat = new SimpleDateFormat("E, d MMM yyyy");
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_item, container, false);
        if (mRssItem == null) {
            mRssItem = new RssItem();
            if (mRssItemBehaviorSubject == null) {
                mRssItemBehaviorSubject = BehaviorSubject.createDefault(mRssItem);
            } else {
                mRssItemBehaviorSubject.onNext(mRssItem);
            }
        }
        TextView textDate = view.findViewById(R.id.text_date);
        TextView textTitle = view.findViewById(R.id.text_title);
        Provider provider = BaseApplication.of(activity).getProvider();
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = provider.get(StatefulViewProvider.class);
        view.setOnClickListener(this);
        mSvProvider.get(RxDisposer.class).add("mRssItemSubject",
                mRssItemBehaviorSubject.subscribe(rssItem -> {
                    if (rssItem.pubDate != null) {
                        textDate.setText(mDateFormat.format(rssItem.pubDate));
                    } else if (rssItem.createdDateTime != null) {
                        textDate.setText(mDateFormat.format(rssItem.createdDateTime));
                    }
                    if (rssItem.title != null) {
                        textTitle.setText(HtmlCompat
                                .fromHtml(rssItem.title, HtmlCompat.FROM_HTML_MODE_COMPACT));
                    }
                    if (rssItem.isRead) {
                        textDate.setTypeface(null, Typeface.NORMAL);
                        textTitle.setTypeface(null, Typeface.NORMAL);
                    } else {
                        textDate.setTypeface(null, Typeface.BOLD);
                        textTitle.setTypeface(null, Typeface.BOLD);
                    }
                })
        );
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mRssItemBehaviorSubject != null) {
            mRssItemBehaviorSubject.onComplete();
            mRssItemBehaviorSubject = null;
        }
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
    }

    @Override
    public void onClick(View view) {
        if (mNavigator != null) {
            mSvProvider.get(RxDisposer.class)
                    .add("onClick_getRssChannelById",
                            mSvProvider.get(RssQueryCmd.class)
                                    .getRssChannelById(mRssItem.channelId)
                                    .subscribe((rssChannel, throwable) -> {
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class)
                                                    .e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            mNavigator.push((args, activity1) -> new RssItemDetailPage(),
                                                    RssItemDetailPage.Args.withRss(mRssItem, rssChannel));
                                        }
                                    })
                    );
        }
        if (!mRssItem.isRead) {
            mSvProvider.get(RssChangeNotifier.class)
                    .readRssItem(mRssItem);
            mRssItemBehaviorSubject.onNext(mRssItem);
        }
    }

    public void setRssItem(RssItem rssItem) {
        mRssItem = rssItem;
        if (mRssItemBehaviorSubject != null) {
            mRssItemBehaviorSubject.onNext(rssItem);
        }
    }
}
