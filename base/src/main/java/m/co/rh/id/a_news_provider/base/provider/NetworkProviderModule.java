package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;

import java.io.File;

import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for network configuration
 */
public class NetworkProviderModule implements ProviderModule {

    @Override
    public void provides(Context context, ProviderRegistry providerRegistry, Provider provider) {
        Context appContext = context.getApplicationContext();
        providerRegistry.registerLazy(RequestQueue.class, () -> {
            RequestQueue requestQueue;
            Cache cache = new DiskBasedCache(new File(appContext.getCacheDir(), "volley"),
                    1024 * 20480); // 20MB cap
            Network network = new BasicNetwork(new HurlStack());
            requestQueue = new RequestQueue(cache, network);
            requestQueue.start();
            return requestQueue;
        });
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}
