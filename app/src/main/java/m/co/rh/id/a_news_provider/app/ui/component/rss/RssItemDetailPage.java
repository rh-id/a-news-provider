package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemDetailPage extends StatefulView<Activity> implements RequireNavigator, View.OnClickListener {
    private AppBarSV mAppBarSV;
    private RssItem mRssItem;
    private RssChannel mRssChannel;
    private transient Provider mSvProvider;

    public RssItemDetailPage(RssItem rssItem) {
        mRssItem = rssItem;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        if (mAppBarSV == null) {
            mAppBarSV = new AppBarSV(navigator);
        } else {
            mAppBarSV.provideNavigator(navigator);
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_rss_item_detail;
        Provider provider = BaseApplication.of(activity).getProvider();
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = BaseApplication.of(activity).getProvider().get(StatefulViewProvider.class);
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        if (appSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_rss_item_detail;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        TextView titleText = view.findViewById(R.id.text_title);
        titleText.setText(HtmlCompat
                .fromHtml(mRssItem.title, HtmlCompat.FROM_HTML_MODE_COMPACT));
        titleText.setOnClickListener(this);
        TextView textView = view.findViewById(R.id.text_content);
        String desc = mRssItem.description;
        if (desc != null && !desc.isEmpty()) {
            textView.setText(HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        Button fabOpenLink = view.findViewById(R.id.fab_open_link);
        fabOpenLink.setOnClickListener(this);
        if (mRssChannel == null) {
            RssDao rssDao = provider.get(RssDao.class);
            mSvProvider.get(RxDisposer.class).add("getRssChannel",
                    Single.fromCallable(() ->
                            rssDao.findRssChannelById(mRssItem.channelId))
                            .subscribeOn(Schedulers.from(provider.get(ExecutorService.class)))
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe((rssChannel, throwable) -> {
                                mRssChannel = rssChannel;
                                if (mRssChannel != null) {
                                    mAppBarSV.setTitle(mRssChannel.feedName);
                                }
                            })
            );
        }
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItem = null;
        mRssChannel = null;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.text_title || viewId == R.id.fab_open_link) {
            Activity activity = UiUtils.getActivity(view);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.link));
            activity.startActivity(browserIntent);
        }
    }
}
