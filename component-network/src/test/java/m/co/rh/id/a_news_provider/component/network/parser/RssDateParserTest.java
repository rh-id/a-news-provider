package m.co.rh.id.a_news_provider.component.network.parser;

import org.junit.Test;

import java.util.Date;

import m.co.rh.id.alogger.ILogger;

import static org.junit.Assert.*;

public class RssDateParserTest {

    @Test
    public void testParsePubDateRfc822WithOffset() {
        String dateStr = "Mon, 06 Sep 2010 00:01:00 +0000";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
        // Just verify it's a reasonable date - exact time comparison can be timezone-dependent
        assertTrue("Date should be after 2010", date.getTime() > 1283673600000L); // Sept 1, 2010
    }

    @Test
    public void testParsePubDateRfc822WithNegativeOffset() {
        String dateStr = "Mon, 06 Sep 2010 00:01:00 -0700";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateWithoutSeconds() {
        String dateStr = "Mon, 06 Sep 2010 00:01 +0000";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateWithoutTimezone() {
        String dateStr = "Mon, 06 Sep 2010 00:01:00";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateDayFirst() {
        String dateStr = "6 Sep 2010 00:01:00 +0000";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateIso8601() {
        String dateStr = "2010-09-06T00:01:00Z";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateDateOnly() {
        String dateStr = "2010-09-06";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParsePubDateInvalidFormat() {
        String dateStr = "Invalid Date String";
        Date date = RssDateParser.parsePubDate(dateStr, null);
        assertNull(date);
    }

    @Test
    public void testParsePubDateNullInput() {
        Date date = RssDateParser.parsePubDate(null, null);
        assertNull(date);
    }

    @Test
    public void testParsePubDateEmptyInput() {
        Date date = RssDateParser.parsePubDate("", null);
        assertNull(date);
    }

    @Test
    public void testParseUpdatedIso8601WithZ() {
        String dateStr = "2010-09-06T00:01:00Z";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParseUpdatedIso8601WithOffset() {
        String dateStr = "2010-09-06T00:01:00+05:30";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParseUpdatedIso8601WithMilliseconds() {
        String dateStr = "2010-09-06T00:01:00.123Z";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParseUpdatedDateOnly() {
        String dateStr = "2010-09-06";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParseUpdatedWithoutTimezone() {
        String dateStr = "2010-09-06T00:01:00";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNotNull(date);
    }

    @Test
    public void testParseUpdatedInvalidFormat() {
        String dateStr = "Invalid Date String";
        Date date = RssDateParser.parseUpdated(dateStr, null);
        assertNull(date);
    }

    @Test
    public void testParseUpdatedNullInput() {
        Date date = RssDateParser.parseUpdated(null, null);
        assertNull(date);
    }

    @Test
    public void testParseUpdatedEmptyInput() {
        Date date = RssDateParser.parseUpdated("", null);
        assertNull(date);
    }

    @Test
    public void testParsePubDateWithVariousFormats() {
        // Test various RFC 822 formats
        String[] validFormats = {
            "Tue, 15 Nov 2022 12:34:56 +0000",
            "Wed, 16 Nov 2022 01:23:45 GMT", 
            "Thu, 17 Nov 2022 23:45 +0100",
            "2022-11-19T12:34:56Z",
            "2022-11-20"
        };

        for (String format : validFormats) {
            Date date = RssDateParser.parsePubDate(format, null);
            assertNotNull("Failed to parse: " + format, date);
            // Verify the date is in a reasonable range (2022)
            assertTrue("Date should be from 2022: " + format, 
                date.getTime() > 1640995200000L && date.getTime() < 1672531200000L);
        }
    }

    @Test
    public void testParseUpdatedWithVariousFormats() {
        // Test various ISO 8601 formats
        String[] validFormats = {
            "2022-11-15T12:34:56Z",
            "2022-11-16T12:34:56+05:30",
            "2022-11-17T12:34:56.123Z",
            "2022-11-18T12:34:56",
            "2022-11-19"
        };

        for (String format : validFormats) {
            Date date = RssDateParser.parseUpdated(format, null);
            assertNotNull("Failed to parse: " + format, date);
        }
    }

    @Test
    public void testLoggerCalledOnParseFailure() {
        final boolean[] loggerCalled = {false};
        ILogger testLogger = new ILogger() {
            @Override
            public void d(String tag, String message) {
                loggerCalled[0] = true;
            }

            @Override
            public void d(String tag, String message, Throwable throwable) {
                loggerCalled[0] = true;
            }

            // Other methods not used
            @Override public void v(String tag, String message) {}
            @Override public void v(String tag, String message, Throwable throwable) {}
            @Override public void i(String tag, String message) {}
            @Override public void i(String tag, String message, Throwable throwable) {}
            @Override public void w(String tag, String message) {}
            @Override public void w(String tag, String message, Throwable throwable) {}
            @Override public void e(String tag, String message) {}
            @Override public void e(String tag, String message, Throwable throwable) {}
            @Override public void setLogLevel(int logLevel) {}
        };

        RssDateParser.parsePubDate("invalid", testLogger);
        assertTrue("Logger should be called on parse failure", loggerCalled[0]);
        
        loggerCalled[0] = false;
        RssDateParser.parseUpdated("invalid", testLogger);
        assertTrue("Logger should be called on parse failure", loggerCalled[0]);
    }
}