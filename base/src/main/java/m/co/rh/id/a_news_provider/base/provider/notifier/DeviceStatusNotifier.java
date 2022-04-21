package m.co.rh.id.a_news_provider.base.provider.notifier;

import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;
import m.co.rh.id.aprovider.ProviderValue;

/**
 * Hub to handle status of device
 */
public class DeviceStatusNotifier implements ProviderDisposable, Application.ActivityLifecycleCallbacks {
    private Context mAppContext;
    private ProviderValue<ExecutorService> mExecutorService;

    // network related
    private BroadcastReceiver mNetworkActionBroadcastReceiver;
    private ConnectivityManager.NetworkCallback mNetworkCallback;

    private BehaviorSubject<Boolean> mIsOnlineBehaviorSubject;

    public DeviceStatusNotifier(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mExecutorService = provider.lazyGet(ExecutorService.class);
        mIsOnlineBehaviorSubject = BehaviorSubject.createDefault(false);
        checkOnlineStatus();
    }

    private void registerNetworkStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            mNetworkActionBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    checkOnlineStatus();
                }
            };
            mAppContext.registerReceiver(mNetworkActionBroadcastReceiver, filter);
        } else {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            builder.addTransportType(NetworkCapabilities.TRANSPORT_VPN);

            mNetworkCallback =
                    new ConnectivityManager.NetworkCallback() {

                        @Override
                        public void onAvailable(Network network) {
                            super.onAvailable(network);
                            checkOnlineStatus();
                        }

                        @Override
                        public void onLost(Network network) {
                            super.onLost(network);
                            checkOnlineStatus();
                        }

                    };
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) mAppContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.registerNetworkCallback(builder.build(), mNetworkCallback);
        }
    }

    private void disposeNetworkStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            mAppContext.unregisterReceiver(mNetworkActionBroadcastReceiver);
        } else {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) mAppContext
                            .getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
    }

    private void checkOnlineStatus() {
        mExecutorService.get().execute(() -> {
            ConnectivityManager cm =
                    (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null &&
                    activeNetwork.isConnectedOrConnecting();
            mIsOnlineBehaviorSubject.onNext(isConnected);
        });
    }


    public Flowable<Boolean> onlineStatus() {
        return Flowable.fromObservable(mIsOnlineBehaviorSubject, BackpressureStrategy.BUFFER);
    }

    public boolean isOnline() {
        return mIsOnlineBehaviorSubject.getValue();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
        // leave blank
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // leave blank
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        registerNetworkStatus();
        checkOnlineStatus();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        disposeNetworkStatus();
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // leave blank
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
        // leave blank
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // leave blank
    }

    @Override
    public void dispose(Context context) {
        disposeNetworkStatus();
        mExecutorService = null;
        mAppContext = null;
        mNetworkActionBroadcastReceiver = null;
        mNetworkCallback = null;
        if (mIsOnlineBehaviorSubject != null) {
            mIsOnlineBehaviorSubject.onComplete();
            mIsOnlineBehaviorSubject = null;
        }
    }
}
