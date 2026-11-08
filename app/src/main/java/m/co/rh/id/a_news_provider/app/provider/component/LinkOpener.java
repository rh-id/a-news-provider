package m.co.rh.id.a_news_provider.app.provider.component;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.util.OpenLinkPolicy;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.util.UrlSanitizer;
import m.co.rh.id.aprovider.Provider;

/**
 * Opens web links for the UI, honoring the "Open links in" preference
 * (issue #35) and the strip-tracking-parameters toggle (issue #76). Extracted
 * from the all-static {@link UiUtils} as a Provider-registered instance class
 * (same precedent as {@link UrlSanitizer}): its two dependencies are
 * Provider-injected final fields instead of the per-call service-locator
 * lookups the static version performed. Both preferences are read inside
 * {@link #openLink(Activity, String)}, at CLICK time, so a settings change
 * applies to the very next tap.
 */
public class LinkOpener {

    private final AppSharedPreferences mAppSharedPreferences;
    private final UrlSanitizer mUrlSanitizer;

    public LinkOpener(Provider provider) {
        mAppSharedPreferences = provider.get(AppSharedPreferences.class);
        mUrlSanitizer = provider.get(UrlSanitizer.class);
    }

    /**
     * Opens a web link honoring the "Open links in" preference (issue #35). The
     * preference is read here, at CLICK time, so a settings change applies to the
     * very next tap. Custom Tabs mode launches via androidx.browser when a
     * supporting browser is installed (decided by the JVM-testable
     * {@link OpenLinkPolicy}); every other case - including a missing supporting
     * browser - falls back to a raw ACTION_VIEW intent. ActivityNotFoundException
     * is swallowed here so every call site fails gracefully instead of crashing.
     * <p>
     * When the strip-tracking-parameters toggle is on (issue #76, default on),
     * tracking query parameters (utm_* / fbclid-style click ids) are removed from a
     * LOCAL copy of the link before launch - the caller's
     * {@code RssItem.link} is never mutated.
     * <p>
     * The caller must pass an Activity (never the application context) so no
     * FLAG_ACTIVITY_NEW_TASK is needed and exit animations apply.
     *
     * @param activity launching activity, may be null (fails gracefully)
     * @param url      web url to open
     * @return true if an activity was started to handle the link
     */
    public boolean openLink(Activity activity, String url) {
        if (activity == null || url == null || url.isEmpty()) {
            return false;
        }
        // strip tracking params at CLICK time (issue #76): sanitize a LOCAL copy
        // only - RssItem.link is the cross-channel read/favorite/dedup key and is
        // never written back. Read the toggle here too, so a settings change
        // applies to the very next tap (same as the "Open links in" mode below).
        String sanitizedUrl = mAppSharedPreferences.isStripTrackingParamsEnabled()
                ? mUrlSanitizer.sanitize(url)
                : url;
        Uri uri = Uri.parse(sanitizedUrl);
        String customTabsPackageName = CustomTabsClient.getPackageName(activity, null);
        OpenLinkPolicy.Decision decision = OpenLinkPolicy.decide(
                mAppSharedPreferences.getOpenLinksMode(), customTabsPackageName);
        try {
            if (decision == OpenLinkPolicy.Decision.OPEN_CUSTOM_TAB) {
                try {
                    launchCustomTabs(activity, uri, customTabsPackageName);
                    return true;
                } catch (ActivityNotFoundException e) {
                    // the provider vanished between the availability check and the
                    // launch: fall through to the plain ACTION_VIEW path below
                }
            }
            startActivity(activity, new Intent(Intent.ACTION_VIEW, uri));
            return true;
        } catch (ActivityNotFoundException e) {
            // no browser installed to handle the link: fail gracefully
            return false;
        }
    }

    /**
     * Starts a Custom Tabs session with the browser that declared Custom Tabs
     * support. Theming follows the system day/night scheme and the app's own
     * daynight theme attributes (never a hardcoded toolbar color), so a dark-mode
     * user never gets a white toolbar flash.
     *
     * @param activity    launching activity
     * @param uri         link to open
     * @param packageName package of the Custom Tabs-capable browser, never null
     */
    private void launchCustomTabs(Activity activity, Uri uri, String packageName) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        // COLOR_SCHEME_SYSTEM (the 1.9.0 name for follow-the-system day/night)
        // so the custom tab never flashes a white toolbar in dark mode; the
        // params below add the app's own brand colors for both schemes
        builder.setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM);
        // resolved from the daynight theme at click time (dynamic-color aware),
        // so the custom tab matches the app's action bar / status bar in both modes
        CustomTabColorSchemeParams colorSchemeParams = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(UiUtils.getColorFromAttribute(activity, R.attr.colorPrimary))
                .setNavigationBarColor(UiUtils.getColorFromAttribute(activity, R.attr.colorPrimaryVariant))
                .build();
        builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_LIGHT, colorSchemeParams);
        builder.setColorSchemeParams(CustomTabsIntent.COLOR_SCHEME_DARK, colorSchemeParams);
        CustomTabsIntent customTabsIntent = builder.build();
        // pin to the browser found by CustomTabsClient so the tap cannot resolve
        // to a non-supporting default browser instead
        customTabsIntent.intent.setPackage(packageName);
        customTabsIntent.launchUrl(activity, uri);
    }

    /**
     * Reroutes every {@link URLSpan} in the TextView's current text through
     * {@link #openLink(Activity, String)} (issue #35), so links inside HTML
     * descriptions and Linkified dialogs honor the "Open links in" preference
     * instead of firing a raw ACTION_VIEW internally. Swapping in a
     * {@link CustomTabsUrlSpan} (a URLSpan subclass) keeps the platform link
     * semantics - TalkBack still announces and activates the text as a link.
     *
     * @param textView text view whose links should be rerouted
     */
    public void routeUrlSpans(TextView textView) {
        CharSequence text = textView.getText();
        Spannable spannable;
        if (text instanceof Spannable) {
            spannable = (Spannable) text;
        } else {
            // HtmlCompat.fromHtml returns an immutable Spanned: copy it into a
            // mutable buffer the TextView keeps, so the spans below can be swapped
            textView.setText(new SpannableStringBuilder(text), TextView.BufferType.SPANNABLE);
            spannable = (Spannable) textView.getText();
        }
        URLSpan[] urlSpans = spannable.getSpans(0, spannable.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            int start = spannable.getSpanStart(urlSpan);
            int end = spannable.getSpanEnd(urlSpan);
            int flags = spannable.getSpanFlags(urlSpan);
            spannable.removeSpan(urlSpan);
            spannable.setSpan(new CustomTabsUrlSpan(urlSpan.getURL(), this), start, end, flags);
        }
    }

    /**
     * Launch seam for the instrumented tests (same precedent as
     * FetchArticleContentCmd's package-private {@code fetchArticleBytes} seam,
     * no Mockito on device): the ACTION_VIEW path starts the activity through
     * this method so a test subclass can record the intent instead of launching
     * it. NOT used by the Custom Tabs path - {@code launchUrl} there is a
     * library call that stays exercised for real.
     *
     * @param activity launching activity
     * @param intent   the ACTION_VIEW intent to start
     */
    void startActivity(Activity activity, Intent intent) {
        activity.startActivity(intent);
    }
}
