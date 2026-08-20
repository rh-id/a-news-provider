package m.co.rh.id.a_news_provider.component.network.parser;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;

import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.model.RssModel;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

public class RssFeedParser {
    private static final String TAG = RssFeedParser.class.getName();
    private final ILogger mLogger;

    public RssFeedParser(Provider provider) {
        mLogger = provider.get(ILogger.class);
    }

    /**
     * Parse RSS/Atom/RDF feed XML and return RssModel
     *
     * @param xml the XML string to parse
     * @param url the feed URL to set on the parsed channel
     * @return the parsed RssModel
     * @throws XmlPullParserException if the XML is malformed or format is not recognized
     * @throws IOException if there's an IO error during parsing
     */
    public RssModel parse(String xml, String url) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        xpp.setInput(new StringReader(xml));
        xpp.nextTag();

        String rootTag = xpp.getName();
        RssModel rssModel = null;

        switch (rootTag) {
            case "rss":
                xpp.require(XmlPullParser.START_TAG, null, "rss");
                while (xpp.next() != XmlPullParser.END_TAG) {
                    if (xpp.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String name = xpp.getName();
                    if (name.equals("channel")) {
                        rssModel = readChannel(xpp, url);
                    } else {
                        skip(xpp);
                    }
                }
                break;
            case "feed":
                xpp.require(XmlPullParser.START_TAG, null, "feed");
                rssModel = readFeed(xpp, url);
                break;
            case "rdf:RDF":
                xpp.require(XmlPullParser.START_TAG, null, "rdf:RDF");
                rssModel = readRdf(xpp, url);
                break;
            default:
                throw new XmlPullParserException("Unrecognized root tag: " + rootTag);
        }

        if (mLogger != null) {
            mLogger.v(TAG, "Parsed RssModel: " + rssModel);
        }
        return rssModel;
    }

    private RssModel readChannel(XmlPullParser xpp, String url) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "channel");
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
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

    // RDF XML
    private RssModel readRdf(XmlPullParser xpp, String url) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "rdf:RDF");
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
        ArrayList<RssItem> rssItemList = new ArrayList<>();
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("channel")) {
                while (xpp.next() != XmlPullParser.END_TAG) {
                    if (xpp.getEventType() != XmlPullParser.START_TAG) {
                        continue;
                    }
                    String channelXppName = xpp.getName();
                    if (channelXppName.equals("title")) {
                        rssChannel.title = readTitle(xpp);
                        rssChannel.feedName = rssChannel.title;
                    } else if (channelXppName.equals("link")) {
                        rssChannel.link = readLink(xpp);
                    } else if (channelXppName.equals("description")) {
                        rssChannel.description = readDescription(xpp);
                    } else {
                        skip(xpp);
                    }
                }
            } else if (name.equals("item")) {
                rssItemList.add(readItem(xpp));
            } else {
                skip(xpp);
            }
        }
        return new RssModel(rssChannel, rssItemList);
    }

    // Atom XML
    private RssModel readFeed(XmlPullParser xpp, String url) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "feed");
        RssChannel rssChannel = new RssChannel();
        rssChannel.url = url;
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
        Date pubDate = RssDateParser.parseUpdated(dateText, mLogger);
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
                } else {
                    // if nothing matches by default it is image
                    rssMedia.type = RssMedia.TYPE_IMAGE;
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
        Date pubDate = RssDateParser.parsePubDate(dateText, mLogger);
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
}
