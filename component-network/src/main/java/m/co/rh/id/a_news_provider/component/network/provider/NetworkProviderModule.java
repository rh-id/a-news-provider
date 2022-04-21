package m.co.rh.id.a_news_provider.component.network.provider;

import android.graphics.Bitmap;
import android.os.Build;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.BaseHttpStack;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;

import java.io.File;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.component.network.RssRequestFactory;
import m.co.rh.id.a_news_provider.component.network.volley.TlsEnabledSSLSocketFactory;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Provider module for network configuration
 */
public class NetworkProviderModule implements ProviderModule {

    @Override
    public void provides(ProviderRegistry providerRegistry, Provider provider) {
        providerRegistry.registerLazy(BaseHttpStack.class, () -> {
            SSLSocketFactory sslSocketFactory = null;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH) {
                SocketFactory socketFactory = SSLSocketFactory.getDefault();
                sslSocketFactory =
                        new TlsEnabledSSLSocketFactory((SSLSocketFactory) socketFactory);
            }
            return new HurlStack(null, sslSocketFactory);
        });
        providerRegistry.registerLazy(Network.class, () -> new BasicNetwork(provider.get(BaseHttpStack.class)));
        providerRegistry.registerLazy(Cache.class, () -> new DiskBasedCache(new File(provider.getContext().getCacheDir(), "volley"),
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
        providerRegistry.registerLazy(RssRequestFactory.class, () -> new RssRequestFactory(provider));
    }

    @Override
    public void dispose(Provider provider) {
        provider.get(RequestQueue.class).stop();
    }
}
