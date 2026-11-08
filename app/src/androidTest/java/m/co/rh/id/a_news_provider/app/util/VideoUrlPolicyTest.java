package m.co.rh.id.a_news_provider.app.util;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for the {@link VideoUrlPolicy} instance API covering every
 * decision branch: null/blank input, each listed streaming host (exact +
 * subdomain), each page-file extension, each direct-video extension, unknown
 * paths (default-true) and case-insensitivity of both host and path matching.
 */
@RunWith(AndroidJUnit4.class)
public class VideoUrlPolicyTest {

    private VideoUrlPolicy mVideoUrlPolicy;

    @Before
    public void setUp() {
        mVideoUrlPolicy = new VideoUrlPolicy();
    }

    // ---- null / empty ----

    @Test
    public void testNullUrlIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(null));
    }

    @Test
    public void testEmptyUrlIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(""));
    }

    @Test
    public void testBlankUrlIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl("   "));
    }

    // ---- streaming-platform hosts -> false ----

    @Test
    public void testYoutubeHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://www.youtube.com/watch?v=abc"));
    }

    @Test
    public void testMobileYoutubeHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://m.youtube.com/watch?v=abc"));
    }

    @Test
    public void testMusicYoutubeHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://music.youtube.com/watch?v=abc"));
    }

    @Test
    public void testYoutubeShortHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://youtu.be/abc123"));
    }

    @Test
    public void testVimeoHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://vimeo.com/12345"));
    }

    @Test
    public void testDailymotionHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://www.dailymotion.com/video/x8abc"));
    }

    @Test
    public void testBilibiliHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://www.bilibili.com/video/BV1xx411c7mD"));
    }

    @Test
    public void testTwitchHostIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://www.twitch.tv/videos/123456"));
    }

    @Test
    public void testSubdomainOfStreamingHostIsFalse() {
        // suffix-match: gaming.youtube.com is a subdomain of youtube.com
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://gaming.youtube.com/watch?v=abc"));
    }

    @Test
    public void testNotSubdomainOfStreamingHost() {
        // "notyoutube.com" ends with "youtube.com" WITHOUT a dot boundary -
        // must NOT match the host rule
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://notyoutube.com/clip.mp4"));
    }

    @Test
    public void testStreamingHostIsCaseInsensitive() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://WWW.YouTube.COM/watch?v=abc"));
    }

    // ---- page-file extensions -> false ----

    @Test
    public void testHtmlExtensionIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/video.html"));
    }

    @Test
    public void testHtmExtensionIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/video.htm"));
    }

    @Test
    public void testPhpExtensionIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/play.php"));
    }

    @Test
    public void testSwfExtensionIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/legacy.swf"));
    }

    @Test
    public void testPageExtensionBeforeQueryIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/player.html?token=x"));
    }

    @Test
    public void testPageExtensionCaseInsensitiveIsFalse() {
        assertFalse(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/player.HTML"));
    }

    // ---- direct-video extensions -> true ----

    @Test
    public void testMp4IsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.mp4"));
    }

    @Test
    public void testM4vIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.m4v"));
    }

    @Test
    public void testWebmIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.webm"));
    }

    @Test
    public void test3gppIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.3gpp"));
    }

    @Test
    public void testMkvIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.mkv"));
    }

    @Test
    public void testMovIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.mov"));
    }

    @Test
    public void testTsIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.ts"));
    }

    @Test
    public void testVideoExtensionBeforeQueryIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.mp4?token=x#frag"));
    }

    @Test
    public void testVideoExtensionCaseInsensitiveIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.MP4"));
    }

    @Test
    public void testUppercaseHostWithVideoPathIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://CDN.example.COM/media/clip.mp4"));
    }

    // ---- unknown paths -> true (runtime fallback covers mis-detection) ----

    @Test
    public void testUnknownExtensionIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/media/clip.avi"));
    }

    @Test
    public void testNoExtensionIsTrue() {
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl(
                "https://cdn.example.com/stream/12345"));
    }

    @Test
    public void testNoHostAndNoExtensionIsTrue() {
        // scheme-less relative path: no host matches, unknown path -> default true
        assertTrue(mVideoUrlPolicy.isProbablyDirectVideoUrl("stream/12345"));
    }
}
