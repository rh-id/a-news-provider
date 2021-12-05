package m.co.rh.id.a_news_provider.test;

import android.app.Activity;

import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;

public class TestApplication extends BaseApplication {

    private Provider mProvider;

    public void setProvider(Provider provider) {
        mProvider = provider;
    }

    @Override
    public Provider getProvider() {
        return mProvider;
    }

    @Override
    public INavigator getNavigator(Activity activity) {
        return mProvider.get(INavigator.class);
    }
}
