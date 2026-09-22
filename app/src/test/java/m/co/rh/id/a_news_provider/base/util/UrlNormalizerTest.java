package m.co.rh.id.a_news_provider.base.util;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for UrlNormalizer covering trailing slash stripping,
 * scheme/host casing, whitespace trimming and null handling.
 */
public class UrlNormalizerTest {

    private UrlNormalizer mUrlNormalizer;

    @Before
    public void setUp() {
        mUrlNormalizer = new UrlNormalizer();
    }

    @Test
    public void testNullReturnsNull() {
        assertNull(mUrlNormalizer.normalizeUrl(null));
    }

    @Test
    public void testEmptyStringReturnsEmpty() {
        assertEquals("", mUrlNormalizer.normalizeUrl(""));
    }

    @Test
    public void testSingleTrailingSlashStripped() {
        assertEquals("https://example.com/feed",
                mUrlNormalizer.normalizeUrl("https://example.com/feed/"));
    }

    @Test
    public void testMultipleTrailingSlashesStripped() {
        assertEquals("https://example.com/feed",
                mUrlNormalizer.normalizeUrl("https://example.com/feed///"));
    }

    @Test
    public void testHostCasingLowercased() {
        assertEquals("https://example.com/Feed",
                mUrlNormalizer.normalizeUrl("https://EXAMPLE.com/Feed"));
    }

    @Test
    public void testSchemeCasingLowercased() {
        assertEquals("https://example.com/feed",
                mUrlNormalizer.normalizeUrl("HTTPS://example.com/feed"));
    }

    @Test
    public void testPathCasePreserved() {
        assertEquals("https://example.com/Feed/Path",
                mUrlNormalizer.normalizeUrl("https://example.com/Feed/Path"));
    }

    @Test
    public void testWhitespaceTrimmed() {
        assertEquals("https://example.com/feed",
                mUrlNormalizer.normalizeUrl("  https://example.com/feed  "));
    }

    @Test
    public void testQueryAndFragmentPreservedWithCase() {
        assertEquals("https://example.com/path?Query=Alpha&Z=1#Frag",
                mUrlNormalizer.normalizeUrl("https://EXAMPLE.com/path?Query=Alpha&Z=1#Frag"));
    }

    @Test
    public void testQueryCasePreservedWhenNoPath() {
        assertEquals("https://host?Q=1",
                mUrlNormalizer.normalizeUrl("https://HOST?Q=1"));
    }

    @Test
    public void testFragmentCasePreservedWhenNoPath() {
        assertEquals("https://host#Frag",
                mUrlNormalizer.normalizeUrl("https://HOST#Frag"));
    }

    @Test
    public void testSchemeDelimiterOnlyStaysIntact() {
        assertEquals("https://", mUrlNormalizer.normalizeUrl("https://"));
    }

    @Test
    public void testPathAndQueryCasePreserved() {
        assertEquals("https://host/Path?Q=1",
                mUrlNormalizer.normalizeUrl("https://HOST/Path?Q=1"));
    }

    @Test
    public void testNoPathUrl() {
        assertEquals("https://example.com",
                mUrlNormalizer.normalizeUrl("https://EXAMPLE.com"));
    }

    @Test
    public void testNoPathUrlWithTrailingSlash() {
        assertEquals("https://example.com",
                mUrlNormalizer.normalizeUrl("https://EXAMPLE.com/"));
    }

    @Test
    public void testNoSchemeStripsTrailingSlashOnly() {
        assertEquals("example.com/feed",
                mUrlNormalizer.normalizeUrl("example.com/feed/"));
    }

    @Test
    public void testAlreadyNormalizedUnchanged() {
        assertEquals("https://example.com/feed",
                mUrlNormalizer.normalizeUrl("https://example.com/feed"));
    }
}
