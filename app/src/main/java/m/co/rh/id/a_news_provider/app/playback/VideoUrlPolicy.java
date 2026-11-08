package m.co.rh.id.a_news_provider.app.playback;

import java.util.Locale;

/**
 * Pure-java decision policy for INLINE video playback (issue #69): decides
 * whether an item's {@code mediaVideo} enclosure URL is probably a directly
 * playable video file ({@code PlayerView} can render it) or a web page /
 * stream hub (YouTube, Vimeo, ... - those stay on the open-in-browser FAB).
 * Stateless instance class registered in the application provider (same
 * pattern as the base module's {@code UrlSanitizer}), tested by instrumented
 * tests in androidTest.
 * <p>
 * Decision order (order is load-bearing):
 * <ol>
 *     <li>null / blank URL - false (nothing to render inline).</li>
 *     <li>Host is a streaming-platform host - false: these URLs open video
 *     HUB pages, not files; the player would show an error or a blank frame.
 *     Hosts are matched case-insensitively, equal-to or subdomain-of
 *     (suffix-match like UrlSanitizer, so {@code m.youtube.com} also matches
 *     the bare {@code youtube.com} entry it is explicitly listed beside).</li>
 *     <li>Path ends with a page-file extension (.swf/.html/.htm/.php) -
 *     false: a web page, however video-like the URL looks.</li>
 *     <li>Path ends with a known video-file extension (.mp4/.m4v/.webm/
 *     .3gpp/.mkv/.mov/.ts) - true.</li>
 *     <li>otherwise TRUE: the runtime player covers mis-detections with its
 *     own error path ({@code video_playback_failed} toast + revert to the
 *     browser FAB), so an unknown-but-direct enclosure inline-plays and a
 *     hub URL without a known host fails gracefully - favoring inline
 *     playback here keeps the common enclosure case working.</li>
 * </ol>
 */
public final class VideoUrlPolicy {

    private static final String SCHEME_DELIMITER = "://";

    /**
     * Streaming-platform hosts whose enclosure-style URLs are HUB pages, not
     * files (matched equal-to or subdomain-of).
     */
    private static final String[] PAGE_HOSTS = {
            "youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be",
            "vimeo.com", "dailymotion.com", "bilibili.com", "twitch.tv"};

    /**
     * Path extensions that name web pages rather than video files.
     */
    private static final String[] PAGE_EXTENSIONS = {".swf", ".html", ".htm", ".php"};

    /**
     * Path extensions that name directly playable video files.
     */
    private static final String[] DIRECT_VIDEO_EXTENSIONS = {
            ".mp4", ".m4v", ".webm", ".3gpp", ".mkv", ".mov", ".ts"};

    /**
     * Decides whether the given media video URL is probably a directly
     * playable video file.
     *
     * @param url the enclosure URL to inspect, may be null or empty
     * @return true when the URL should render in the inline player
     */
    public boolean isProbablyDirectVideoUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        if (hostMatches(extractHost(url))) {
            return false;
        }
        String lowerPath = extractPath(url).toLowerCase(Locale.US);
        for (String extension : PAGE_EXTENSIONS) {
            if (lowerPath.endsWith(extension)) {
                return false;
            }
        }
        for (String extension : DIRECT_VIDEO_EXTENSIONS) {
            if (lowerPath.endsWith(extension)) {
                return true;
            }
        }
        // unknown path: assume a direct file - the player's error path covers
        // mis-detections (see class doc, decision 5)
        return true;
    }

    /**
     * True when the host equals one of {@link #PAGE_HOSTS} or is a subdomain
     * of one (e.g. {@code m.youtube.com} matches {@code youtube.com}), always
     * compared lowercased.
     *
     * @param host lowercased host without userinfo/port, possibly empty
     * @return true when the host names a streaming-platform page
     */
    private static boolean hostMatches(String host) {
        for (String domain : PAGE_HOSTS) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the lowercased host from the URL: the authority run after
     * "://" up to the first '/', '?' or '#', with userinfo (up to the last
     * '@') and the ':port' suffix stripped (same extraction approach as the
     * base UrlSanitizer, minus IPv6 bracket handling, which enclosure URLs
     * never use). Scheme-less strings have no host.
     *
     * @param url the URL to inspect, non-null
     * @return the lowercased host, or the empty string when none exists
     */
    private static String extractHost(String url) {
        int schemeIndex = url.indexOf(SCHEME_DELIMITER);
        if (schemeIndex < 0) {
            // no scheme: nothing that looks like an authority
            return "";
        }
        int schemeEnd = schemeIndex + SCHEME_DELIMITER.length();
        int authorityEnd = url.length();
        for (int i = schemeEnd; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        String authority = url.substring(schemeEnd, authorityEnd);
        int atIndex = authority.lastIndexOf('@');
        if (atIndex >= 0) {
            authority = authority.substring(atIndex + 1);
        }
        int colonIndex = authority.indexOf(':');
        String host = colonIndex < 0 ? authority : authority.substring(0, colonIndex);
        return host.toLowerCase(Locale.US);
    }

    /**
     * Extracts the URL path: the run after the host (the first '/', '?' or
     * '#' following the authority) up to the query ('?') or fragment ('#').
     * A URL with no path after the authority yields the empty string.
     *
     * @param url the URL to inspect, non-null
     * @return the path (leading '/' included when present), possibly empty
     */
    private static String extractPath(String url) {
        int schemeIndex = url.indexOf(SCHEME_DELIMITER);
        int searchStart = schemeIndex < 0 ? 0 : schemeIndex + SCHEME_DELIMITER.length();
        int pathStart = -1;
        int pathEnd = url.length();
        for (int i = searchStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '?' || c == '#') {
                pathEnd = i;
                break;
            }
            if (c == '/' && pathStart < 0) {
                pathStart = i;
            }
        }
        if (pathStart < 0 || pathStart >= pathEnd) {
            return "";
        }
        return url.substring(pathStart, pathEnd);
    }
}
