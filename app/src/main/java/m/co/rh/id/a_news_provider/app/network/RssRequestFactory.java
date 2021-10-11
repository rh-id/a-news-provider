package m.co.rh.id.a_news_provider.app.network;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.Response;

import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.aprovider.Provider;

public class RssRequestFactory {
    private final Provider mProvider;
    private final Context mAppContext;

    public RssRequestFactory(Provider provider, Context context) {
        mProvider = provider;
        mAppContext = context.getApplicationContext();
    }

    public RssRequest newRssRequest(int method, String url,
                                    @Nullable Response.ErrorListener errorListener,
                                    @NonNull Response.Listener<RssModel> listener) {
        return new RssRequest(method, url, errorListener, listener, mProvider, mAppContext);
    }
}
