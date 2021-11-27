package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.RxProviderModule;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements RequireNavigator {

    private RssItem mRssItem;
    private transient BehaviorSubject<RssItem> mRssItemBehaviorSubject;
    private transient Provider mSvProvider;
    private transient INavigator mNavigator;

    public void setRssItem(RssItem rssItem) {
        mRssItem = rssItem;
        if (mRssItemBehaviorSubject != null) {
            mRssItemBehaviorSubject.onNext(rssItem);
        }
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
        mSvProvider = Provider.createProvider(activity.getApplicationContext(), new RxProviderModule());
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        view.setOnClickListener(view1 -> {
            if (mNavigator != null) {
                mNavigator.push((args, activity1) -> new RssItemDetailPage((RssItem) args),
                        mRssItem, null);
            }
            if (!mRssItem.isRead) {
                rssChangeNotifier.readRssItem(mRssItem);
                mRssItemBehaviorSubject.onNext(mRssItem);
            }
        });
        mSvProvider.get(RxDisposer.class).add("mRssItemSubject",
                mRssItemBehaviorSubject.subscribe(rssItem -> {
                    if (rssItem.pubDate != null) {
                        textDate.setText(rssItem.pubDate.toString());
                    } else if (rssItem.createdDateTime != null) {
                        textDate.setText(rssItem.createdDateTime.toString());
                    }
                    if (rssItem.title != null) {
                        textTitle.setText(rssItem.title);
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
}
