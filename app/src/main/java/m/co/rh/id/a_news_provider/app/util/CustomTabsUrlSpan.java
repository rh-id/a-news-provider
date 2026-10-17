package m.co.rh.id.a_news_provider.app.util;

import android.app.Activity;
import android.text.style.URLSpan;
import android.view.View;

/**
 * A {@link URLSpan} that routes taps through {@link UiUtils#openLink(Activity, String)}
 * so links inside article descriptions, HTML text and Linkified dialogs honor the
 * "Open links in" preference (issue #35) instead of firing a raw ACTION_VIEW.
 * <p>
 * Subclassing URLSpan (rather than swapping in a generic ClickableSpan) keeps the
 * platform link semantics: TalkBack still announces the text as a link and
 * activates it with the standard double-tap.
 */
public class CustomTabsUrlSpan extends URLSpan {

    public CustomTabsUrlSpan(String url) {
        super(url);
    }

    @Override
    public void onClick(View widget) {
        Activity activity = UiUtils.getActivity(widget);
        if (activity != null) {
            // the preference is read inside openLink at CLICK time, never when
            // the span was attached, so a settings change applies immediately
            UiUtils.openLink(activity, getURL());
        } else {
            // no activity behind the view (defensive): keep the platform behavior
            super.onClick(widget);
        }
    }
}
