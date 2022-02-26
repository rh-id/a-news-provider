package m.co.rh.id.a_news_provider.app.ui.component;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.Toolbar;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.base.rx.SerialBehaviorSubject;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.annotation.NavRouteIndex;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class AppBarSV extends StatefulView<Activity> implements RequireComponent<Provider>, View.OnClickListener, Toolbar.OnMenuItemClickListener {

    @NavInject
    private transient INavigator mNavigator;
    @NavRouteIndex
    private transient byte mRouteIndex;
    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private SerialBehaviorSubject<String> mTitleSubject;
    private transient Toolbar.OnMenuItemClickListener mOnMenuItemClickListener;
    private transient Runnable mNavigationOnClick;
    private transient OnMenuCreated mOnMenuCreated;
    private Integer mMenuResId;

    public AppBarSV() {
        this(null);
    }

    public AppBarSV(Integer menuResId) {
        mMenuResId = menuResId;
        mTitleSubject = new SerialBehaviorSubject<>("");
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.app_bar, container, false);
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        mRxDisposer.add("updateTitle",
                mTitleSubject.getSubject()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(toolbar::setTitle));
        if (isInitialRoute()) {
            toolbar.setNavigationIcon(R.drawable.ic_menu_white);
            toolbar.setNavigationContentDescription(R.string.main_menu);
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_navigation_arrow_back_white);
            toolbar.setNavigationContentDescription(R.string.back_to_previous_page);
        }
        toolbar.setNavigationOnClickListener(this);

        if (mMenuResId != null) {
            toolbar.inflateMenu(mMenuResId);
            if (mOnMenuCreated != null) {
                mOnMenuCreated.onMenuCreated(toolbar.getMenu());
            }
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
    }

    public boolean isInitialRoute() {
        return mRouteIndex == 0;
    }

    public void setTitle(String title) {
        mTitleSubject.onNext(title);
    }

    public void setNavigationOnClick(Runnable navigationOnClick) {
        mNavigationOnClick = navigationOnClick;
    }

    public void setMenuItemListener(Toolbar.OnMenuItemClickListener listener) {
        mOnMenuItemClickListener = listener;
    }

    public void setOnMenuCreated(OnMenuCreated onMenuCreated) {
        mOnMenuCreated = onMenuCreated;
    }

    @Override
    public void onClick(View view) {
        if (isInitialRoute()) {
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

    public interface OnMenuCreated {
        void onMenuCreated(Menu menu);
    }
}
