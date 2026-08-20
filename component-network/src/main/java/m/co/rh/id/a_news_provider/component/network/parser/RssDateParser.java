package m.co.rh.id.a_news_provider.component.network.parser;

import android.os.Build;

import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;

import m.co.rh.id.alogger.ILogger;

class RssDateParser {
    private static final String TAG = RssDateParser.class.getName();

    /**
     * Parses RFC-822 formatted dates (used in RSS pubDate)
     *
     * @param dateText the date string to parse
     * @param logger optional logger for debug output
     * @return the parsed Date, or null if parsing fails
     */
    @Nullable
    static Date parsePubDate(String dateText, ILogger logger) {
        Date pubDate = null;
        String[] formats = {
            "EEE, d MMM yyyy HH:mm:ss Z",
            "EEE, d MMM yyyy HH:mm:ss z",
            "EEE, d MMM yyyy HH:mm Z",
            "EEE, d MMM yyyy HH:mm:ss",
            "d MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        };
        for (String format : formats) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
                pubDate = simpleDateFormat.parse(dateText);
                if (pubDate != null) break;
            } catch (Throwable throwable) {
                // Try next format
            }
        }
        if (pubDate == null && logger != null) {
            logger.d(TAG, "Failed to parse date: " + dateText);
        }
        return pubDate;
    }

    /**
     * Parses ISO-8601 formatted dates (used in Atom updated)
     *
     * @param dateText the date string to parse
     * @param logger optional logger for debug output
     * @return the parsed Date, or null if parsing fails
     */
    @Nullable
    static Date parseUpdated(String dateText, ILogger logger) {
        Date pubDate = null;
        String[] formats;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            formats = new String[]{
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
            };
        } else {
            formats = new String[]{
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssZ",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
            };
        }
        for (String format : formats) {
            try {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(format);
                pubDate = simpleDateFormat.parse(dateText);
                if (pubDate != null) break;
            } catch (Throwable throwable) {
                // Try next format
            }
        }
        if (pubDate == null && logger != null) {
            logger.d(TAG, "Failed to parse date: " + dateText);
        }
        return pubDate;
    }
}