package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements RequireNavigator {

    private transient BehaviorSubject<RssItem> mRssItemSubject;
    private transient RxDisposer mRxDisposer;
    private transient INavigator mNavigator;

    public RssItemSV() {
        mRssItemSubject = BehaviorSubject.createDefault(new RssItem());
    }

    public void setRssItem(RssItem rssItem) {
        mRssItemSubject.onNext(rssItem);
    }


    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_item, container, false);
        TextView textDate = view.findViewById(R.id.text_date);
        TextView textTitle = view.findViewById(R.id.text_title);
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        RssChangeNotifier rssChangeNotifier = provider.get(RssChangeNotifier.class);
        view.setOnClickListener(view1 -> {
            if (mNavigator != null) {
                mNavigator.push((args, activity1) -> new RssItemDetailPage((RssItem) args),
                        mRssItemSubject.getValue(), null);
            }
            RssItem rssItem = mRssItemSubject.getValue();
            if (!rssItem.isRead) {
                rssChangeNotifier.readRssItem(rssItem);
                mRssItemSubject.onNext(rssItem);
            }
        });
        mRxDisposer.add("mRssItemSubject",
                mRssItemSubject.subscribe(rssItem -> {
                    if (rssItem.createdDateTime != null) {
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

    private void prepareDisposer(Provider provider) {
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
        }
        mRxDisposer = provider.get(RxDisposer.class);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mRssItemSubject != null) {
            mRssItemSubject.onComplete();
            mRssItemSubject = null;
        }
        if (mRxDisposer != null) {
            mRxDisposer.dispose();
            mRxDisposer = null;
        }
    }
}
