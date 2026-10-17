package m.co.rh.id.a_news_provider.app.util;

import m.co.rh.id.a_news_provider.base.AppSharedPreferences;

/**
 * Pure-java decision policy for the "Open links in" preference (issue #35).
 * Contains no android imports so it runs on the JVM for unit tests; the
 * availability of a Custom Tabs-capable browser is passed in as the plain
 * package name returned by {@code CustomTabsClient.getPackageName(context, null)}.
 * <p>
 * Semantics of the inputs:
 * <ul>
 *     <li>{@code prefMode} - the stored preference value, either
 *     {@link AppSharedPreferences#MODE_DEFAULT_BROWSER} or
 *     {@link AppSharedPreferences#MODE_CUSTOM_TABS}. Any unknown value falls
 *     back to the default-browser mode (defensive: a corrupt or future pref
 *     must never make links unopenable).</li>
 *     <li>{@code customTabsPackageName} - package name of an installed browser
 *     declaring Custom Tabs support, or {@code null} (or empty, defensively)
 *     when none is available. Custom Tabs is browser-agnostic: Firefox, Edge,
 *     Brave, Samsung Internet and Chrome all qualify.</li>
 * </ul>
 * <p>
 * Decision order (order is load-bearing):
 * <ol>
 *     <li>pref is MODE_CUSTOM_TABS AND a supporting browser exists - open a
 *     Custom Tabs session with that browser: OPEN_CUSTOM_TAB.</li>
 *     <li>otherwise - open the raw {@code ACTION_VIEW} intent (the default and
 *     also the unconditional fallback when Custom Tabs is requested but no
 *     supporting browser is installed): OPEN_DEFAULT_BROWSER.</li>
 * </ol>
 */
public final class OpenLinkPolicy {

    /**
     * Possible outcomes of {@link #decide(int, String)}.
     */
    public enum Decision {
        /**
         * Launch a raw {@code ACTION_VIEW} intent (current/legacy behavior).
         */
        OPEN_DEFAULT_BROWSER,
        /**
         * Launch the link in a Custom Tabs session of the browser that declared
         * Custom Tabs support.
         */
        OPEN_CUSTOM_TAB
    }

    private OpenLinkPolicy() {
    }

    /**
     * Decides how a web link should be opened.
     *
     * @param prefMode               stored "Open links in" preference value
     * @param customTabsPackageName  package name of an installed Custom Tabs
     *                               browser, or null when unavailable
     * @return the decision, never null
     */
    public static Decision decide(int prefMode, String customTabsPackageName) {
        if (prefMode == AppSharedPreferences.MODE_CUSTOM_TABS
                && customTabsPackageName != null && !customTabsPackageName.isEmpty()) {
            return Decision.OPEN_CUSTOM_TAB;
        }
        return Decision.OPEN_DEFAULT_BROWSER;
    }
}
