package m.co.rh.id.a_news_provider.base.provider;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;

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
        providerRegistry.registerLazy(Network.class, () -> new BasicNetwork(new HurlStack()));
        providerRegistry.registerLazy(Cache.class, () -> new DiskBasedCache(new File(appContext.getCacheDir(), "volley"),
                1024 * 20480));
        providerRegistry.registerLazy(RequestQueue.class, () -> {
            Cache cache = provider.get(Cache.class);
            Network network = provider.get(Network.class);
            RequestQueue requestQueue = new RequestQueue(cache, network);
            requestQueue.start();
            return requestQueue;
        });
        providerRegistry.registerAsync(ImageLoader.class, () ->
                new ImageLoader(provider.get(RequestQueue.class),
                        new ImageLoader.ImageCache() {
                            private final LruCache<String, Bitmap> mCache = new LruCache<>(20);

                            @Nullable
                            @Override
                            public Bitmap getBitmap(String url) {
                                return mCache.get(url);
                            }

                            @Override
                            public void putBitmap(String url, Bitmap bitmap) {
                                mCache.put(url, bitmap);
                            }
                        }));
    }

    @Override
    public void dispose(Context context, Provider provider) {
        // nothing to dispose
    }
}
