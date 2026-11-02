package m.co.rh.id.a_news_provider.base.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalizes feed item titles so that near-duplicate headlines of the same story
 * (wire copies, syndications with source suffixes, trivial punctuation/casing
 * differences) compare equal or similar, backing the cross-feed duplicate story
 * collapse (issue #45 phase 2). The normalized form doubles as the fallback
 * dedup cluster key assigned by RssRepository at sync time; at query time rows
 * whose key ended up empty are neutralized by {@code NULLIF(dedup_key, '')} and
 * fall back to exact-link grouping, so an empty normalization result is safe.
 * Pure Java - no android dependencies so it is unit-testable and reusable
 * from background workers. Registered in the Provider; can gain provider-injected
 * dependencies later if needed (same precedent as {@link UrlNormalizer}).
 */
public class RssTitleNormalizer {

    /**
     * Token-set Jaccard similarity at or above which two normalized titles are
     * considered the same story (issue #45 phase 2). 0.8 is intentionally strict:
     * wire copies of one story usually drop or add a single trailing word, which
     * keeps Jaccard at or above ~0.85 for typical 6-10 token headlines, while
     * genuinely different stories sharing a topic stay far below.
     */
    public static final double JACCARD_THRESHOLD = 0.8;

    /**
     * Minimum token count a normalized title must have before fuzzy (similarity
     * based) matching is attempted at all. Titles shorter than this match only by
     * exact normalized equality (the in-batch title map / the key fallback): short
     * generic headlines like "Markets surge" or "Breaking news" are structurally
     * close to too many unrelated stories, so fuzzy matching them would mass-merge
     * distinct articles into one cluster.
     */
    public static final int MIN_TOKENS_FOR_FUZZY = 4;

    /**
     * Recency window (in days) for the fuzzy candidate pass: only rss_item rows
     * whose {@code updated_date_time} is at or after {@code now - 7 days} are
     * fetched as fuzzy candidates by RssDao.findRecentRssItemsForDedupMatching.
     * The window bounds COMPARISON COST only, not correctness of the collapse: any
     * feed still carrying an item re-stamps its updated_date_time on every sync
     * (see RssDao.upsertRssItemsForChannel), so a live story's copies are always
     * inside the window; meanwhile the union-by-link step (the repository's first
     * matching rule) is deliberately NOT bounded by this window, keeping the
     * phase-1 same-link invariant recency-independent.
     */
    public static final int DEDUP_WINDOW_DAYS = 7;

    /**
     * Separators that introduce a trailing boilerplate source/date segment in a
     * headline ("Headline - Reuters", "Headline – Live", "Headline | Analysis").
     * Only these three spellings are stripped: they are the dominant
     * space-padded patterns in RSS titles; compact variants ("Headline-Reuters")
     * are ambiguous with hyphenated words and stay untouched.
     */
    private static final String[] BOILERPLATE_SEPARATORS = {" - ", " – ", " | "};

    /**
     * Matches a trailing segment that names a PART of a series rather than a
     * source: part/pt/ep/episode/chapter/vol/disc/track/season. Such segments are
     * load-bearing content - "Interview with Jane Doe - Part 2" must NOT merge
     * with "Part 1" of the same series - so their presence cancels the strip.
     * Case-insensitive because callers may run it before or after lowercasing.
     */
    private static final Pattern PART_OR_EPISODE_LIKE =
            Pattern.compile(".*(part|pt|ep|episode|chapter|vol|disc|track|season).*",
                    Pattern.CASE_INSENSITIVE);

    public RssTitleNormalizer() {
    }

    /**
     * Normalizes the given feed item title for duplicate comparison, in order:
     * <ol>
     * <li>null/blank input yields "" - the caller (RssRepository) stores it as-is
     * and the query-time {@code NULLIF(dedup_key, '')} neutralizes it, so such
     * rows fall back to link/sentinel grouping instead of all collapsing into one
     * empty-key group;</li>
     * <li>trim, then lowercase with {@link Locale#ROOT}: ROOT (not the default
     * locale) so the mapping never depends on device settings - the Turkish-İ
     * problem, same precedent as the categories feature (issue #95): under the
     * tr locale a plain ASCII "I" would lowercase to dotless "ı" and split every
     * uppercase English headline into a different cluster;</li>
     * <li>BEFORE punctuation removal, strip one trailing boilerplate segment:
     * when the string ends with one of the space-padded separators (" - ",
     * " – ", " | ") followed by a trailing segment, that segment is removed so
     * "Big Market Rally - Reuters" normalizes to the same string as "Big Market
     * Rally". The strip is guarded, ALL conditions must hold: (a) the trailing
     * segment has at most 3 whitespace tokens - a suffix that long is content, not
     * attribution; (b) the remaining head has at least 4 whitespace tokens -
     * "Fed cuts - X" stays whole because the head "fed cuts" alone is too
     * little to remain a meaningful cluster key and the strip would erase real
     * information; (c) the segment is NOT part/episode-like (no digit anywhere,
     * no part/pt/ep/episode/chapter/vol/disc/track/season word) - "Interview
     * with Jane Doe - Part 2" keeps its suffix so it can never merge with
     * "Part 1". Only ONE segment is stripped: chained attributions are rare and
     * a repeated strip risks eating real tail content;</li>
     * <li>map every character that is not {@link Character#isLetterOrDigit(char)}
     * to a space and collapse whitespace runs to a single space, then trim - this
     * makes punctuation, quotes and dash variants irrelevant; combining marks are
     * DELETED rather than spaced: Locale.ROOT lowercasing of the Turkish dotted
     * capital İ (U+0130) yields "i" + U+0307 COMBINING DOT ABOVE (the Unicode
     * full case mapping), and deleting the mark re-unifies "İSTANBUL" with
     * "istanbul" instead of splitting it into the two tokens "i stanbul".</li>
     * </ol>
     *
     * @param title the raw feed item title, may be null or blank
     * @return the normalized title, "" for null/blank input, never null
     */
    public String normalize(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "";
        }
        String lowercased = title.trim().toLowerCase(Locale.ROOT);
        String stripped = stripBoilerplateSuffix(lowercased);
        StringBuilder builder = new StringBuilder(stripped.length());
        for (int i = 0; i < stripped.length(); i++) {
            char currentChar = stripped.charAt(i);
            if (isCombiningMark(currentChar)) {
                // artifact of the ROOT lowercase full mapping (Turkish İ) or of
                // accented input - deleting keeps the base letter's cluster
                continue;
            }
            if (Character.isLetterOrDigit(currentChar)) {
                builder.append(currentChar);
            } else {
                builder.append(' ');
            }
        }
        return builder.toString().replaceAll("\\s+", " ").trim();
    }

    /**
     * Computes the token-set Jaccard similarity of the two given RAW titles:
     * both are run through {@link #normalize(String)}, split into token SETS
     * (duplicates within one title count once), and the result is
     * intersection-size / union-size. Returns 0 when either token set is empty
     * (nothing in common can be proven from an empty side); two identical
     * normalized titles yield exactly 1.0. Callers compare against
     * {@link #JACCARD_THRESHOLD}.
     *
     * @param titleA the first raw title, may be null/blank
     * @param titleB the second raw title, may be null/blank
     * @return the token-set Jaccard similarity in [0.0, 1.0]
     */
    public double similarity(String titleA, String titleB) {
        return similarity(tokenSet(titleA), tokenSet(titleB));
    }

    /**
     * Computes the token-set Jaccard similarity of two PRE-BUILT token sets -
     * the overload behind {@link #similarity(String, String)}, exposed so the
     * sync-time matcher (RssRepository.assignDedupKeys) can memoize each
     * candidate's token set once and never re-tokenize per compared item.
     * Semantics are identical: set intersection / set union, 0 when either set
     * is empty, 1.0 when the sets are equal.
     *
     * @param tokensA the first title's token set, never null
     * @param tokensB the second title's token set, never null
     * @return the token-set Jaccard similarity in [0.0, 1.0]
     */
    public double similarity(Set<String> tokensA, Set<String> tokensB) {
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0;
        }
        if (tokensA.equals(tokensB)) {
            return 1.0;
        }
        int intersectionSize = 0;
        for (String token : tokensA) {
            if (tokensB.contains(token)) {
                intersectionSize++;
            }
        }
        int unionSize = tokensA.size() + tokensB.size() - intersectionSize;
        return (double) intersectionSize / (double) unionSize;
    }

    /**
     * Tokenizes the given RAW title into the SET of tokens of its
     * {@link #normalize(String)}d form. Companion of
     * {@link #similarity(Set, Set)} for callers that memoize token sets (the
     * sync-time matcher): the returned set must be treated as immutable. A
     * null/blank title yields the empty set.
     *
     * @param title the raw title to tokenize, may be null or blank
     * @return the normalized title's distinct tokens, empty for null/blank input
     */
    public Set<String> tokenSet(String title) {
        return tokenSetOfNormalized(normalize(title));
    }

    /**
     * Tokenizes an ALREADY-NORMALIZED title (the exact output of
     * {@link #normalize(String)}), skipping the re-normalization pass. Companion
     * of {@link #similarity(Set, Set)} for the sync-time matcher, which holds
     * both the normalized form and the token set of every candidate memoized
     * once. Passing a not-yet-normalized string is SAFE but wrong-footed:
     * normalization is idempotent, so only punctuation/casing sensitivity is
     * lost. Whitespace-splitting is exact because normalize collapses every
     * whitespace run to a single separator; an empty input yields the empty set.
     *
     * @param normalizedTitle the normalized title to split, may be empty
     * @return the title's distinct tokens, empty for an empty input
     */
    public Set<String> tokenSetOfNormalized(String normalizedTitle) {
        if (normalizedTitle == null || normalizedTitle.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(Arrays.asList(normalizedTitle.split(" ")));
    }

    /**
     * Strips one trailing boilerplate segment (" - Reuters" etc.) from the given
     * LOWERCASED title when all three guards of {@link #normalize(String)}
     * hold (segment length, head length, not part/episode-like); otherwise
     * returns the input unchanged. When several separators occur, the LAST
     * (rightmost) one splits - "Headline | Analysis - Reuters" strips the
     * "- Reuters" tail. An empty trailing segment (the string merely ends with a
     * dangling separator) passes the guards trivially and is trimmed away.
     *
     * @param lowercasedTitle the trimmed, lowercased title, never null
     * @return the title without its boilerplate suffix, or the input unchanged
     */
    private String stripBoilerplateSuffix(String lowercasedTitle) {
        int lastSeparatorIndex = -1;
        int lastSeparatorLength = 0;
        for (String separator : BOILERPLATE_SEPARATORS) {
            int index = lowercasedTitle.lastIndexOf(separator);
            if (index > lastSeparatorIndex) {
                lastSeparatorIndex = index;
                lastSeparatorLength = separator.length();
            }
        }
        // the separator must leave a (possibly empty) trailing segment behind,
        // and the head must not be empty either - " - Reuters" alone has no head
        if (lastSeparatorIndex <= 0
                || lastSeparatorIndex + lastSeparatorLength >= lowercasedTitle.length()) {
            return lowercasedTitle;
        }
        String head = lowercasedTitle.substring(0, lastSeparatorIndex);
        String segment = lowercasedTitle.substring(lastSeparatorIndex + lastSeparatorLength);
        // guard (a): a segment of more than 3 tokens is content, not attribution
        if (countWhitespaceTokens(segment) > 3) {
            return lowercasedTitle;
        }
        // guard (b): the remaining head must carry the headline on its own
        if (countWhitespaceTokens(head) < 4) {
            return lowercasedTitle;
        }
        // guard (c): "Part 2"/"Episode 5"-style suffixes are series content and
        // must survive so different parts never merge into one cluster
        if (isPartOrEpisodeLike(segment)) {
            return lowercasedTitle;
        }
        return head;
    }

    /**
     * Counts the whitespace-separated tokens of the given string; null/blank
     * counts as 0 tokens.
     *
     * @param value the string to count, may be null or blank
     * @return the number of whitespace-separated tokens
     */
    private int countWhitespaceTokens(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }
        return value.trim().split("\\s+").length;
    }

    /**
     * True when the given trailing segment names a part of a series instead of a
     * source: it contains any digit ("Part 2" loses only because of the digit -
     * the word "part" alone is checked by the pattern below), or it contains a
     * part/episode word (part, pt, ep, episode, chapter, vol, disc, track,
     * season) case-insensitively. Such segments are load-bearing: stripping them
     * would merge distinct installments of a series into one cluster.
     *
     * @param segment the trailing boilerplate candidate segment, never null
     * @return true when the segment must NOT be stripped
     */
    private boolean isPartOrEpisodeLike(String segment) {
        for (int i = 0; i < segment.length(); i++) {
            if (Character.isDigit(segment.charAt(i))) {
                return true;
            }
        }
        return PART_OR_EPISODE_LIKE.matcher(segment).matches();
    }

    /**
     * True when the given character is a Unicode combining mark (non-spacing,
     * combining-spacing or enclosing mark). Combining marks are deleted during
     * normalization: Locale.ROOT lowercasing of the Turkish dotted capital İ
     * (U+0130) emits "i" + U+0307 COMBINING DOT ABOVE (the unconditional full
     * case mapping), and spacing that mark would split the word into two tokens
     * ("i stanbul"), defeating the very unification the ROOT-locale choice is
     * meant to guarantee.
     *
     * @param character the character to test
     * @return true when the character is a combining mark
     */
    private boolean isCombiningMark(char character) {
        int type = Character.getType(character);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
