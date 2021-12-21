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

import java.io.Serializable;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class RssItemDetailPage extends StatefulView<Activity> implements View.OnClickListener {

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private transient Provider mProvider;
    @NavInject
    private transient NavRoute mNavRoute;
    private RssItem mRssItem;
    private RssChannel mRssChannel;
    private transient Provider mSvProvider;

    public RssItemDetailPage() {
        mAppBarSV = new AppBarSV();
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mRssItem = args.getRssItem();
            mRssChannel = args.getRssChannel();
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        if (mSvProvider != null) {
            mSvProvider.dispose();
        }
        mSvProvider = mProvider.get(StatefulViewProvider.class);
        int layoutId = R.layout.page_rss_item_detail;
        AppSharedPreferences appSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
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
        titleText.setContentDescription(activity.getString(R.string.open_link));
        TextView textView = view.findViewById(R.id.text_content);
        String desc = mRssItem.description;
        if (desc != null && !desc.isEmpty()) {
            textView.setText(HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        Button fabOpenLink = view.findViewById(R.id.fab_open_link);
        fabOpenLink.setOnClickListener(this);
        mAppBarSV.setTitle(mRssChannel.feedName);
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
        mProvider = null;
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

    public static class Args implements Serializable {
        public static Args withRss(RssItem rssItem, RssChannel rssChannel) {
            Args args = new Args();
            args.mRssItem = rssItem;
            args.mRssChannel = rssChannel;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private RssItem mRssItem;
        private RssChannel mRssChannel;

        public RssItem getRssItem() {
            return mRssItem;
        }

        public RssChannel getRssChannel() {
            return mRssChannel;
        }
    }
}
