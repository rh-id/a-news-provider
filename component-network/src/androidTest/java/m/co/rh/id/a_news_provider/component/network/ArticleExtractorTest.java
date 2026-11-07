package m.co.rh.id.a_news_provider.component.network;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for {@link ArticleExtractor} (moved from the unit-test
 * source set per maintainer decision, like DiskLruBitmapCacheTest before it)
 * running against HTML fixtures under src/androidTest/resources: a simple
 * article, a boilerplate-heavy page, a windows-1252 meta-charset page (raw
 * bytes, exercising Jsoup charset detection), a page with hostile markup
 * (script/iframe/javascript: href), an NPR-style page whose audio-player embed
 * snippet is stored as ESCAPED TEXT (dropped as noise while prose mentioning
 * iframes survives), a too-short page, and relative link absolutization with
 * alt-text handling.
 */
@RunWith(AndroidJUnit4.class)
public class ArticleExtractorTest {

    private static final String SIMPLE_BASE_URI = "https://site.example/posts/simple";
    private static final String NOISY_BASE_URI = "https://site.example/posts/noisy";

    /**
     * Real extractor instance: the extractor is a stateless singleton
     * (NetworkProviderModule registers it lazily), so a directly constructed
     * one behaves identically - extraction stays under test as it runs in
     * production, and the {@link ArticleExtractor#allowedTags()} helper is
     * called on this instance.
     */
    private final ArticleExtractor mArticleExtractor = new ArticleExtractor();

