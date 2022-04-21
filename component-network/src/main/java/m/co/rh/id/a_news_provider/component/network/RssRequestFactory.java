package m.co.rh.id.a_news_provider.component.network;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Response;

import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.aprovider.Provider;

public class RssRequestFactory {
    private final Provider mProvider;

    public RssRequestFactory(Provider provider) {
        mProvider = provider;
    }

    public RssRequest newRssRequest(int method, String url,
                                    @Nullable Response.ErrorListener errorListener,
                                    @NonNull Response.Listener<RssModel> listener) {
        return new RssRequest(method, url, errorListener, listener, mProvider);
    }
}
