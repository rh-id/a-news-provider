package m.co.rh.id.a_news_provider.app.network;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.HttpHeaderParser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.model.RssModel;
import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class RssRequest extends Request<RssModel> {
    private static final String TAG = RssRequest.class.getName();
    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<RssDao> mRssDao;
    private final Response.Listener<RssModel> mListener;

    public RssRequest(int method, String url, @Nullable Response.ErrorListener errorListener, Response.Listener<RssModel> listener, Provider provider, Context context) {
        super(method, url, errorListener);
        mListener = listener;
        mAppContext = context.getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mRssDao = provider.lazyGet(RssDao.class);
    }

    @Override
    protected Response<RssModel> parseNetworkResponse(NetworkResponse response) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xpp.setInput(new StringReader(new String(response.data)));
            xpp.nextTag();
            xpp.require(XmlPullParser.START_TAG, null, "rss");
            RssModel rssModel = null;
            while (xpp.next() != XmlPullParser.END_TAG) {
                if (xpp.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = xpp.getName();
                if (name.equals("channel")) {
                    rssModel = readChannel(xpp);
                } else {
                    skip(xpp);
                }
            }
            mLogger.get().d(TAG, "Parsed RssModel: " + rssModel);
            if (rssModel == null) {
                throw new XmlPullParserException(mAppContext.getString
                        (R.string.unable_to_parse, getUrl())
                );
            }
            // save to db once parsed succesfully
            RssDao rssDao = mRssDao.get();
            RssChannel rssChannel = rssDao.findRssChannelByUrl(rssModel.getRssChannel().url);
            if (rssChannel == null) {
                rssDao.insertRssChannel(rssModel.getRssChannel(), rssModel.getRssItems().toArray(new RssItem[0]));
            } else {
                // map some field from db
                RssChannel responseRssChannel = rssModel.getRssChannel();
                responseRssChannel.id = rssChannel.id;
                responseRssChannel.feedName = rssChannel.feedName;
                responseRssChannel.createdDateTime = rssChannel.createdDateTime;
                responseRssChannel.updatedDateTime = rssChannel.updatedDateTime;

                ArrayList<RssItem> rssItemsFromModel = rssModel.getRssItems();
                List<RssItem> rssItemList = rssDao.findRssItemsByChannelId(rssChannel.id);
                // if the link is the same, import the previous isRead
                if (rssItemList != null && !rssItemList.isEmpty() &&
                        rssItemsFromModel != null && !rssItemsFromModel.isEmpty()) {
                    for (RssItem rssItemFromModel : rssItemsFromModel) {
                        String modelLink = rssItemFromModel.link;
                        if (modelLink != null && !modelLink.isEmpty()) {
                            for (RssItem rssItem : rssItemList) {
                                if (modelLink.equals(rssItem.link)) {
                                    rssItemFromModel.isRead = rssItem.isRead;
                                    break;
                                }
                            }
                        }
                    }
                }
                rssModel = new RssModel(responseRssChannel, rssItemsFromModel);
                rssDao.updateRssChannel(responseRssChannel, rssModel.getRssItems().toArray(new RssItem[0]));
            }
            return Response.success(rssModel, HttpHeaderParser.parseCacheHeaders(response));
        } catch (XmlPullParserException e) {
            return Response.error(new ParseError(e));
        } catch (Throwable throwable) {
            return Response.error(new VolleyError(throwable));
        }
    }

    private RssModel readChannel(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "channel");
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = getUrl();
        ArrayList<RssItem> rssItemList = new ArrayList<>();
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("title")) {
                rssChannel.title = readTitle(xpp);
                rssChannel.feedName = rssChannel.title;
            } else if (name.equals("description")) {
                rssChannel.description = readDescription(xpp);
            } else if (name.equals("link")) {
                rssChannel.link = readLink(xpp);
            } else if (name.equals("image")) {
                readImage(rssChannel, xpp);
            } else if (name.equals("item")) {
                rssItemList.add(readItem(xpp));
            } else {
                skip(xpp);
            }
        }
        return new RssModel(rssChannel, rssItemList);
    }

    private void readImage(RssChannel rssChannel, XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "image");
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("url")) {
                rssChannel.imageUrl = readText(xpp);
            } else {
                skip(xpp);
            }
        }
    }

    private RssItem readItem(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "item");
        RssItem rssItem = new RssItem();
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("title")) {
                rssItem.title = readTitle(xpp);
            } else if (name.equals("description")) {
                rssItem.description = readDescription(xpp);
            } else if (name.equals("link")) {
                rssItem.link = readLink(xpp);
            } else if (name.equals("pubDate")) {
                rssItem.pubDate = readPubDate(xpp);
            } else {
                skip(xpp);
            }
        }
        return rssItem;
    }

    private Date readPubDate(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "pubDate");
        String dateText = readText(xpp);
        Date pubDate = null;
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
            pubDate = simpleDateFormat.parse(dateText);
        } catch (Throwable throwable) {
            mLogger.get().d(TAG, "Failed to parse date: " + dateText, throwable);
        }
        xpp.require(XmlPullParser.END_TAG, null, "pubDate");
        return pubDate;
    }

    private String readTitle(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "title");
        String title = readText(xpp);
        xpp.require(XmlPullParser.END_TAG, null, "title");
        return title;
    }

    private String readDescription(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "description");
        String title = readText(xpp);
        xpp.require(XmlPullParser.END_TAG, null, "description");
        return title;
    }

    private String readLink(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "link");
        String title = readText(xpp);
        xpp.require(XmlPullParser.END_TAG, null, "link");
        return title;
    }

    private String readText(XmlPullParser xpp) throws IOException, XmlPullParserException {
        String result = "";
        if (xpp.next() == XmlPullParser.TEXT) {
            result = xpp.getText();
            xpp.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser xpp) throws XmlPullParserException, IOException {
        if (xpp.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (xpp.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }


    @Override
    protected void deliverResponse(RssModel response) {
        mListener.onResponse(response);
    }
}
