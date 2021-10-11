package m.co.rh.id.a_news_provider.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import m.co.rh.id.a_news_provider.app.provider.AppNotificationHandler;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class NotificationDeleteReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Provider provider = BaseApplication.of(context).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        appNotificationHandler.removeNotification(intent);
    }
}
