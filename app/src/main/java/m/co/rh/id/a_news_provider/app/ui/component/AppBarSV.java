package m.co.rh.id.a_news_provider.app.ui.component;

import android.app.Activity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProviderModule;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class AppBarSV extends StatefulView<Activity> implements RequireNavigator, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    private transient INavigator mNavigator;
    private String mTitle;
    private transient Runnable mNavigationOnClick;
    private boolean mIsInitialRoute;
    private Integer mMenuResId;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Provider mSvProvider;
    private transient BehaviorSubject<String> mUpdateTitle;

    public AppBarSV(INavigator navigator) {
        this(navigator, null);
    }

    public AppBarSV(INavigator navigator, Integer menuResId) {
        mNavigator = navigator;
        mIsInitialRoute = mNavigator.isInitialRoute();
        mMenuResId = menuResId;
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.app_bar, container, false);
        if (mUpdateTitle == null) {
            if (mTitle == null) {
                mUpdateTitle = BehaviorSubject.create();
            } else {
                mUpdateTitle = BehaviorSubject.createDefault(mTitle);
            }
        }
        mSvProvider = Provider.createProvider(activity, new StatefulViewProviderModule(activity));
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        mSvProvider.get(RxDisposer.class).add("updateTitle",
                mUpdateTitle.subscribe(toolbar::setTitle));
        if (mIsInitialRoute) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_navigation_arrow_back_white);
        }
        toolbar.setNavigationOnClickListener(this);

        if (mMenuResId != null) {
            toolbar.inflateMenu(mMenuResId);
        }
        toolbar.setOnMenuItemClickListener(this);
        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mNavigationOnClick = null;
        mTitle = null;
    }

    public void setTitle(String title) {
        mTitle = title;
        if (mUpdateTitle != null) {
            mUpdateTitle.onNext(title);
        }
    }

    public void setNavigationOnClick(Runnable navigationOnClick) {
        mNavigationOnClick = navigationOnClick;
    }

    public void setMenuItemListener(Toolbar.OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    @Override
    public void onClick(View view) {
        if (mIsInitialRoute) {
            if (mNavigationOnClick != null) {
                mNavigationOnClick.run();
            }
        } else {
            mNavigator.pop();
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (mOnMenuItemClickListener != null) {
            return mOnMenuItemClickListener.onMenuItemClick(item);
        }
        return false;
    }
}
