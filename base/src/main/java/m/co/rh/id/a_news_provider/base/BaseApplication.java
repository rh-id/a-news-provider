package m.co.rh.id.a_news_provider.base;

import android.app.Activity;
import android.app.Application;
import android.content.Context;


import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.aprovider.Provider;


public abstract class BaseApplication extends Application {
    public static BaseApplication of(Context context) {
        if (context.getApplicationContext() instanceof BaseApplication) {
            return (BaseApplication) context.getApplicationContext();
        }
        return null;
    }

    public abstract Provider getProvider();

    public abstract INavigator getNavigator(Activity activity);
}
