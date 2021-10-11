package m.co.rh.id.a_news_provider.app;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import m.co.rh.id.a_news_provider.app.provider.AppNotificationHandler;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.aprovider.Provider;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        getOnBackPressedDispatcher().addCallback(this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        BaseApplication.of(MainActivity.this)
                                .getNavigator(MainActivity.this).onBackPressed();
                    }
                });
        super.onCreate(savedInstanceState);
        handleNotification(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNotification(intent);
    }

    private void handleNotification(Intent intent) {
        Provider provider = BaseApplication.of(this).getProvider();
        AppNotificationHandler appNotificationHandler = provider.get(AppNotificationHandler.class);
        appNotificationHandler.processNotification(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // this is required to let navigator handle onActivityResult
        BaseApplication.of(this).getNavigator(this).onActivityResult(requestCode, resultCode, data);
    }
}