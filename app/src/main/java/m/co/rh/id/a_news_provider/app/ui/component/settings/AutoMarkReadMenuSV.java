package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.BaseApplication;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings menu for the unread retention window: when enabled, unread rss items
 * older than the chosen number of days are automatically marked as read at the
 * end of each sync. Off (0) disables the feature entirely (default).
 * Favorites are never auto-marked.
 */
public class AutoMarkReadMenuSV extends StatefulView<Activity> {

    /**
     * Allowed retention values in days; index 0 (0 = off) disables the feature.
     */
    private static final int[] AUTO_MARK_READ_DAYS_OPTIONS = new int[]{0, 7, 14, 30, 90};

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_auto_mark_read, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        TextView subtitleText = view.findViewById(R.id.text_subtitle);
        subtitleText.setText(getSubtitleText(activity, appSharedPreferences.getAutoMarkReadDays()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = indexOfDays(appSharedPreferences.getAutoMarkReadDays());
            MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder.setTitle(activity.getString(R.string.auto_mark_read_period_in_day));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity), checkedItem,
                    (dialogInterface, i) ->
                    {
                        int days = AUTO_MARK_READ_DAYS_OPTIONS[i];
                        appSharedPreferences.setAutoMarkReadDays(days);
                        subtitleText.setText(getSubtitleText(activity, days));
                        dialogInterface.dismiss();
                    });
            materialAlertDialogBuilder.setNegativeButton(android.R.string.cancel, (dialogInterface, i) -> {
                // leave blank
            });
            materialAlertDialogBuilder.create().show();
        });

        return view;
    }

    private static String getSubtitleText(Activity activity, int days) {
        if (days > 0) {
            return activity.getResources().getQuantityString(R.plurals.auto_mark_read_after_x_days, days, days);
        }
        return activity.getString(R.string.auto_mark_read_off);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[AUTO_MARK_READ_DAYS_OPTIONS.length];
        for (int i = 0; i < AUTO_MARK_READ_DAYS_OPTIONS.length; i++) {
            int days = AUTO_MARK_READ_DAYS_OPTIONS[i];
            if (days > 0) {
                options[i] = activity.getResources().getQuantityString(R.plurals.x_days, days, days);
            } else {
                options[i] = activity.getString(R.string.auto_mark_read_off);
            }
        }
        return options;
    }

    private static int indexOfDays(int days) {
        for (int i = 0; i < AUTO_MARK_READ_DAYS_OPTIONS.length; i++) {
            if (AUTO_MARK_READ_DAYS_OPTIONS[i] == days) {
                return i;
            }
        }
        return 0;
    }
}
