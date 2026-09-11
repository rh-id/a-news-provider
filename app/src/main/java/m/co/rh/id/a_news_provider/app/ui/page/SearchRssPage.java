package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.SearchRssItemListSV;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class SearchRssPage extends StatefulView<Activity> implements RequireComponent<Provider> {

    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private SearchRssItemListSV mSearchRssItemListSV;
    // component
    private transient Provider mSvProvider;
    private transient AppSharedPreferences mAppSharedPreferences;

    public SearchRssPage() {
        mAppBarSV = new AppBarSV();
        mSearchRssItemListSV = new SearchRssItemListSV();
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_search_rss;
        if (mAppSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_search_rss;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        mAppBarSV.setTitle(activity.getString(R.string.menu_search));
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        ViewGroup containerSearchList = view.findViewById(R.id.container_search_list);
        containerSearchList.addView(mSearchRssItemListSV.buildView(activity, container));
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mSearchRssItemListSV.dispose(activity);
        mSearchRssItemListSV = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mAppSharedPreferences = null;
    }
}
