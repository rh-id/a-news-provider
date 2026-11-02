package m.co.rh.id.a_news_provider.base.util;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link RssTitleNormalizer} (issue #45 phase 2): the normalize
 * pipeline (null/blank to "", Locale.ROOT lowercasing incl. the Turkish-İ
 * combining-mark artifact, boilerplate suffix stripping with its three guards,
 * punctuation/whitespace collapse) and the token-set Jaccard similarity.
 */
public class RssTitleNormalizerTest {

    private final RssTitleNormalizer normalizer = new RssTitleNormalizer();

    // ==================================================================================
    // normalize: null/blank handling
    // ==================================================================================

    @Test
    public void normalizeNullReturnsEmptyString() {
        assertEquals("", normalizer.normalize(null));
    }

    @Test
    public void normalizeEmptyReturnsEmptyString() {
        assertEquals("", normalizer.normalize(""));
    }

    @Test
    public void normalizeBlankReturnsEmptyString() {
        assertEquals("", normalizer.normalize("   \t\n "));
    }

    // ==================================================================================
    // normalize: casing (Locale.ROOT) and the Turkish İ
    // ==================================================================================

    @Test
    public void normalizeLowercasesWithRootLocale() {
        // ROOT (never the default locale): a device set to Turkish must not map
        // the ASCII I to dotless ı, or every uppercase English headline would
        // split into a different cluster
        assertEquals("istanbul", normalizer.normalize("ISTANBUL"));
        assertEquals("istanbul", normalizer.normalize("istanbul"));
    }

    @Test
    public void normalizeUnifiesTurkishDottedCapitalI() {
        // the ROOT full case mapping of İ (U+0130) is "i" + U+0307 combining dot
        // above; the combining mark is deleted during punctuation removal, so the
        // Turkish-spelled and plain spellings normalize to the SAME key
        assertEquals(normalizer.normalize("istanbul"),
                normalizer.normalize("İSTANBUL"));
    }

    // ==================================================================================
    // normalize: punctuation and whitespace collapse
    // ==================================================================================

    @Test
    public void normalizeCollapsesPunctuationAndWhitespaceRuns() {
        assertEquals("hello world", normalizer.normalize("Hello,   World!!"));
        assertEquals("hello world", normalizer.normalize("  Hello,\t World!!  "));
        // dash variants without space padding are never boilerplate-stripped,
        // just punctuated away
        assertEquals("state of the union 2026 edition",
                normalizer.normalize("State of the Union — 2026 Edition"));
    }

    @Test
    public void normalizeIsIdempotent() {
        String once = normalizer.normalize("Big Rally!  Markets Soar – Reuters");
        assertEquals(once, normalizer.normalize(once));
    }

    // ==================================================================================
    // normalize: boilerplate suffix stripping
    // ==================================================================================

    @Test
    public void normalizeStripsTrailingSourceSuffix() {
        // accept case with a >= 4-token head: the attribution suffix disappears,
        // so the wire copy and the bare headline share one cluster key
        String bare = normalizer.normalize("Stock Markets Rally as Rate Cut Hopes Rise");
        String attributed = normalizer.normalize(
                "Stock Markets Rally as Rate Cut Hopes Rise - Reuters");
        assertEquals(bare, attributed);
        assertEquals(bare, normalizer.normalize(
                "Stock Markets Rally as Rate Cut Hopes Rise | Analysis"));
        // en-dash separator spelling
        assertEquals(bare, normalizer.normalize(
                "Stock Markets Rally as Rate Cut Hopes Rise – Live"));
    }

    @Test
    public void normalizeRejectsPartLikeSuffix() {
        // "Part 2" is series content, not a source: the digit cancels the strip,
        // so different installments of a series can never merge
        String part2 = normalizer.normalize("Interview with Jane Doe - Part 2");
        String part1 = normalizer.normalize("Interview with Jane Doe - Part 1");
        assertEquals("interview with jane doe part 2", part2);
        assertNotEquals(part1, part2);
        // the part WORD alone cancels too (no digit variant)
        assertEquals("interview with jane doe episode",
                normalizer.normalize("Interview with Jane Doe - Episode"));
    }

    @Test
    public void normalizeRejectsShortHead() {
        // head < 4 tokens: stripping would erase the headline's substance
        assertEquals("fed cuts x", normalizer.normalize("Fed cuts - X"));
        // even a plausible-looking attribution stays when the head is too short
        assertNotEquals(normalizer.normalize("Big Market Rally"),
                normalizer.normalize("Big Market Rally - Reuters"));
    }

    @Test
    public void normalizeRejectsLongSuffixSegment() {
        // a trailing segment of more than 3 tokens is content, not attribution
        String full = normalizer.normalize(
                "Central Bank Warns of Slowdown - Global Economic Outlook Report");
        assertEquals("central bank warns of slowdown global economic outlook report",
                full);
    }

    @Test
    public void normalizeStripsOnlyTheRightmostSuffixSegment() {
        // "Headline | Analysis - Reuters": the rightmost separator splits, so the
        // strip target is "- Reuters"; "| Analysis" survives (it is 2 tokens,
        // within the segment budget)
        String stripped = normalizer.normalize(
                "Markets Slide as Traders Flee to Safety | Analysis - Reuters");
        assertEquals("markets slide as traders flee to safety analysis", stripped);
    }

    @Test
    public void normalizeKeepsSuffixWhenHeadWouldBeEmpty() {
        // a string that is only a suffix has no head - nothing to strip
        assertEquals("reuters", normalizer.normalize(" - Reuters"));
    }

    // ==================================================================================
    // similarity: token-set Jaccard
    // ==================================================================================

    @Test
    public void similarityOfIdenticalTitlesIsOne() {
        assertEquals(1.0, normalizer.similarity(
                "Fed cuts rates sharply amid inflation fears",
                "Fed cuts rates sharply amid inflation fears"), 1e-9);
    }

    @Test
    public void similarityOfDisjointTitlesIsZero() {
        assertEquals(0.0, normalizer.similarity(
                "Earthquake strikes coastal town",
                "President resigns after scandal"), 1e-9);
    }

    @Test
    public void similarityOfWireStoryCopiesIsAtOrAboveThreshold() {
        // one dropped trailing word: 6 of 7 tokens shared -> 6/7 >= 0.8
        double similarity = normalizer.similarity(
                "Fed cuts rates sharply amid inflation fears",
                "Fed cuts rates sharply amid inflation");
        assertTrue("wire copy pair must clear the threshold, was " + similarity,
                similarity >= RssTitleNormalizer.JACCARD_THRESHOLD);
        // symmetric
        assertEquals(similarity, normalizer.similarity(
                "Fed cuts rates sharply amid inflation",
                "Fed cuts rates sharply amid inflation fears"), 1e-9);
        // and the suffix-stripped attribution variant matches exactly
        assertEquals(1.0, normalizer.similarity(
                "Fed cuts rates sharply amid inflation fears",
                "Fed cuts rates sharply amid inflation fears - Reuters"), 1e-9);
    }

    @Test
    public void similarityOfDistinctStoriesIsBelowThreshold() {
        double similarity = normalizer.similarity(
                "Central bank cuts rates by half point",
                "Central bank raises rates by quarter point");
        assertTrue("topic-sharing but distinct stories must stay below, was "
                + similarity, similarity < RssTitleNormalizer.JACCARD_THRESHOLD);
    }

    @Test
    public void similarityWithEmptyInputIsZero() {
        assertEquals(0.0, normalizer.similarity(null, "any title here"), 1e-9);
        assertEquals(0.0, normalizer.similarity("any title here", ""), 1e-9);
        assertEquals(0.0, normalizer.similarity("", "   "), 1e-9);
    }

    @Test
    public void similarityCountsTokenSetsNotMultisets() {
        // repeated words collapse: {a, b, c} vs {a, a, b, b, c} are the SAME set
        assertEquals(1.0, normalizer.similarity("a b c", "a a b b c"), 1e-9);
    }

    @Test
    public void tokenSetOfNormalizedSplitsIntoDistinctTokens() {
        Set<String> tokens = normalizer.tokenSetOfNormalized("fed cuts rates sharply");
        assertEquals(4, tokens.size());
        assertTrue(tokens.contains("fed"));
        assertTrue(tokens.contains("rates"));
        assertEquals(0, normalizer.tokenSetOfNormalized("").size());
    }
}
