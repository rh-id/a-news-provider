package m.co.rh.id.a_news_provider.app.util;

import org.junit.Test;

import m.co.rh.id.a_news_provider.base.AppSharedPreferences;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for OpenLinkPolicy covering both "Open links in" modes (issue #35)
 * with a Custom Tabs-capable browser available or not, plus the defensive
 * fallbacks for an empty package name and an unknown stored mode.
 */
public class OpenLinkPolicyTest {

    private static final String A_CUSTOM_TABS_BROWSER = "com.example.customtabs.browser";

    @Test
    public void testDefaultBrowserModeAlwaysOpensPlainBrowser() {
        // mode preference wins even when a supporting browser IS installed
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(AppSharedPreferences.MODE_DEFAULT_BROWSER,
                        A_CUSTOM_TABS_BROWSER));
        // and of course without one
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(AppSharedPreferences.MODE_DEFAULT_BROWSER, null));
    }

    @Test
    public void testCustomTabsModeOpensCustomTabsWhenProviderAvailable() {
        assertEquals(OpenLinkPolicy.Decision.OPEN_CUSTOM_TAB,
                OpenLinkPolicy.decide(AppSharedPreferences.MODE_CUSTOM_TABS,
                        A_CUSTOM_TABS_BROWSER));
    }

    @Test
    public void testCustomTabsModeFallsBackWhenNoProviderAvailable() {
        // CustomTabsClient.getPackageName returns null when no installed browser
        // declares Custom Tabs support: fall back unconditionally, never fail
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(AppSharedPreferences.MODE_CUSTOM_TABS, null));
        // defensive: treat an empty package name the same as none
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(AppSharedPreferences.MODE_CUSTOM_TABS, ""));
    }

    @Test
    public void testUnknownModeFallsBackToDefaultBrowser() {
        // a corrupt or future pref value must never make links unopenable
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(-1, A_CUSTOM_TABS_BROWSER));
        assertEquals(OpenLinkPolicy.Decision.OPEN_DEFAULT_BROWSER,
                OpenLinkPolicy.decide(99, A_CUSTOM_TABS_BROWSER));
    }
}
