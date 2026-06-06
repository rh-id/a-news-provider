package m.co.rh.id.a_news_provider.base.provider.notifier;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderDisposable;

@SuppressWarnings("deprecation")
public class DeviceStatusNotifier implements ProviderDisposable, Application.ActivityLifecycleCallbacks {
    private Context mAppContext;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private BehaviorSubject<Boolean> mIsOnlineBehaviorSubject;

    public DeviceStatusNotifier(Provider provider) {
        mAppContext = provider.getContext().getApplicationContext();
        mIsOnlineBehaviorSubject = BehaviorSubject.createDefault(false);
        checkOnlineStatus();
    }

    private void registerNetworkStatus() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    super.onAvailable(network);
                }

                @Override
                public void onLost(@NonNull Network network) {
                    mIsOnlineBehaviorSubject.onNext(false);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    boolean isConnected = hasInternetCapability(networkCapabilities);
                    mIsOnlineBehaviorSubject.onNext(isConnected);
                }
            };
            connectivityManager.registerDefaultNetworkCallback(mNetworkCallback);
        } else {
            NetworkRequest.Builder builder = new NetworkRequest.Builder();
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

            mNetworkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    checkOnlineStatus();
                }

                @Override
                public void onLost(@NonNull Network network) {
                    mIsOnlineBehaviorSubject.onNext(false);
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                        @NonNull NetworkCapabilities networkCapabilities) {
                    boolean isConnected = hasInternetCapability(networkCapabilities);
                    mIsOnlineBehaviorSubject.onNext(isConnected);
                }
            };
            connectivityManager.registerNetworkCallback(builder.build(), mNetworkCallback);
        }
    }

    private boolean hasInternetCapability(NetworkCapabilities capabilities) {
        boolean hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return hasInternet && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return hasInternet;
    }

    private void disposeNetworkStatus() {
        if (mNetworkCallback != null) {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            connectivityManager.unregisterNetworkCallback(mNetworkCallback);
        }
    }

    private void checkOnlineStatus() {
        ConnectivityManager cm =
                (ConnectivityManager) mAppContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = cm.getActiveNetwork();
            NetworkCapabilities capabilities = activeNetwork != null
                    ? cm.getNetworkCapabilities(activeNetwork) : null;
            boolean isConnected = capabilities != null
                    && hasInternetCapability(capabilities);
            mIsOnlineBehaviorSubject.onNext(isConnected);
        } else {
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null
                    && activeNetwork.isConnectedOrConnecting();
            mIsOnlineBehaviorSubject.onNext(isConnected);
        }
    }

    public Flowable<Boolean> onlineStatus() {
        return Flowable.fromObservable(mIsOnlineBehaviorSubject, BackpressureStrategy.BUFFER);
    }

    public boolean isOnline() {
        return mIsOnlineBehaviorSubject.getValue();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
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
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void dispose(Context context) {
        disposeNetworkStatus();
        mAppContext = null;
        mNetworkCallback = null;
        if (mIsOnlineBehaviorSubject != null) {
            mIsOnlineBehaviorSubject.onComplete();
            mIsOnlineBehaviorSubject = null;
        }
    }
}