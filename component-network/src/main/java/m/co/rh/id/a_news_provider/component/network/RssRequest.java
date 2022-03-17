package m.co.rh.id.a_news_provider.component.network;

import android.content.Context;
import android.os.Build;

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

import m.co.rh.id.a_news_provider.base.dao.RssDao;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class RssRequest extends Request<RssModel> {
    private static final String TAG = RssRequest.class.getName();
    private final Context mAppContext;
    private final ILogger mLogger;
    private final RssDao mRssDao;
    private final Response.Listener<RssModel> mListener;

    public RssRequest(int method, String url, @Nullable Response.ErrorListener errorListener, Response.Listener<RssModel> listener, Provider provider, Context context) {
        super(method, url, errorListener);
        mListener = listener;
        mAppContext = context.getApplicationContext();
        mLogger = provider.get(ILogger.class);
        mRssDao = provider.get(RssDao.class);
    }

    @Override
    protected Response<RssModel> parseNetworkResponse(NetworkResponse response) {
        try {
            RssModel rssModel = null;
            try {
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xpp.setInput(new StringReader(new String(response.data)));
                xpp.nextTag();
                xpp.require(XmlPullParser.START_TAG, null, "rss");
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
            } catch (XmlPullParserException e) {
                mLogger.v(TAG, "Error parsing rss, try parsing atom: " + e.getMessage(), e);
                // TRY parse atom
                XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                factory.setNamespaceAware(true);
                XmlPullParser xpp = factory.newPullParser();
                xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
                xpp.setInput(new StringReader(new String(response.data)));
                xpp.nextTag();
                xpp.require(XmlPullParser.START_TAG, null, "feed");
                rssModel = readFeed(xpp);
            }
            mLogger.v(TAG, "Parsed RssModel: " + rssModel);
            if (rssModel == null) {
                throw new XmlPullParserException(mAppContext.getString
                        (R.string.unable_to_parse, getUrl())
                );
            }
            // save to db once parsed succesfully
            RssChannel rssChannel = mRssDao.findRssChannelByUrl(rssModel.getRssChannel().url);
            if (rssChannel == null) {
                mRssDao.insertRssChannel(rssModel.getRssChannel(), rssModel.getRssItems().toArray(new RssItem[0]));
            } else {
                // map some field from db
                RssChannel responseRssChannel = rssModel.getRssChannel();
                responseRssChannel.id = rssChannel.id;
                responseRssChannel.feedName = rssChannel.feedName;
                responseRssChannel.createdDateTime = rssChannel.createdDateTime;
                responseRssChannel.updatedDateTime = rssChannel.updatedDateTime;

                ArrayList<RssItem> rssItemsFromModel = rssModel.getRssItems();
                List<RssItem> rssItemList = mRssDao.findRssItemsByChannelId(rssChannel.id);
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
                mRssDao.updateRssChannel(responseRssChannel, rssModel.getRssItems().toArray(new RssItem[0]));
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

    // Atom XML
    private RssModel readFeed(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "feed");
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
            } else if (name.equals("link")) {
                rssChannel.link = readLinkHref(xpp);
            } else if (name.equals("entry")) {
                rssItemList.add(readEntry(xpp));
            } else {
                skip(xpp);
            }
        }
        return new RssModel(rssChannel, rssItemList);
    }

    // Atom XML
    private String readLinkHref(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "link");
        String url = null;
        int attrSize = xpp.getAttributeCount();
        for (int i = 0; i < attrSize; i++) {
            switch (xpp.getAttributeName(i)) {
                case "href":
                    url = xpp.getAttributeValue(i);
                    break;
            }
        }
        xpp.next();
        xpp.require(XmlPullParser.END_TAG, null, "link");
        return url;
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

    // Atom XML
    private RssItem readEntry(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "entry");
        RssItem rssItem = new RssItem();
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("title")) {
                rssItem.title = readTitle(xpp);
            } else if (name.equals("summary")) {
                rssItem.description = readSummary(xpp);
            } else if (name.equals("content")) {
                rssItem.description = readContent(xpp);
            } else if (name.equals("link")) {
                rssItem.link = readLinkHref(xpp);
            } else if (name.equals("updated")) {
                rssItem.pubDate = readUpdated(xpp);
            } else {
                skip(xpp);
            }
        }
        return rssItem;
    }

    // Atom XML
    private String readSummary(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "summary");
        String title = readText(xpp);
        xpp.require(XmlPullParser.END_TAG, null, "summary");
        return title;
    }

    // Atom XML
    private String readContent(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "content");
        String title = readText(xpp);
        xpp.require(XmlPullParser.END_TAG, null, "content");
        return title;
    }

    // Atom XML
    private Date readUpdated(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "updated");
        String dateText = readText(xpp);
        Date pubDate = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                pubDate = simpleDateFormat.parse(dateText);
            } else {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                pubDate = simpleDateFormat.parse(dateText);
            }
        } catch (Throwable throwable) {
            mLogger.d(TAG, "Failed to parse date: " + dateText, throwable);
        }
        xpp.require(XmlPullParser.END_TAG, null, "updated");
        return pubDate;
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
            } else if (name.equals("media:content")) {
                RssMedia rssMedia = readMediaContent(xpp);
                if (rssMedia.isImage()) {
                    rssItem.mediaImage = rssMedia.url;
                } else if (rssMedia.isVideo()) {
                    rssItem.mediaVideo = rssMedia.url;
                }
            } else if (name.equals("media:thumbnail")) {
                RssMedia rssMedia = readMediaThumbnail(xpp);
                if (rssMedia.isImage()) {
                    rssItem.mediaImage = rssMedia.url;
                }
            } else if (name.equals("enclosure")) {
                RssMedia rssMedia = readEnclosure(xpp);
                if (rssMedia.isImage()) {
                    rssItem.mediaImage = rssMedia.url;
                } else if (rssMedia.isVideo()) {
                    rssItem.mediaVideo = rssMedia.url;
                }
            } else {
                skip(xpp);
            }
        }
        return rssItem;
    }

    private RssMedia readMediaContent(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "media:content");
        String mimeType = "";
        String medium = "";
        int attrSize = xpp.getAttributeCount();
        RssMedia rssMedia = new RssMedia();
        for (int i = 0; i < attrSize; i++) {
            switch (xpp.getAttributeName(i)) {
                case "type":
                    mimeType = xpp.getAttributeValue(i);
                    break;
                case "medium":
                    medium = xpp.getAttributeValue(i);
                    break;
                case "url":
                    rssMedia.url = xpp.getAttributeValue(i);
                    break;
            }
        }
        switch (mimeType) {
            case "image/bmp":
            case "image/gif":
            case "image/png":
            case "image/webp":
            case "image/jpeg":
                rssMedia.type = RssMedia.TYPE_IMAGE;
                break;
            case "application/x-shockwave-flash":
                rssMedia.type = RssMedia.TYPE_VIDEO;
                break;
            case "":
                if (medium.equals("image")) {
                    rssMedia.type = RssMedia.TYPE_IMAGE;
                } else if (medium.equals("video")) {
                    rssMedia.type = RssMedia.TYPE_VIDEO;
                }
                break;
        }
        xpp.next();
        xpp.require(XmlPullParser.END_TAG, null, "media:content");
        return rssMedia;
    }

    private RssMedia readMediaThumbnail(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "media:thumbnail");
        int attrSize = xpp.getAttributeCount();
        RssMedia rssMedia = new RssMedia();
        rssMedia.type = RssMedia.TYPE_IMAGE;
        for (int i = 0; i < attrSize; i++) {
            switch (xpp.getAttributeName(i)) {
                case "url":
                    rssMedia.url = xpp.getAttributeValue(i);
                    break;
            }
        }
        xpp.next();
        xpp.require(XmlPullParser.END_TAG, null, "media:thumbnail");
        return rssMedia;
    }

    private RssMedia readEnclosure(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "enclosure");
        String mimeType = "";
        int attrSize = xpp.getAttributeCount();
        RssMedia rssMedia = new RssMedia();
        for (int i = 0; i < attrSize; i++) {
            switch (xpp.getAttributeName(i)) {
                case "type":
                    mimeType = xpp.getAttributeValue(i);
                    break;
                case "url":
                    rssMedia.url = xpp.getAttributeValue(i);
                    break;
            }
        }
        switch (mimeType) {
            case "image/bmp":
            case "image/gif":
            case "image/png":
            case "image/webp":
            case "image/jpeg":
                rssMedia.type = RssMedia.TYPE_IMAGE;
                break;
            case "video/mp4":
            case "video/webm":
            case "video/ogg":
            case "video/3gpp":
            case "video/x-matroska":
            case "application/x-shockwave-flash":
                rssMedia.type = RssMedia.TYPE_VIDEO;
                break;
        }
        xpp.next();
        xpp.require(XmlPullParser.END_TAG, null, "enclosure");
        return rssMedia;
    }

    private Date readPubDate(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "pubDate");
        String dateText = readText(xpp);
        Date pubDate = null;
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
            pubDate = simpleDateFormat.parse(dateText);
        } catch (Throwable throwable) {
            mLogger.d(TAG, "Failed to parse date: " + dateText, throwable);
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

    private static class RssMedia {
        static final int TYPE_IMAGE = 1;
        static final int TYPE_VIDEO = 2;
        String url;
        int type;

        boolean isImage() {
            return type == TYPE_IMAGE;
        }

        boolean isVideo() {
            return type == TYPE_VIDEO;
        }
    }
}
