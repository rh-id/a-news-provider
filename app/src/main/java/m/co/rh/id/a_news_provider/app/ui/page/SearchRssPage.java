package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.io.Serializable;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.SearchRssItemListSV;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.NavRoute;
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
        this(null);
    }

    /**
     * Creates the search page seeded with the term carried by the given route args.
     * The constructor-arg pattern is required (not NavRoute injection) because the
     * child {@link SearchRssItemListSV} is built here, before NavRoute injection
     * happens.
     *
     * @param args the route args carrying the optional search term, may be null
     */
    public SearchRssPage(Serializable args) {
        mAppBarSV = new AppBarSV();
        Args pageArgs = Args.of(args);
        mSearchRssItemListSV = new SearchRssItemListSV(
                pageArgs == null ? null : pageArgs.getSearchTerm());
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

    public static class Args implements Serializable {
        public static Args withSearchTerm(String searchTerm) {
            Args args = new Args();
            args.mSearchTerm = searchTerm;
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

        private String mSearchTerm;

        public String getSearchTerm() {
            return mSearchTerm;
        }
    }
}
