package m.co.rh.id.a_news_provider.component.network;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.xmlpull.v1.XmlPullParserException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.a_news_provider.component.network.parser.RssFeedParser;
import m.co.rh.id.aprovider.Provider;

public class RssRequest extends Request<RssModel> {
    private final Context mAppContext;
    private final RssFeedParser mParser;
    private final Response.Listener<RssModel> mListener;

    public RssRequest(int method, String url, @Nullable Response.ErrorListener errorListener, Response.Listener<RssModel> listener, Provider provider) {
        super(method, url, errorListener);
        mListener = listener;
        mAppContext = provider.getContext().getApplicationContext();
        mParser = provider.get(RssFeedParser.class);
    }

    @Override
    protected Response<RssModel> parseNetworkResponse(NetworkResponse response) {
        try {
            String responseString = decodeResponse(response);
            RssModel rssModel = mParser.parse(responseString, getUrl());
            if (rssModel == null) {
                throw new XmlPullParserException(mAppContext.getString
                        (R.string.unable_to_parse, getUrl())
                );
            }
            return Response.success(rssModel, HttpHeaderParser.parseCacheHeaders(response));
        } catch (XmlPullParserException e) {
            return Response.error(new ParseError(e));
        } catch (Throwable throwable) {
            return Response.error(new VolleyError(throwable));
        }
    }

    private String decodeResponse(NetworkResponse response) {
        String charset = HttpHeaderParser.parseCharset(response.headers);
        try {
            return new String(response.data, charset);
        } catch (UnsupportedEncodingException e) {
            return new String(response.data, StandardCharsets.UTF_8);
        }
    }

    @Override
    protected void deliverResponse(RssModel response) {
        mListener.onResponse(response);
    }
}
