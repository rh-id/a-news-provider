package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = RssItemSV.class.getName();


    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient Handler mHandler;
    private transient RxDisposer mRxDisposer;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient RssQueryCmd mRssQueryCmd;

    private SerialBehaviorSubject<RssItem> mRssItemSubject;
    private transient Runnable mGetRssChannelByIdAndOpenDetail;
    private transient RouteOptions mGetRssChannelByIdAndOpenDetail_routeOptions;

    private DateFormat mDateFormat;

    public RssItemSV() {
        mDateFormat = new SimpleDateFormat("E, d MMM yyyy");
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mHandler = mSvProvider.get(Handler.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssChangeNotifier = mSvProvider.get(RssChangeNotifier.class);
        mRssQueryCmd = mSvProvider.get(RssQueryCmd.class);
        if (mRssItemSubject == null) {
            mRssItemSubject = new SerialBehaviorSubject<>(new RssItem());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mGetRssChannelByIdAndOpenDetail_routeOptions = RouteOptions.withTransition(R.transition.page_rss_item_detail_enter,
                    R.transition.page_rss_item_detail_exit);
        } else {
            mGetRssChannelByIdAndOpenDetail_routeOptions = RouteOptions.withAnimation(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left,
                    null,
                    android.R.anim.slide_out_right
            );
        }

        mGetRssChannelByIdAndOpenDetail = () -> {
            RssItem rssItem = mRssItemSubject.getValue();
            mRxDisposer
                    .add("onClick_getRssChannelById",
                            mRssQueryCmd
                                    .getRssChannelById(rssItem.channelId)
                                    .subscribe((rssChannel, throwable) -> {
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class)
                                                    .e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            mNavigator.push(Routes.RSS_ITEM_DETAIL_PAGE,
                                                    RssItemDetailPage.Args.withRss(rssItem, rssChannel), null
                                                    , mGetRssChannelByIdAndOpenDetail_routeOptions);
                                        }
                                    })
                    );
            mHandler.removeCallbacks(mGetRssChannelByIdAndOpenDetail);
        };
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_item, container, false);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        TextView textDate = view.findViewById(R.id.text_date);
        TextView textTitle = view.findViewById(R.id.text_title);
        mRxDisposer.add("mRssItemSubject",
                mRssItemSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItem -> {
                            ViewCompat.setTransitionName(textTitle, "title_" + rssItem.id);
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
        mRxDisposer.add("createView_onRssItemUpdated",
                mRssChangeNotifier.getUpdatedRssItem()
                        .subscribe(rssItem -> {
                            RssItem currentRssItem = mRssItemSubject.getValue();
                            if (rssItem.id.equals(currentRssItem.id)) {
                                mRssItemSubject.onNext(rssItem);
                            }
                        }));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mGetRssChannelByIdAndOpenDetail = null;
        mGetRssChannelByIdAndOpenDetail_routeOptions = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            RssItem rssItem = mRssItemSubject.getValue();
            if (!rssItem.isRead) {
                mRssChangeNotifier
                        .readRssItem(rssItem);
                mRssItemSubject.onNext(rssItem);
            }
            /*
             Strange issue here, bold text seemed to cause animation jank when transition to new page,
             fixed by delaying navigation on to next frame (assuming 60FPS so around 16 milis).
             Update: Still jank sometimes when opening the detail, adjusting the delay to 3 frames.
             */
            mHandler
                    .postDelayed(mGetRssChannelByIdAndOpenDetail, 48);
        }
    }

    public void setRssItem(RssItem rssItem) {
        mRssItemSubject.onNext(rssItem);
    }

    @Override
    public boolean onLongClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            Context context = mSvProvider.getContext();
            RssItem rssItem = mRssItemSubject.getValue();
            if (!rssItem.isRead) {
                mRssChangeNotifier
                        .readRssItem(rssItem);
                mRssItemSubject.onNext(rssItem);
                Toast.makeText(context, context.getString(R.string.mark_as_read), Toast.LENGTH_SHORT)
                        .show();
            } else {
                mRssChangeNotifier
                        .unReadRssItem(rssItem);
                mRssItemSubject.onNext(rssItem);
                Toast.makeText(context, context.getString(R.string.mark_as_unread), Toast.LENGTH_SHORT)
                        .show();
            }
            return true;
        }
        return false;
    }
}
