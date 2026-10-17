package m.co.rh.id.a_news_provider.app.ui.page;

import android.app.Activity;
import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

public class DonationsPage extends StatefulView<Activity> implements View.OnClickListener {
    private static final String TAG = DonationsPage.class.getName();
    private static final String DEV_URL = "<a href='https://rh-apps.github.io/'>https://rh-apps.github.io/</a>";
    private static final String DONATE_URL = "https://teer.id/rh-id";

    @NavInject
    private transient Provider mProvider;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.page_donations, container, false);
        Button donate = rootLayout.findViewById(R.id.button_donate);
        donate.setOnClickListener(this);
        TextView otherApps = rootLayout.findViewById(R.id.text_other_apps);
        String otherAppMsg = activity.getString(R.string.donation_other_apps, DEV_URL);
        otherApps.setText(HtmlCompat.fromHtml(otherAppMsg, HtmlCompat.FROM_HTML_MODE_LEGACY));
        otherApps.setMovementMethod(LinkMovementMethod.getInstance());
        // the dev-site URLSpan would fire a raw ACTION_VIEW internally; swap it for
        // a span that honors the "Open links in" preference (issue #35)
        UiUtils.routeUrlSpans(otherApps);
        return rootLayout;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.button_donate) {
            Context context = view.getContext();
            boolean opened = UiUtils.openLink(UiUtils.getActivity(view), DONATE_URL);
            ILogger logger = mProvider.get(ILogger.class);
            if (opened) {
                logger.i(TAG, context.getString(R.string.donation_thank_you));
            } else {
                // openLink already swallowed the ActivityNotFoundException; the
                // old retry-identical-url fallback here was dead code
                logger.e(TAG, context.getString(R.string.error_open_link));
            }
        }
    }
}
