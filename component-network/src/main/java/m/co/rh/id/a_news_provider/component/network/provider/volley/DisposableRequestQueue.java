package m.co.rh.id.a_news_provider.component.network.provider.volley;

import android.content.Context;

import com.android.volley.Cache;
import com.android.volley.Network;
import com.android.volley.RequestQueue;
import com.android.volley.ResponseDelivery;

import m.co.rh.id.aprovider.ProviderDisposable;

public class DisposableRequestQueue extends RequestQueue implements ProviderDisposable {

    public DisposableRequestQueue(Cache cache, Network network, int threadPoolSize, ResponseDelivery delivery) {
        super(cache, network, threadPoolSize, delivery);
    }

    public DisposableRequestQueue(Cache cache, Network network, int threadPoolSize) {
        super(cache, network, threadPoolSize);
    }

    public DisposableRequestQueue(Cache cache, Network network) {
        super(cache, network);
    }

    @Override
    public void dispose(Context context) {
        stop();
    }
}
