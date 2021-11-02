package m.co.rh.id.a_news_provider.app.provider.parser;

import android.content.Context;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.NewRssChannelCmd;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderValue;

public class OpmlParser {
    private static final String TAG = OpmlParser.class.getName();
    private final Context mAppContext;
    private final ProviderValue<ILogger> mLogger;
    private final ProviderValue<NewRssChannelCmd> mNewRssChannelCmd;

    public OpmlParser(Provider provider, Context context) {
        mAppContext = context.getApplicationContext();
        mLogger = provider.lazyGet(ILogger.class);
        mNewRssChannelCmd = provider.lazyGet(NewRssChannelCmd.class);
    }

    public void parse(File opmlFile) {
        StringBuilder stringBuilder = new StringBuilder();
        try (FileInputStream fileInputStream = new FileInputStream(opmlFile);
             InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
        ) {
            char[] buff = new char[2048];
            int b = bufferedReader.read(buff);
            while (b != -1) {
                stringBuilder.append(buff);
                b = bufferedReader.read(buff);
            }
        } catch (Throwable throwable) {
            mLogger.get().e(TAG,
                    mAppContext.getString(R.string.error_failed_to_open_file),
                    throwable);
            return;
        }
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();
            xpp.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            xpp.setInput(new StringReader(stringBuilder.toString()));
            xpp.nextTag();
            xpp.require(XmlPullParser.START_TAG, null, "opml");
            while (xpp.next() != XmlPullParser.END_TAG) {
                if (xpp.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }
                String name = xpp.getName();
                if (name.equals("body")) {
                    readBody(xpp);
                } else {
                    skip(xpp);
                }
            }
        } catch (Throwable throwable) {
            mLogger.get().e(TAG,
                    mAppContext.getString(R.string.error_parsing_opml_file),
                    throwable);
        }
    }

    private void readBody(XmlPullParser xpp) throws IOException, XmlPullParserException {
        xpp.require(XmlPullParser.START_TAG, null, "body");
        List<String> rssUrls = new ArrayList<>();
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("outline")) {
                readNestedOutline(xpp, rssUrls);
            } else {
                skip(xpp);
            }
        }
        mLogger.get().i(TAG, mAppContext.getString(R.string.added_rss, rssUrls.size()));
        mLogger.get().d(TAG, "RSS URLS: " + rssUrls);
    }

    private void readNestedOutline(XmlPullParser xpp, List<String> rssUrls) throws IOException, XmlPullParserException {
        String type = xpp.getAttributeValue(null, "type");
        if ("rss".equals(type)) {
            String xmlUrl = xpp.getAttributeValue(null, "xmlUrl");
            if (xmlUrl != null && !xmlUrl.isEmpty()) {
                mNewRssChannelCmd.get()
                        .execute(xmlUrl);
                rssUrls.add(xmlUrl);
            }
        }
        while (xpp.next() != XmlPullParser.END_TAG) {
            if (xpp.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = xpp.getName();
            if (name.equals("outline")) {
                readNestedOutline(xpp, rssUrls);
            } else {
                skip(xpp);
            }
        }
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
