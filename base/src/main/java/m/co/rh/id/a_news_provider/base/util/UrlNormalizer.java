package m.co.rh.id.a_news_provider.base.util;

import java.util.Locale;

/**
 * Normalizes feed URLs so that trivially different spellings of the same URL
 * (extra trailing slashes, host/scheme casing, surrounding whitespace) compare equal.
 * Pure Java - no android dependencies so it is unit-testable and reusable
 * from background workers and Room migrations.
 * Registered in the Provider; can gain provider-injected dependencies later if needed.
 */
public class UrlNormalizer {

    private static final String SCHEME_DELIMITER = "://";

    public UrlNormalizer() {
    }

    /**
     * Normalizes the given URL: trims surrounding whitespace, lowercases the scheme
     * and host portions (everything up to the first '/', '?' or '#' after "://")
     * while keeping the path/query/fragment case intact, and strips trailing '/'
     * characters without ever eating the "//" that follows the scheme.
     * Null-safe - returns the input unchanged when null. If the input has no
     * "://" delimiter, only trim and trailing-slash stripping are applied conservatively.
     *
     * @param url the URL to normalize
     * @return the normalized URL, or null when the input is null
     */
    public String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.trim();
        int schemeIndex = trimmed.indexOf(SCHEME_DELIMITER);
        if (schemeIndex <= 0) {
            // No scheme delimiter - conservatively trim and strip trailing slashes only
            return stripTrailingSlashes(trimmed, 0);
        }
        // Lowercase only the scheme+host portion - everything before the first
        // '/', '?' or '#' after "://" - so query/fragment case stays intact
        int hostEnd = -1;
        for (int i = schemeIndex + SCHEME_DELIMITER.length(); i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                hostEnd = i;
                break;
            }
        }
        int splitAt = hostEnd < 0 ? trimmed.length() : hostEnd;
        String normalized = trimmed.substring(0, splitAt).toLowerCase(Locale.US)
                + trimmed.substring(splitAt);
        // Never strip past the "//" that follows the scheme (e.g. "https://" stays intact)
        return stripTrailingSlashes(normalized, schemeIndex + SCHEME_DELIMITER.length());
    }

    private String stripTrailingSlashes(String url, int minEnd) {
        int end = url.length();
        while (end > minEnd && url.charAt(end - 1) == '/') {
            end--;
        }
        return url.substring(0, end);
    }
}
