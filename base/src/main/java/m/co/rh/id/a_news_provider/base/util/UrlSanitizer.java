package m.co.rh.id.a_news_provider.base.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Strips tracking query parameters from links at DISPLAY time (issue #76):
 * {@link #sanitize(String)} removes utm_* / fbclid-style click identifiers from an
 * item link's query before the link is opened or copied, so the destination sees
 * a clean URL and the user does not leak marketing attribution on every share.
 * <p>
 * INVARIANT (issue #76): this class is a pure string-to-string function and the
 * app treats its result as a LOCAL copy only - {@code RssItem.link} is the
 * cross-channel state key (read/favorite/dedup all query by it) and must never
 * be written back with a sanitized value. Callers sanitize at the UI boundary.
 * <p>
 * Implementation constraints, all deliberate:
 * <ul>
 * <li>Pure Java - no android.* imports, so it runs on JVM unit tests and is
 * reusable from background workers (same pattern as {@link UrlNormalizer}).</li>
 * <li>Manual string parsing on the ENCODED query text: the raw {@code &} tokens
 * are split and kept tokens are kept byte-for-byte. The query is NEVER
 * decoded-then-re-encoded (an android.net.Uri round-trip corrupts kept params:
 * {@code +} becomes {@code %20} and escape casing changes), so token order,
 * encoding and the fragment are preserved exactly.</li>
 * <li>Parameter-name matching is case-insensitive (lowercased before compare) -
 * trackers ship mixed-case spellings (Firefox strips both {@code hsctatracking}
 * and {@code hsCtaTracking}; Miniflux lowercases too).</li>
 * <li>Every occurrence of a matching parameter is stripped, including valueless
 * tokens ({@code ?fbclid} without {@code =}); a token whose empty name is
 * followed by {@code =} ({@code =value}) is KEPT because it names nothing.</li>
 * <li>Only http/https URLs are processed (scheme check is case-insensitive);
 * mailto:, market:, relative refs etc. are returned unchanged.</li>
 * </ul>
 * <p>
 * Rule sources: the prefix and global-exact lists are the union of Brave's
 * classic global query-parameter blocklist (github.com/brave/adblock-lists),
 * Firefox's query parameter stripping (QPS) Remote Settings list and Miniflux's
 * urlcleaner.go, extended with the HubSpot hsa_* click-id family and TikTok's
 * ttclid / Google Merchant's srsltid. Host-scoped entries mirror Brave's
 * site-specific tier (YouTube/Spotify {@code si}, X/Twitter
 * {@code ref_src}/{@code ref_url}).
 * <p>
 * Explicit NON-GOALS (issue #76): generic functional-looking parameters such as
 * {@code ref}, {@code ref_}, {@code source}, {@code s}, {@code t},
 * {@code feature}, {@code ocid}, {@code spm}, {@code ved} and eBay/Amazon
 * specific parameters are deliberately NOT stripped globally - Firefox scopes
 * them per host and sites routinely require them to render or route correctly;
 * a future host-scoped tier could revisit individual ones.
 */
public class UrlSanitizer {

    private static final String SCHEME_DELIMITER = "://";
    private static final String QUERY_START = "?";
    private static final String FRAGMENT_START = "#";
    private static final String PARAM_SEPARATOR = "&";

    /**
     * Prefix rules (issue #76): any parameter whose (lowercased) name starts with
     * one of these prefixes is a tracking parameter regardless of its suffix.
     * utm_* is Google Analytics' campaign tagging, mtm_* is Matomo's clone of it.
     */
    private static final String[] TRACKING_NAME_PREFIXES = {"utm_", "mtm_"};

    /**
     * Global exact rules (issue #76): parameter names (matched lowercased) that
     * are click/ad identifiers everywhere and are stripped from any host. Union
     * of Brave's classic global list, Firefox QPS Remote Settings and Miniflux
     * urlcleaner.go, plus the HubSpot hsa_* family and ttclid/srsltid.
     */
    private static final Set<String> GLOBAL_TRACKERS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "msclkid",
            "twclid", "ttclid", "yclid", "ysclid", "_openstat", "wickedid",
            "rb_clickid", "s_cid", "ml_subscriber", "ml_subscriber_hash",
            "srsltid", "mc_cid", "mc_eid", "mc_tc", "vero_id", "vero_conv",
            "oly_anon_id", "oly_enc_id", "mkt_tok", "_hsenc", "__hssc",
            "__hstc", "__hsfp", "_hsmi", "hsctatracking", "s_kwcid", "sc_cid",
            "_bhlid", "_branch_match_id", "_branch_referrer", "oft_id", "oft_k",
            "oft_lk", "oft_d", "oft_c", "oft_ck", "oft_ids", "oft_sk",
            "hmb_campaign", "hmb_medium", "hmb_source", "itm_campaign",
            "itm_medium", "itm_source", "__readwiselocation", "fb_action_ids",
            "fb_action_types", "fb_ref", "fb_source", "fb_comment_id",
            "campaign_id", "campaign_medium", "campaign_name",
            "campaign_source", "campaign_term", "campaign_content", "igshid",
            "__s", "hsa_cam", "hsa_grp", "hsa_src", "hsa_ad", "hsa_acc",
            "hsa_kw", "hsa_tgt", "hsa_ver", "hsa_la", "hsa_ol", "hsa_net",
            "hsa_mt")));

    /**
     * Host-scoped rules (issue #76): parameter names that are only trackers on
     * specific hosts - {@code si} is a share/link identifier on YouTube and
     * Spotify, {@code ref_src}/{@code ref_url} are referral echo parameters on
     * X/Twitter. On other hosts these names can be functional, so they are kept
     * (matching Brave's site-scoped tier). Hosts are matched equal-to or
     * subdomain-of, so m.youtube.com matches youtube.com without being listed.
     */
    private static final Map<String, String[]> HOST_SCOPED_TRACKERS;

    static {
        Map<String, String[]> map = new HashMap<>();
        map.put("si", new String[]{"youtube.com", "m.youtube.com",
                "music.youtube.com", "youtu.be", "spotify.com",
                "open.spotify.com"});
        map.put("ref_src", new String[]{"twitter.com", "x.com"});
        map.put("ref_url", new String[]{"twitter.com", "x.com"});
        HOST_SCOPED_TRACKERS = Collections.unmodifiableMap(map);
    }

    public UrlSanitizer() {
    }

    /**
     * Returns the given URL with tracking query parameters removed, or the input
     * unchanged when there is nothing to strip. Null-safe: null returns null.
     * Everything except stripped tokens is preserved byte-for-byte - token
     * order, percent-encoding (case included), {@code +} signs, the fragment,
     * and the scheme/host/path spelling. If every parameter is stripped the
     * bare {@code ?} is dropped too. Non-http(s) URLs (mailto:, market:,
     * relative strings) are returned unchanged.
     *
     * @param url the link to clean
     * @return the cleaned link, the input unchanged when nothing matched, or
     * null when the input is null
     */
    public String sanitize(String url) {
        if (url == null) {
            return null;
        }
        // Only http/https links carry web tracking params; leave any other
        // scheme (and scheme-less strings) untouched
        if (!url.regionMatches(true, 0, "https://", 0, 8)
                && !url.regionMatches(true, 0, "http://", 0, 7)) {
            return url;
        }
        int fragmentIndex = url.indexOf(FRAGMENT_START);
        // the query runs from the first '?' BEFORE the fragment to the fragment
        // (or end): a '?' inside the fragment is fragment content, not a query
        int searchLimit = fragmentIndex < 0 ? url.length() : fragmentIndex;
        int queryIndex = -1;
        for (int i = 0; i < searchLimit; i++) {
            if (url.charAt(i) == QUERY_START.charAt(0)) {
                queryIndex = i;
                break;
            }
        }
        if (queryIndex < 0) {
            // no query at all
            return url;
        }
        // Brave's unsubscribe exception (issue #76): mkt_tok carries the token
        // newsletter links need to opt out, so it must survive on any link that
        // routes through an unsubscribe page
        boolean keepMktTok = containsIgnoreCase(url, "unsubscribe");
        String[] tokens = url.substring(queryIndex + 1, searchLimit)
                .split(PARAM_SEPARATOR, -1);
        StringBuilder cleanedQuery = new StringBuilder(url.length() - queryIndex);
        boolean first = true;
        for (String token : tokens) {
            if (isTrackingParam(token, url, keepMktTok)) {
                continue;
            }
            if (!first) {
                cleanedQuery.append(PARAM_SEPARATOR);
            }
            // kept tokens stay byte-for-byte: no decode/re-encode anywhere
            cleanedQuery.append(token);
            first = false;
        }
        String prefix = url.substring(0, queryIndex);
        String fragment = fragmentIndex < 0 ? "" : url.substring(fragmentIndex);
        if (cleanedQuery.length() == 0) {
            // every parameter was stripped: drop the now-empty bare '?'
            return prefix + fragment;
        }
        return prefix + QUERY_START + cleanedQuery + fragment;
    }

    /**
     * Decides whether one raw query token (still encoded, possibly without an
     * {@code =}) names a tracking parameter. The name is the text before the
     * FIRST '=' only, so {@code a=b=c} names {@code a} and a value containing
     * '=' never leaks into the name; {@code =value} (empty name) is kept.
     *
     * @param token      raw query token, encoded
     * @param url        the full URL, for the host-scoped rules
     * @param keepMktTok true when the unsubscribe exemption applies
     * @return true when this token must be stripped
     */
    private boolean isTrackingParam(String token, String url, boolean keepMktTok) {
        int equalsIndex = token.indexOf('=');
        String name = equalsIndex < 0 ? token : token.substring(0, equalsIndex);
        if (name.isEmpty()) {
            // "=value" names nothing and must be preserved
            return false;
        }
        String lowerName = name.toLowerCase(Locale.US);
        for (String prefix : TRACKING_NAME_PREFIXES) {
            if (lowerName.startsWith(prefix)) {
                return true;
            }
        }
        if (GLOBAL_TRACKERS.contains(lowerName)) {
            return !(keepMktTok && "mkt_tok".equals(lowerName));
        }
        String[] domains = HOST_SCOPED_TRACKERS.get(lowerName);
        if (domains == null) {
            return false;
        }
        return hostMatches(extractHost(url), domains);
    }

    /**
     * Extracts the lowercased host from an http(s) URL: the authority run after
     * "://" up to the first '/', '?' or '#', with userinfo (up to the last '@')
     * and the ':port' suffix stripped (bracketed IPv6 literals keep their
     * brackets, so no ':' inside them is ever mistaken for a port separator).
     *
     * @param url an http(s) URL (scheme already validated)
     * @return the lowercased host
     */
    private String extractHost(String url) {
        int schemeEnd = url.indexOf(SCHEME_DELIMITER) + SCHEME_DELIMITER.length();
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
        String host;
        if (authority.startsWith("[")) {
            int bracketEnd = authority.indexOf(']');
            host = bracketEnd < 0 ? authority : authority.substring(0, bracketEnd + 1);
        } else {
            int colonIndex = authority.indexOf(':');
            host = colonIndex < 0 ? authority : authority.substring(0, colonIndex);
        }
        return host.toLowerCase(Locale.US);
    }

    /**
     * True when the host equals one of the listed domains or is a subdomain of
     * one (e.g. m.youtube.com matches youtube.com).
     *
     * @param host    lowercased host without userinfo/port
     * @param domains the allow-list of tracker scopes for one parameter name
     * @return true when the parameter applies to this host
     */
    private boolean hostMatches(String host, String[] domains) {
        for (String domain : domains) {
            if (host.equals(domain) || host.endsWith("." + domain)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Case-insensitive substring search without allocating a lowercased copy of
     * the whole haystack.
     *
     * @param haystack text to search
     * @param needle   lowercase ASCII text to find
     * @return true when haystack contains needle, ignoring case
     */
    private boolean containsIgnoreCase(String haystack, String needle) {
        int limit = haystack.length() - needle.length();
        for (int i = 0; i <= limit; i++) {
            if (haystack.regionMatches(true, i, needle, 0, needle.length())) {
                return true;
            }
        }
        return false;
    }
}
