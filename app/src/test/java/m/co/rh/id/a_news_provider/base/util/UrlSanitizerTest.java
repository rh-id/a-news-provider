package m.co.rh.id.a_news_provider.base.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for UrlSanitizer (issue #76): tracking parameter stripping at
 * display time - prefix/global/host-scoped rules, encoded-query fidelity (kept
 * tokens stay byte-for-byte), fragment handling and non-http schemes.
 */
public class UrlSanitizerTest {

    private UrlSanitizer mUrlSanitizer;

    @Before
    public void setUp() {
        mUrlSanitizer = new UrlSanitizer();
    }

    @Test
    public void testNullReturnsNull() {
        assertNull(mUrlSanitizer.sanitize(null));
    }

    @Test
    public void testEmptyStringReturnsEmpty() {
        assertEquals("", mUrlSanitizer.sanitize(""));
    }

    @Test
    public void testUrlWithNoQueryUnchanged() {
        assertEquals("https://example.com/a/b#c",
                mUrlSanitizer.sanitize("https://example.com/a/b#c"));
    }

    @Test
    public void testSingleUtmSourceStripped() {
        assertEquals("https://example.com/post?id=7",
                mUrlSanitizer.sanitize("https://example.com/post?utm_source=feedly&id=7"));
    }

    @Test
    public void testUtmAndMtmPrefixVariantsStripped() {
        assertEquals("https://example.com/",
                mUrlSanitizer.sanitize("https://example.com/?utm_reader=x&utm_social=y&mtm_campaign=z"));
    }

    @Test
    public void testParamNamesMatchedCaseInsensitively() {
        assertEquals("https://example.com/",
                mUrlSanitizer.sanitize("https://example.com/?Utm_Source=1&UTM_SOURCE=1&FbClId=1"));
    }

    @Test
    public void testSocialClickIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?fbclid=IwAR2&ttclid=17long"));
    }

    @Test
    public void testGoogleClickIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?gclid=EAIaIQ&gclsrc=aw.ds&dclid=Cj0K&gbraid=abcd&wbraid=efgh"));
    }

    @Test
    public void testMailchimpIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?mc_cid=abcd&mc_eid=efgh&mc_tc=xyz"));
    }

    @Test
    public void testHubspotIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?_hsenc=abc&__hssc=1&__hstc=2&__hsfp=3&_hsmi=4"));
    }

    @Test
    public void testMixedCaseHsCtaTrackingStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?hsCtaTracking=abc-123"));
    }

    @Test
    public void testAdobeSearchIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?s_kwcid=AL!44&sc_cid=xyz"));
    }

    @Test
    public void testBranchIdsStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?_branch_match_id=123&_branch_referrer=https%3A%2F%2Fx&_bhlid=abc"));
    }

    @Test
    public void testReadwiseLocationStripped() {
        // __readwiseLocation ships with mixed casing in the wild
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?__readwiseLocation=https%3A%2F%2Fe.com"));
    }

    @Test
    public void testGoogleMerchantSrsltidStripped() {
        assertEquals("https://example.com/product",
                mUrlSanitizer.sanitize("https://example.com/product?srsltid=AfmBOo"));
    }

    @Test
    public void testIgshidStripped() {
        assertEquals("https://example.com/reel",
                mUrlSanitizer.sanitize("https://example.com/reel?igshid=abc123"));
    }

    @Test
    public void testDoubleUnderscoreSStripped() {
        assertEquals("https://example.com/a",
                mUrlSanitizer.sanitize("https://example.com/a?__s=abc"));
    }

    @Test
    public void testEveryOccurrenceStripped() {
        assertEquals("https://example.com/?x=1",
                mUrlSanitizer.sanitize("https://example.com/?utm_source=a&x=1&utm_source=b&fbclid=1&fbclid=2"));
    }

    @Test
    public void testKeptParamsKeepOrderAndEncoding() {
        assertEquals("https://example.com/?q=a+b&p=%2B&r=%2b&id=9",
                mUrlSanitizer.sanitize("https://example.com/?q=a+b&p=%2B&r=%2b&id=9"));
    }

    @Test
    public void testKeptParamsKeepOrderAndEncodingAroundStrippedOnes() {
        assertEquals("https://example.com/?q=a+b&p=%2B&r=%2b",
                mUrlSanitizer.sanitize("https://example.com/?fbclid=z&q=a+b&p=%2B&r=%2b"));
    }

    @Test
    public void testFragmentPreservedWhenStripping() {
        assertEquals("https://example.com/p?id=1#section",
                mUrlSanitizer.sanitize("https://example.com/p?fbclid=1&id=1#section"));
    }

    @Test
    public void testQuestionInsideFragmentNotQueryStart() {
        // the '?' after '#' is fragment content: no query, nothing stripped
        assertEquals("https://example.com/p#f?utm_source=x",
                mUrlSanitizer.sanitize("https://example.com/p#f?utm_source=x"));
    }

    @Test
    public void testQueryBeforeFragmentOnlyProcessed() {
        assertEquals("https://example.com/p?x=1#f?utm_source=y",
                mUrlSanitizer.sanitize("https://example.com/p?x=1#f?utm_source=y"));
    }

    @Test
    public void testValuelessParamStripped() {
        assertEquals("https://example.com/?x=1",
                mUrlSanitizer.sanitize("https://example.com/?fbclid&x=1"));
    }

    @Test
    public void testEmptyNameTokenKept() {
        assertEquals("https://example.com/?=x",
                mUrlSanitizer.sanitize("https://example.com/?=x&fbclid=y"));
    }

    @Test
    public void testNameStopsAtFirstEquals() {
        assertEquals("https://example.com/?a=b=c",
                mUrlSanitizer.sanitize("https://example.com/?a=b=c&fbclid=z"));
    }

    @Test
    public void testKeptValueWithEncodedAmpersandPreserved() {
        assertEquals("https://example.com/?a=b%26c",
                mUrlSanitizer.sanitize("https://example.com/?a=b%26c&fbclid=z"));
    }

    @Test
    public void testEmptiedQueryDropsQuestionMark() {
        assertEquals("https://example.com/p",
                mUrlSanitizer.sanitize("https://example.com/p?fbclid=1"));
    }

    @Test
    public void testEmptiedQueryKeepsFragment() {
        assertEquals("https://example.com/p#top",
                mUrlSanitizer.sanitize("https://example.com/p?fbclid=1#top"));
    }

    @Test
    public void testSiStrippedOnYouTubeWatch() {
        assertEquals("https://www.youtube.com/watch?v=abc",
                mUrlSanitizer.sanitize("https://www.youtube.com/watch?v=abc&si=X"));
    }

    @Test
    public void testSiStrippedOnYouTubeShortHost() {
        assertEquals("https://youtu.be/x",
                mUrlSanitizer.sanitize("https://youtu.be/x?si=X"));
    }

    @Test
    public void testSiStrippedOnYouTubeMusic() {
        assertEquals("https://music.youtube.com/watch?v=abc",
                mUrlSanitizer.sanitize("https://music.youtube.com/watch?v=abc&si=X"));
    }

    @Test
    public void testSiStrippedOnSpotify() {
        assertEquals("https://open.spotify.com/track/123",
                mUrlSanitizer.sanitize("https://open.spotify.com/track/123?si=XYZ"));
    }

    @Test
    public void testSiKeptOnOtherHosts() {
        assertEquals("https://example.com/?si=X",
                mUrlSanitizer.sanitize("https://example.com/?si=X"));
    }

    @Test
    public void testRefSrcRefUrlStrippedOnX() {
        assertEquals("https://x.com/user/status/1",
                mUrlSanitizer.sanitize("https://x.com/user/status/1?ref_src=twsrc%5Etfw&ref_url=https%3A%2F%2Fx.com%2F"));
    }

    @Test
    public void testRefSrcStrippedOnTwitter() {
        assertEquals("https://twitter.com/user",
                mUrlSanitizer.sanitize("https://twitter.com/user?ref_src=twsrc%5Egoogle"));
    }

    @Test
    public void testRefSrcKeptOnOtherHosts() {
        assertEquals("https://example.com/?ref_src=x",
                mUrlSanitizer.sanitize("https://example.com/?ref_src=x"));
    }

    @Test
    public void testMktTokKeptOnUnsubscribeLinks() {
        assertEquals("https://mail.example.com/unsubscribe?mkt_tok=abc",
                mUrlSanitizer.sanitize("https://mail.example.com/unsubscribe?mkt_tok=abc"));
    }

    @Test
    public void testMktTokKeptOnUnsubscribeLinksCaseInsensitive() {
        assertEquals("https://mail.example.com/UNSUBSCRIBE?mkt_tok=abc",
                mUrlSanitizer.sanitize("https://mail.example.com/UNSUBSCRIBE?mkt_tok=abc"));
    }

    @Test
    public void testMktTokStrippedWithoutUnsubscribe() {
        assertEquals("https://mail.example.com/subscribe",
                mUrlSanitizer.sanitize("https://mail.example.com/subscribe?mkt_tok=abc"));
    }

    @Test
    public void testMailtoUnchanged() {
        assertEquals("mailto:user@host?utm_source=x",
                mUrlSanitizer.sanitize("mailto:user@host?utm_source=x"));
    }

    @Test
    public void testNonHttpSchemeUnchanged() {
        assertEquals("market://details?id=x&utm_source=y",
                mUrlSanitizer.sanitize("market://details?id=x&utm_source=y"));
    }

    @Test
    public void testNoSchemeStringUnchanged() {
        assertEquals("example.com/?utm_source=x",
                mUrlSanitizer.sanitize("example.com/?utm_source=x"));
    }

    @Test
    public void testRelativeStringUnchanged() {
        assertEquals("/path?utm_source=x",
                mUrlSanitizer.sanitize("/path?utm_source=x"));
    }

    @Test
    public void testUpperCaseSchemeStillSanitized() {
        assertEquals("HTTPS://example.com/a",
                mUrlSanitizer.sanitize("HTTPS://example.com/a?FBCLID=1"));
    }

    @Test
    public void testPortIgnoredForHostScope() {
        assertEquals("https://www.youtube.com:443/watch?v=a",
                mUrlSanitizer.sanitize("https://www.youtube.com:443/watch?v=a&si=X"));
    }

    @Test
    public void testGenericFunctionalParamsKept() {
        // non-goal (issue #76): ref/source/feature-style params can be
        // functionally required and are deliberately not stripped globally
        assertEquals("https://example.com/v?ref=news&source=rss&feature=youtu.be",
                mUrlSanitizer.sanitize("https://example.com/v?ref=news&source=rss&feature=youtu.be"));
    }
}