    private static byte[] readFixtureBytes(String fixtureName) throws IOException {
        InputStream inputStream = ArticleExtractorTest.class
                .getResourceAsStream("/" + fixtureName);
        assertNotNull("fixture missing: " + fixtureName, inputStream);
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        } finally {
            inputStream.close();
        }
    }

    private static String readFixture(String fixtureName) throws IOException {
        return new String(readFixtureBytes(fixtureName), StandardCharsets.UTF_8);
    }

    @Test
    public void extract_simpleArticle_returnsSanitizedParagraphs() throws IOException {
        String result = mArticleExtractor.extract(readFixture("article_simple.html"),
                SIMPLE_BASE_URI);

        assertNotNull(result);
        assertTrue(result.contains("Simple Article Title"));
        assertTrue(result.contains("first paragraph of a very simple article"));
        assertTrue(result.contains("<p>"));
        // the article element itself is not on the safelist: unwrapped, never emitted
        assertFalse(result.contains("<article"));
        assertFalse(result.contains("<html"));
        assertFalse(result.contains("<head"));
    }

    @Test
    public void extract_noisyArticle_dropsNavAdsFooterAndScript() throws IOException {
        String result = mArticleExtractor.extract(readFixture("article_noisy.html"),
                NOISY_BASE_URI);

        assertNotNull(result);
        assertTrue(result.contains("Noisy Article Title"));
        assertTrue(result.contains("The article body starts here"));
        // navigation menu
        assertFalse(result.contains("Tech section"));
        // advertisement banner
        assertFalse(result.contains("Buy this thing"));
        // sidebar / most-read
        assertFalse(result.contains("Most read"));
        // footer block
        assertFalse(result.contains("Copyright notice"));
        // script payload
        assertFalse(result.contains("injected"));
        assertFalse(result.contains("<script"));
    }

    @Test
    public void extract_windows1252Bytes_decodesViaMetaCharset() throws IOException {
        // raw fixture bytes encoded in windows-1252 (0xE9 = e-acute, 0x93/0x94 =
        // curly quotes, 0x96 = en dash); only the page's <meta charset> can
        // decode them, proving the byte[] path delegates charset detection
        byte[] fixtureBytes = readFixtureBytes("article_windows1252.html");

        String result = mArticleExtractor.extract(fixtureBytes,
                "https://legacy.example/article");

        assertNotNull(result);
        assertTrue(result.contains("Legacy Encoding Article"));
        assertTrue(result.contains("caf\u00e9"));
        assertTrue(result.contains("\u201Ceuros\u201D"));
        assertTrue(result.contains("\u2013"));
    }

    @Test
    public void extract_hostilePage_stripsScriptIframeFormsAndUnsafeProtocols()
            throws IOException {
        String result = mArticleExtractor.extract(readFixture("article_hostile.html"),
                "https://site.example/hostile");

        assertNotNull(result);
        assertTrue(result.contains("Hostile Article Title"));
        // script (content included)
        assertFalse(result.contains("<script"));
        assertFalse(result.contains("script payload"));
        // iframe and its source
        assertFalse(result.contains("<iframe"));
        assertFalse(result.contains("tracking.example"));
        // forms and form controls
        assertFalse(result.contains("<form"));
        assertFalse(result.contains("<input"));
        // unsafe link protocols: href is stripped, plain link text survives
        assertFalse(result.contains("javascript:"));
        assertFalse(result.contains("data:text/html"));
        assertTrue(result.contains("a javascript link"));
        // tables are stripped too (TextView renders them badly), their text survives
        assertFalse(result.contains("<table"));
        assertFalse(result.contains("<td"));
    }

    @Test
    public void extract_embedCodeSnippetWidget_isDroppedAsText() throws IOException {
        // NPR-style page: the audio-player widget's embed snippet is stored as
        // ESCAPED TEXT inside <b><code> rows, so the safelist cannot touch it -
        // it must be removed as noise, not rendered as article text
        String result = mArticleExtractor.extract(readFixture("article_embed_code.html"),
                "https://site.example/embed-code");

        assertNotNull(result);
        // no trace of the escaped (or any) iframe markup survives as text
        assertFalse(result.toLowerCase(Locale.US).contains("iframe"));
        assertFalse(result.contains("player/embed"));
        assertFalse(result.contains("<code>"));
        // the real paragraphs around the widget all survive
        assertTrue(result.contains("Embed Code Widget Story"));
        assertTrue(result.contains("morning story about the storm"));
        assertTrue(result.contains("repairs would continue through the winter"));
        assertTrue(result.contains("vote on the final budget"));
    }

    @Test
    public void extract_proseMentioningIframe_midSentence_survives() {
        // the over-stripping guard: legitimate prose that merely mentions the
        // iframe element as a word, or quotes the markup mid-sentence, is kept
        // (only paragraph-level blocks STARTING with the markup are dropped)
        String html = "<html><body><article>"
                + "<h1>About embedding techniques</h1>"
                + "<p>Standards authors explain that the iframe element can embed "
                + "content from another document, while newer primitives try to "
                + "replace it with cleaner mechanisms.</p>"
                + "<p>When teaching markup, trainers often quote a literal "
                + "&lt;iframe src=\"https://site.example/player\"&gt; snippet in "
                + "the middle of a sentence without ever embedding an actual "
                + "player on the page.</p>"
                + "</article></body></html>";

        String result = mArticleExtractor.extract(html, "https://site.example/prose");

        assertNotNull(result);
        assertTrue(result.contains("the iframe element can embed"));
        assertTrue(result.contains("quote a literal"));
    }

    @Test
    public void extract_relativeLinks_absolutizedAndImageAltKeptAsText()
            throws IOException {
        String result = mArticleExtractor.extract(
                readFixture("article_relative_links.html"),
                "https://site.example/dir/page.html");

        assertNotNull(result);
        // root-relative and relative hrefs are resolved against the baseUri
        assertTrue(result.contains("https://site.example/related/story"));
        assertTrue(result.contains("https://site.example/dir/page-two.html"));
        // img with alt text: tag stripped, alt kept as plain text
        assertTrue(result.contains("Downloads chart 2026"));
        // img without alt: dropped entirely
        assertFalse(result.contains("decorative"));
        assertFalse(result.contains("<img"));
    }

    @Test
    public void extract_shortPage_returnsNullGracefully() throws IOException {
        String result = mArticleExtractor.extract(readFixture("article_short.html"),
                "https://site.example/short");

        assertNull(result);
    }

    @Test
    public void extract_nullOrEmptyInput_returnsNull() {
        assertNull(mArticleExtractor.extract((String) null, SIMPLE_BASE_URI));
        assertNull(mArticleExtractor.extract("   ", SIMPLE_BASE_URI));
        assertNull(mArticleExtractor.extract((byte[]) null, SIMPLE_BASE_URI));
        assertNull(mArticleExtractor.extract(new byte[0], SIMPLE_BASE_URI));
        // null baseUri must not throw; the short page still has no article body
        assertNull(mArticleExtractor.extract(readShortInline(), null));
    }

    @Test
    public void extract_isDeterministicForIdenticalInput() throws IOException {
        String html = readFixture("article_noisy.html");
        String first = mArticleExtractor.extract(html, NOISY_BASE_URI);
        String second = mArticleExtractor.extract(html, NOISY_BASE_URI);
        String bytePathResult = mArticleExtractor.extract(
                html.getBytes(StandardCharsets.UTF_8), NOISY_BASE_URI);

        assertNotNull(first);
        assertEquals(first, second);
        // String and byte[] paths of the same utf-8 input converge
        assertEquals(first, bytePathResult);
    }

    @Test
    public void extract_sanitizedOutputKeepsOnlySafelistedTags() throws IOException {
        // the hostile fixture carries the widest tag variety: assert that every
        // tag of the sanitized output is on the extractor's safelist
        String result = mArticleExtractor.extract(readFixture("article_hostile.html"),
                "https://site.example/hostile");

        assertNotNull(result);
        List<String> allowedTags = mArticleExtractor.allowedTags();
        Elements outputElements = Jsoup.parseBodyFragment(result).body().select("*");
        assertTrue(outputElements.size() > 0);
        for (Element outputElement : outputElements) {
            // the synthetic wrapper element from the re-parse is not part of
            // the sanitized output itself
            if ("body".equals(outputElement.tagName())) {
                continue;
            }
            assertTrue("unexpected tag <" + outputElement.tagName() + "> in output: "
                            + result,
                    allowedTags.contains(outputElement.tagName()));
        }
    }

    private static String readShortInline() {
        return "<html><body><p>bye</p></body></html>";
    }
}
