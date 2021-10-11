package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.AppNotificationHandler;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class SplashPage extends StatefulView<Activity> implements RequireNavigator {
    private transient INavigator mNavigator;
    private boolean mInitialized;

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        Provider provider = BaseApplication.of(activity).getProvider();
        if(!mInitialized){
            provider.get(Handler.class)
                    .postDelayed(() ->
                            mNavigator.retry(new HomePage()), 1000);
            mInitialized = true;
        }
        return activity.getLayoutInflater().inflate(R.layout.page_splash, container, false);
    }

}
