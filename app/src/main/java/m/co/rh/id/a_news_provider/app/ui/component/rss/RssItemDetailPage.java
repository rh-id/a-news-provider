package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;

public class RssItemDetailPage extends StatefulView<Activity> implements RequireNavigator {

    private AppBarSV mAppBarSV;
    private RssItem mRssItem;

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
        View view = activity.getLayoutInflater().inflate(R.layout.page_rss_item_detail, container, false);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        TextView titleText = view.findViewById(R.id.text_title);
        titleText.setText(mRssItem.title);
        titleText.setOnClickListener(view1 -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.link));
            activity.startActivity(browserIntent);
        });
        WebView webView = view.findViewById(R.id.webView);
        webView.loadData(mRssItem.description, null, null);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
    }
}
