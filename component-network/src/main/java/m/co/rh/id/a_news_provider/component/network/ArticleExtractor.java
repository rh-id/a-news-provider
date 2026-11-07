package m.co.rh.id.a_news_provider.component.network;

import org.jsoup.Jsoup;
import org.jsoup.helper.DataUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Readability-style full-text extractor for article pages (issue #28 phase 1).
 * Parses fetched article HTML, heuristically locates the article body, and
 * returns a sanitized, text-only HTML fragment that the detail page renders in
 * its existing {@code text_content} TextView.
 * <p>
 * Pipeline (all steps deterministic, no randomness):
 * <ol>
 * <li><b>Parse</b> - {@code extract(byte[], baseUri)} delegates charset
 * detection to Jsoup (BOM / {@code <meta charset>} win over the HTTP header,
 * which real-world legacy pages frequently contradict).</li>
 * <li><b>Noise removal</b> - structural noise
 * (script/style/noscript/template/iframe/form controls/svg/video, nav, header,
 * footer, aside) and elements whose class or id tokens mark them as ads,
 * social/share widgets, comments, sidebars, cookie banners, newsletter prompts
     * or pagination are dropped; inline {@code display:none}/{@code visibility:hidden}
     * elements are dropped too. Escaped embed-code TEXT (copy-paste player
     * snippets such as NPR's {@code &lt;iframe src=…&gt;} widget) is dropped as
     * well, via a two-tier rule that keeps mid-sentence prose mentions of
     * iframes alive ({@link #removeEmbedCodeNoise(Document)}).</li>
 * <li><b>Image handling</b> - images are NEVER rendered (inline images are not
 * planned; see the closed issue #96 - text-only rendering is deliberate), so
 * every {@code <img>} is removed. An img carrying meaningful {@code alt} text
 * has that text kept as a plain text node, preserving chart/diagram
 * descriptions that would otherwise be lost.</li>
 * <li><b>Root selection</b> - candidate containers (article/main/section/div/body)
 * are scored by text density: paragraph text (capped per paragraph) with a
 * link-density penalty (nav/link farms collapse toward 0), tag bonuses for
 * article/main, class/id bonuses for article/story/post/entry/content names
 * and penalties for comment/sidebar/footer/widget names. The highest-scoring
 * candidate wins; ties go to the outermost (document order), keeping the
 * result stable for identical input.</li>
 * <li><b>Sanitize</b> - a restrictive Safelist keeps only what a TextView
 * renders well: p, br, hr, h1-h6, ul, ol, li, blockquote, pre, code, strong,
 * b, em, i, u, s, sup, sub, span and a[href]. NO img/script/iframe/style/form
 * and NO table (TextView lays tables out as concatenated text). Links allow
 * only http/https/mailto protocols - a {@code javascript:} href is stripped
 * down to plain link text. Relative URLs are resolved against the page's
 * baseUri during cleaning. Non-whitelisted wrapper elements are unwrapped by
 * Jsoup's cleaner, so their text survives; script/style content (Jsoup data
 * nodes) does not.</li>
 * </ol>
 * Returns null (never throws) when no article body can be located or the
 * sanitized output is empty - the caller treats that as a failed extraction.
 * Pure Jsoup, no Android imports, so it runs from background executors and on
 * devices alike. Stateless singleton, Provider-registered (lazy) in
 * {@code NetworkProviderModule} and resolved like any other dependency; the
 * no-arg constructor exists so tests can instantiate it directly too. Every
 * field is final and initialized exactly once in the constructor - the
 * instance carries ZERO mutable state and is thread-safe by design: the one
 * singleton safely serves concurrent fetches on the command executor threads.
 */
public final class ArticleExtractor {

    /**
     * Candidates shorter than this are never selected as the article root.
     */
    private final int MIN_CANDIDATE_TEXT_LENGTH = 50;

    /**
     * Below this total text length a page has no article body at all (login
     * walls, "page unavailable", JS shells) - the extractor returns null
     * instead of wrapping the remnant as content.
     */
    private final int MIN_ARTICLE_TEXT_LENGTH = 100;

    /**
     * A paragraph shorter than this is ignored by the scoring (headlines,
     * bylines, button labels).
     */
    private final int MIN_PARAGRAPH_TEXT_LENGTH = 20;

    /**
     * Per-paragraph score cap: one huge paragraph must not dominate the score
     * of a well-structured article with many medium ones.
     */
    private final int MAX_PARAGRAPH_SCORE = 300;

    private final double TAG_BONUS_ARTICLE = 1.25;
    private final double TAG_BONUS_MAIN = 1.15;
    private final double CLASS_BONUS = 1.15;
    private final double CLASS_PENALTY = 0.5;

    /**
     * The restrictive render Safelist (see class javadoc for the rationale).
     * a[href] is protocol-restricted; relative links are absolutized against
     * the document baseUri by the cleaner (preserveRelativeLinks defaults to
     * false, which is exactly what a cached offline fragment needs).
     */
    private final Safelist SAFELIST = Safelist.none()
            .addTags("p", "br", "hr",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li", "blockquote", "pre", "code",
                    "strong", "b", "em", "i", "u", "s", "sup", "sub", "span", "a")
            .addAttributes("a", "href")
            .addProtocols("a", "href", "http", "https", "mailto");

    /**
     * Tags that never carry article text. Removing them BEFORE root scoring
     * keeps their bulk out of the candidates' link density and text mass.
     */
    private final String NOISE_TAG_SELECTOR =
            "script, style, noscript, template, iframe, frame, object, embed, "
                    + "form, button, input, select, textarea, label, "
                    + "svg, canvas, video, audio, picture, source, "
                    + "nav, header, footer, aside, dialog";

    /**
     * Two-tier selector for the escaped-embed-code noise rule (see
     * {@link #removeEmbedCodeNoise(Document)}): tier 1 (dropped when their text
     * CONTAINS iframe markup anywhere) are the small inline/formatting
     * containers plus list rows that carry copy-paste snippets; tier 2 (dropped
     * only when their text STARTS WITH iframe markup) are paragraph-level
     * blocks, so prose that merely quotes or mentions iframes mid-sentence
     * survives.
     */
    private final String EMBED_CODE_CONTAINS_TAG_SELECTOR =
            "code, pre, b, strong, span, small, em, i, li";

    /**
     * Paragraph-level blocks for the second tier of the escaped-embed-code
     * noise rule: prefix match only (see {@link #removeEmbedCodeNoise(Document)}).
     */
    private final String EMBED_CODE_PREFIX_TAG_SELECTOR = "p, div";

    /**
     * Class/id tokens that mark an element as non-content boilerplate. Matched
     * against lowercased word parts (split on anything that is not a letter),
     * so "ad-container" matches "ad" while "reader" never matches "ad" and
     * "read" never does either.
     */
    private final Set<String> mJunkClassTokens;

    /**
     * Builds the boilerplate token set once: every field is final and
     * initialized exactly once here, so an instance is immutable after
     * construction and safely shareable across threads.
     */
    public ArticleExtractor() {
        mJunkClassTokens = new HashSet<>();
        String[] tokens = {
                "ad", "ads", "advert", "adverts", "advertisement", "advertisements",
                "banner", "banners", "promo", "promos", "sponsor", "sponsored",
                "social", "socials", "share", "shares", "sharing",
                "comment", "comments", "commentlist", "disqus",
                "related", "recommend", "recommended", "recommendations",
                "sidebar", "widget", "widgets",
                "menu", "menus", "nav", "navbar", "navigation", "breadcrumb", "breadcrumbs",
                "header", "footer", "topbar",
                "newsletter", "subscribe", "subscription", "signup", "login", "search",
                "cookie", "cookies", "consent", "gdpr", "paywall",
                "modal", "popup", "tooltip", "byline", "pagination", "pager",
                "hidden", "offscreen", "screenreader", "sr"
        };
        for (String token : tokens) {
            mJunkClassTokens.add(token);
        }
    }

    /**
     * Extracts the sanitized article HTML from RAW page bytes. Charset
     * detection is delegated entirely to Jsoup, which honors the byte-order
     * mark and {@code <meta charset>} declarations - the correct handling for
     * legacy windows-1252 pages whose HTTP header (if any) disagrees with the
     * page.
     *
     * @param htmlBytes the raw response bytes, may be null or empty
     * @param baseUri   the page URL the relative links resolve against,
     *                  may be null (no absolutization is possible then)
     * @return the sanitized text-only article HTML fragment, or null when no
     * article body could be located
     */
    public final String extract(byte[] htmlBytes, String baseUri) {
        if (htmlBytes == null || htmlBytes.length == 0) {
            return null;
        }
        // Jsoup's facade has no byte[] overload; DataUtil.load with a NULL
        // charset is the exact path Jsoup.connect() uses - it runs BOM and
        // <meta charset> detection over the raw bytes before decoding
        Document document;
        try {
            document = DataUtil.load(
                    new ByteArrayInputStream(htmlBytes), null,
                    baseUri != null ? baseUri : "");
        } catch (IOException e) {
            // ByteArrayInputStream never really throws; guarded anyway
            return null;
        }
        return extract(document);
    }

    /**
     * Extracts the sanitized article HTML from a decoded HTML string. The
     * byte[] overload is preferred by callers (correct charset detection);
     * this variant exists for already-decoded input and the tests.
     *
     * @param html    the HTML source, may be null or blank
     * @param baseUri the page URL the relative links resolve against,
     *                may be null (no absolutization is possible then)
     * @return the sanitized text-only article HTML fragment, or null when no
     * article body could be located
     */
    public final String extract(String html, String baseUri) {
        if (html == null || html.trim().isEmpty()) {
            return null;
        }
        Document document = Jsoup.parse(html, baseUri != null ? baseUri : "");
        return extract(document);
    }

    /**
     * Runs the full pipeline on a parsed document (see the class javadoc).
     *
     * @param document the parsed page
     * @return the sanitized article HTML fragment, or null when nothing usable
     * was found
     */
    private String extract(Document document) {
        if (document.body() == null) {
            return null;
        }
        removeNoise(document);
        convertImagesToAltText(document);
        Element articleRoot = findArticleRoot(document);
        if (articleRoot == null) {
            return null;
        }
        isolateArticleRoot(document, articleRoot);
        Document cleanedDocument = new Cleaner(SAFELIST).clean(document);
        cleanedDocument.outputSettings().prettyPrint(false);
        String cleanedHtml = cleanedDocument.body().html().trim();
        return cleanedHtml.isEmpty() ? null : cleanedHtml;
    }

    /**
     * Drops structural noise tags, junk-classed/hidden elements and
     * escaped-embed-code text (see {@link #removeEmbedCodeNoise(Document)},
     * which must run AFTER the structural pass: a real {@code <iframe>} child
     * is gone by then, so any remaining iframe markup in a text node is the
     * copy-paste embed snippet, never a live embed).
     * Jsoup's {@code select} materializes its result list first, so removing
     * elements while iterating the selection is safe.
     *
     * @param document the parsed page, modified in place
     */
    private void removeNoise(Document document) {
        document.select(NOISE_TAG_SELECTOR).remove();
        for (Element element : document.select("[class], [id]")) {
            if (isJunkClassed(element)) {
                element.remove();
            }
        }
        for (Element element : document.select("[style]")) {
            String style = element.attr("style").toLowerCase(Locale.US)
                    .replace(" ", "");
            if (style.contains("display:none")
                    || style.contains("visibility:hidden")) {
                element.remove();
            }
        }
        removeEmbedCodeNoise(document);
    }

    /**
     * Escaped-embed-code noise rule (live NPR extraction bug): some sites ship
     * a "copy embed code" widget whose embed snippet is stored as ESCAPED TEXT,
     * e.g. {@code <b><code>&lt;iframe src="…" …&gt;</code></b>}. Real iframes
     * are already stripped by the safelist, but the snippet survives cleaning
     * as a text node (code/b are allowed, text nodes are always kept) and
     * {@code HtmlCompat.fromHtml} then renders the literal embed code as
     * visible article text. After the structural pass, iframe markup inside a
     * TEXT node can only be such a snippet, so matching elements are removed.
     * Two tiers keep legitimate programming prose alive:
     * <ul>
     * <li><b>Small inline/formatting containers and list rows</b> (code, pre,
     * b, strong, span, small, em, i, li) are dropped when their text CONTAINS
     * the markup anywhere - a li/code row is either a whole embed snippet or
     * carries one; the extra words around it ("Copy this code:") are widget
     * chrome, not article prose. Accepted loss: a programming tutorial's
     * code-block row SHOWING an iframe snippet is dropped too.</li>
     * <li><b>Paragraph-level blocks</b> (p, div) are dropped only when their
     * trimmed text STARTS WITH the markup, i.e. the paragraph IS the embed
     * snippet. Prose mentioning iframes mid-sentence - as words ("the iframe
     * element can embed content") or as a quoted snippet later in the
     * sentence - survives.</li>
     * </ul>
     * Matching is case-insensitive and checks BOTH the DOM text form
     * ({@code <iframe }, which the escaped source becomes after parsing) and
     * the still-escaped form ({@code &lt;iframe }, from double-escaped text).
     *
     * @param document the parsed page with structural noise already removed,
     *                 modified in place
     */
    private void removeEmbedCodeNoise(Document document) {
        for (Element element : document.select(EMBED_CODE_CONTAINS_TAG_SELECTOR)) {
            if (textContainsIframeMarkup(element.text())) {
                element.remove();
            }
        }
        for (Element element : document.select(EMBED_CODE_PREFIX_TAG_SELECTOR)) {
            if (textStartsWithIframeMarkup(element.text())) {
                element.remove();
            }
        }
    }

    /**
     * True when the given element text carries the iframe markup anywhere
     * (tier 1 of the embed-code noise rule).
     *
     * @param text the element's text, may be empty
     * @return true when it contains escaped or plain {@code <iframe} markup
     */
    private boolean textContainsIframeMarkup(String text) {
        if (text.length() < 8) {
            return false;
        }
        String lowerCaseText = text.toLowerCase(Locale.US);
        return lowerCaseText.contains("<iframe ")
                || lowerCaseText.contains("&lt;iframe ");
    }

    /**
     * True when the given element text BEGINS with the iframe markup (tier 2
     * of the embed-code noise rule). Cheap first-character guard before the
     * lowercased comparison, so ordinary prose blocks never allocate a
     * lowercased copy.
     *
     * @param text the element's text, may be empty
     * @return true when it starts with escaped or plain {@code <iframe} markup
     */
    private boolean textStartsWithIframeMarkup(String text) {
        String trimmedText = text.trim();
        if (trimmedText.isEmpty()) {
            return false;
        }
        char firstChar = trimmedText.charAt(0);
        if (firstChar != '<' && firstChar != '&') {
            return false;
        }
        String lowerCaseText = trimmedText.toLowerCase(Locale.US);
        return lowerCaseText.startsWith("<iframe ")
                || lowerCaseText.startsWith("&lt;iframe ");
    }

    /**
     * True when any word part of the element's class or id marks it as
     * boilerplate. Word-part matching: the attribute is lowercased and split
     * on non-letter characters, so camelCase ("CookieBanner"), kebab-case
     * ("cookie-banner") and snake_case ("cookie_banner") all yield the "cookie"
     * and "banner" parts, while words merely containing a junk substring
     * ("reader", "thread") never match.
     *
     * @param element the element to inspect
     * @return true when the element looks like boilerplate
     */
    private boolean isJunkClassed(Element element) {
        String classAndId = element.className() + " " + element.id();
        for (String word : classAndId.toLowerCase(Locale.US).split("[^a-z]+")) {
            if (!word.isEmpty() && mJunkClassTokens.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes every img, keeping meaningful alt text as a plain text node
     * (issue #96: inline images are not planned; text-only rendering is
     * deliberate). Alt text preserves the information of described charts and
     * photos; decorative images (no alt) are dropped outright.
     *
     * @param document the parsed page, modified in place
     */
    private void convertImagesToAltText(Document document) {
        for (Element img : document.select("img")) {
            String alt = img.attr("alt").trim();
            if (alt.isEmpty()) {
                img.remove();
            } else {
                img.replaceWith(new TextNode(" " + alt + " "));
            }
        }
    }

    /**
     * Scores candidate containers and returns the best article root, or the
     * body itself when no candidate clears the minimum text length but the
     * page as a whole carries enough text (small static pages). Returns null
     * when the page has no article body at all.
     *
     * @param document the parsed page with noise already removed
     * @return the article root element, the body element, or null
     */
    private Element findArticleRoot(Document document) {
        Element body = document.body();
        Element bestCandidate = null;
        double bestScore = 0;
        for (Element candidate : document.select("article, main, section, div, body")) {
            String text = candidate.text();
            if (text.length() < MIN_CANDIDATE_TEXT_LENGTH) {
                continue;
            }
            double linkDensity = computeLinkDensity(candidate);
            double paragraphScore = 0;
            int paragraphCount = 0;
            for (Element paragraph : candidate.select("p")) {
                int paragraphLength = paragraph.text().length();
                if (paragraphLength >= MIN_PARAGRAPH_TEXT_LENGTH) {
                    paragraphCount++;
                    paragraphScore += Math.min(paragraphLength, MAX_PARAGRAPH_SCORE);
                }
            }
            // without real paragraphs the raw text mass is the only signal left
            double score = paragraphCount > 0
                    ? paragraphScore
                    : Math.min(text.length(), MAX_PARAGRAPH_SCORE * 2);
            // a candidate that is mostly links is navigation, not content
            score *= (1.0 - linkDensity);
            String tagName = candidate.tagName();
            if ("article".equals(tagName)) {
                score *= TAG_BONUS_ARTICLE;
            } else if ("main".equals(tagName)) {
                score *= TAG_BONUS_MAIN;
            }
            String classAndId = (candidate.className() + " " + candidate.id())
                    .toLowerCase(Locale.US);
            if (classAndId.contains("article") || classAndId.contains("story")
                    || classAndId.contains("post") || classAndId.contains("entry")
                    || classAndId.contains("content")) {
                score *= CLASS_BONUS;
            }
            if (classAndId.contains("comment") || classAndId.contains("sidebar")
                    || classAndId.contains("footer") || classAndId.contains("widget")) {
                score *= CLASS_PENALTY;
            }
            // strict > keeps the FIRST (outermost) candidate on ties: the
            // result is stable for identical input
            if (score > bestScore) {
                bestScore = score;
                bestCandidate = candidate;
            }
        }
        if (bestCandidate != null) {
            return bestCandidate;
        }
        String bodyText = body.text().trim();
        if (bodyText.length() >= MIN_ARTICLE_TEXT_LENGTH) {
            return body;
        }
        return null;
    }

    /**
     * The share of a candidate's text that sits inside links. Navigation
     * containers score near 1.0 (everything is a link) and are effectively
     * eliminated by the (1 - density) score multiplier; article bodies sit
     * near 0.
     *
     * @param candidate the candidate element
     * @return link text length / total text length, clamped to [0, 1]
     */
    private double computeLinkDensity(Element candidate) {
        String text = candidate.text();
        int textLength = text.length();
        if (textLength == 0) {
            return 0;
        }
        int linkTextLength = 0;
        for (Element link : candidate.select("a")) {
            linkTextLength += link.text().length();
        }
        double density = (double) linkTextLength / textLength;
        if (density < 0) {
            return 0;
        }
        if (density > 1) {
            return 1;
        }
        return density;
    }

    /**
     * Moves the chosen article root under the body as its only child so the
     * cleaner processes exactly the article. {@code appendChild} MOVES the
     * node (Jsoup reparents), preserving each node's baseUri - that is what
     * keeps relative-link absolutization working for deep-rooted articles.
     * A root that already IS the body is left in place.
     *
     * @param document    the parsed page, modified in place
     * @param articleRoot the selected root element
     */
    private void isolateArticleRoot(Document document, Element articleRoot) {
        Element body = document.body();
        if (articleRoot == body) {
            return;
        }
        body.empty();
        body.appendChild(articleRoot);
    }

    @Override
    public String toString() {
        return "ArticleExtractor";
    }

    /**
     * Convenience for logging: the first N characters of the sanitized output.
     * Not used by the extraction pipeline itself.
     *
     * @param cleanedHtml the extraction result, may be null
     * @param maxLength   the maximum preview length
     * @return a single-line preview of the result
     */
    public final String preview(String cleanedHtml, int maxLength) {
        if (cleanedHtml == null) {
            return "";
        }
        String flat = cleanedHtml.replaceAll("\\s+", " ").trim();
        if (flat.length() <= maxLength) {
            return flat;
        }
        return flat.substring(0, maxLength);
    }

    /**
     * The tags the sanitizer keeps, exposed for tests and logging.
     *
     * @return an immutable list of allowed tag names
     */
    public final List<String> allowedTags() {
        ArrayList<String> tags = new ArrayList<>();
        tags.add("p");
        tags.add("br");
        tags.add("hr");
        tags.add("h1");
        tags.add("h2");
        tags.add("h3");
        tags.add("h4");
        tags.add("h5");
        tags.add("h6");
        tags.add("ul");
        tags.add("ol");
        tags.add("li");
        tags.add("blockquote");
        tags.add("pre");
        tags.add("code");
        tags.add("strong");
        tags.add("b");
        tags.add("em");
        tags.add("i");
        tags.add("u");
        tags.add("s");
        tags.add("sup");
        tags.add("sub");
        tags.add("span");
        tags.add("a");
        return tags;
    }
}
