package m.co.rh.id.a_news_provider.app.ui.component;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.MenuRes;
import androidx.appcompat.widget.Toolbar;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class AppBarSV extends StatefulView<Activity> implements RequireNavigator {

    private transient INavigator mNavigator;
    private String mTitle;
    private transient View.OnClickListener mNavigationOnClickListener;
    private boolean mIsInitialRoute;
    private transient Integer mMenuResId;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient RxDisposer mRxDisposer;
    private transient BehaviorSubject<String> mUpdateTitle;

    public AppBarSV(INavigator navigator) {
        mNavigator = navigator;
        mIsInitialRoute = mNavigator.isInitialRoute();
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
        Provider provider = BaseApplication.of(activity).getProvider();
        prepareDisposer(provider);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        mRxDisposer.add("updateTitle",
                mUpdateTitle.subscribe(toolbar::setTitle));
        if (mIsInitialRoute) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white);
            toolbar.setNavigationOnClickListener(mNavigationOnClickListener);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_navigation_arrow_back_white);
            toolbar.setNavigationOnClickListener(view1 -> mNavigator.pop());
        }
        if (mMenuResId != null) {
            toolbar.inflateMenu(mMenuResId);
        }
        if (mOnMenuItemClickListener != null) {
            toolbar.setOnMenuItemClickListener(mOnMenuItemClickListener);
        }
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
        mNavigationOnClickListener = null;
        mTitle = null;
    }

    public void setTitle(String title) {
        mTitle = title;
        if (mUpdateTitle != null) {
            mUpdateTitle.onNext(title);
        }
    }

    public void setNavigationOnClickListener(View.OnClickListener navigationOnClickListener) {
        mNavigationOnClickListener = navigationOnClickListener;
    }

    public void setMenu(@MenuRes int resId, Toolbar.OnMenuItemClickListener listener) {
        mMenuResId = resId;
        mOnMenuItemClickListener = listener;
    }
}
