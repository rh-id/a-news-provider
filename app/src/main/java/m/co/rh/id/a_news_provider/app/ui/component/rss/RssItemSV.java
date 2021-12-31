package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = RssItemSV.class.getName();


    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient Provider mProvider;
    private transient Provider mSvProvider;

    private RssItem mRssItem;
    private transient BehaviorSubject<RssItem> mRssItemBehaviorSubject;

    private DateFormat mDateFormat;

    public RssItemSV() {
        mDateFormat = new SimpleDateFormat("E, d MMM yyyy");
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_item, container, false);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
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
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(StatefulViewProvider.class);
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
        int id = view.getId();
        if (id == R.id.root_layout) {
            if (!mRssItem.isRead) {
                mSvProvider.get(RssChangeNotifier.class)
                        .readRssItem(mRssItem);
                mRssItemBehaviorSubject.onNext(mRssItem);
            }
            /*
             Strange issue here, bold text seemed to cause animation jank when transition to new page,
             fixed by delaying navigation on to next frame (assuming 60FPS so around 16 milis)
             */
            mSvProvider.get(Handler.class)
                    .postDelayed(() -> mSvProvider.get(RxDisposer.class)
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
                            ), 16);
        }
    }

    public void setRssItem(RssItem rssItem) {
        mRssItem = rssItem;
        if (mRssItemBehaviorSubject != null) {
            mRssItemBehaviorSubject.onNext(rssItem);
        }
    }

    @Override
    public boolean onLongClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            Context context = mSvProvider.getContext();
            RssChangeNotifier rssChangeNotifier = mSvProvider.get(RssChangeNotifier.class);
            if (!mRssItem.isRead) {
                rssChangeNotifier
                        .readRssItem(mRssItem);
                mRssItemBehaviorSubject.onNext(mRssItem);
                Toast.makeText(context, context.getString(R.string.mark_as_read), Toast.LENGTH_SHORT)
                        .show();
            } else {
                rssChangeNotifier
                        .unReadRssItem(mRssItem);
                mRssItemBehaviorSubject.onNext(mRssItem);
                Toast.makeText(context, context.getString(R.string.mark_as_unread), Toast.LENGTH_SHORT)
                        .show();
            }
            return true;
        }
        return false;
    }
}
