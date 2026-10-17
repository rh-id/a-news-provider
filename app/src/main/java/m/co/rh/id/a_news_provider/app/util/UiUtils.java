package m.co.rh.id.a_news_provider.app.util;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabColorSchemeParams;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.FileProvider;

import com.tokopedia.showcase.ShowCaseBuilder;
import com.tokopedia.showcase.ShowCaseDialog;
import com.tokopedia.showcase.ShowCaseObject;

import java.io.File;
import java.util.ArrayList;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;

public class UiUtils {
    public static void shareFile(Context context, File file, String chooserMessage) {
        Uri fileUri = FileProvider.getUriForFile(
                context,
                "m.co.rh.id.a_news_provider.fileprovider",
                file);
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.setType("*/*");
        shareIntent = Intent.createChooser(shareIntent, chooserMessage);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(shareIntent);
    }

    public static Activity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    public static int getColorFromAttribute(Context context, int attribute) {
        Resources.Theme theme = context.getTheme();
        TypedValue typedValue = new TypedValue();
        theme.resolveAttribute(attribute, typedValue, true);
        return typedValue.data;
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
     * The caller must pass an Activity (never the application context) so no
     * FLAG_ACTIVITY_NEW_TASK is needed and exit animations apply.
     *
     * @param activity launching activity, may be null (fails gracefully)
     * @param url      web url to open
     * @return true if an activity was started to handle the link
     */
    public static boolean openLink(Activity activity, String url) {
        if (activity == null || url == null || url.isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(url);
        AppSharedPreferences appSharedPreferences = BaseApplication.of(activity)
                .getProvider().get(AppSharedPreferences.class);
        String customTabsPackageName = CustomTabsClient.getPackageName(activity, null);
        OpenLinkPolicy.Decision decision = OpenLinkPolicy.decide(
                appSharedPreferences.getOpenLinksMode(), customTabsPackageName);
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
            activity.startActivity(new Intent(Intent.ACTION_VIEW, uri));
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
    private static void launchCustomTabs(Activity activity, Uri uri, String packageName) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        // COLOR_SCHEME_SYSTEM (the 1.9.0 name for follow-the-system day/night)
        // so the custom tab never flashes a white toolbar in dark mode; the
        // params below add the app's own brand colors for both schemes
        builder.setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM);
        // resolved from the daynight theme at click time (dynamic-color aware),
        // so the custom tab matches the app's action bar / status bar in both modes
        CustomTabColorSchemeParams colorSchemeParams = new CustomTabColorSchemeParams.Builder()
                .setToolbarColor(getColorFromAttribute(activity, R.attr.colorPrimary))
                .setNavigationBarColor(getColorFromAttribute(activity, R.attr.colorPrimaryVariant))
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
    public static void routeUrlSpans(TextView textView) {
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
            spannable.setSpan(new CustomTabsUrlSpan(urlSpan.getURL()), start, end, flags);
        }
    }

    /**
     * Copies the given text to the system clipboard.
     *
     * @param context any context
     * @param label   human-readable label describing what is being copied
     * @param text    the text to place on the clipboard
     * @return {@code true} if the text was copied, {@code false} if the clipboard
     * service was unavailable
     */
    public static boolean copyToClipboard(Context context, CharSequence label, CharSequence text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return false;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        return true;
    }

    /**
     * Show RSS channel list showcase dialog.
     *
     * @param activity   The activity to show the showcase on
     * @param drawerView The drawer view containing the target view
     */
    public static void showRssChannelListShowCase(Activity activity, View drawerView) {
        int textColor = R.color.white;
        ShowCaseDialog showCaseDialog = new ShowCaseBuilder()
                .textColorRes(textColor)
                .titleTextColorRes(textColor)
                .shadowColorRes(R.color.shadow)
                .titleTextSizeRes(R.dimen.text_nav_menu)
                .spacingRes(R.dimen.spacing_normal)
                .backgroundContentColorRes(R.color.orange_600)
                .circleIndicatorBackgroundDrawableRes(R.drawable.selector_circle_green)
                .prevStringRes(R.string.previous)
                .nextStringRes(R.string.next)
                .finishStringRes(R.string.finish)
                .useCircleIndicator(false)
                .clickable(true)
                .build();
        String title = activity.getString(R.string.title_showcase_rss_channel_list);
        String description = activity.getString(R.string.showcase_rss_channel_list);
        ArrayList<ShowCaseObject> showCaseList = new ArrayList<>();
        showCaseList.add(new ShowCaseObject(
                drawerView.findViewById(R.id.container_list_channel),
                title,
                description));
        showCaseDialog.show(activity, null, showCaseList);
    }

    private UiUtils() {
    }
}
