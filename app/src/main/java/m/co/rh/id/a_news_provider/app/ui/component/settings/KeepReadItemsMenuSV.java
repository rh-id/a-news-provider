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
 * Settings menu for the read-item retention window (issue #37): when enabled,
 * read, non-favorite rss items that no longer appear in their feed are deleted at
 * the end of a sync once they have not been seen for the chosen number of days.
 * Off (0) disables the feature entirely (default). Items still present in a feed
 * are re-stamped by every sync and never deleted; unread items and favorites are
 * never deleted; paused feeds age under the same window (global opt-in).
 */
public class KeepReadItemsMenuSV extends StatefulView<Activity> {

    /**
     * Allowed retention values in days; index 0 (0 = off) disables the feature.
     */
    private static final int[] KEEP_READ_ITEMS_DAYS_OPTIONS = new int[]{0, 7, 14, 30, 90};

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.menu_keep_read_items, container, false);
        Provider provider = BaseApplication.of(activity).getProvider();
        AppSharedPreferences appSharedPreferences = provider.get(AppSharedPreferences.class);
        TextView subtitleText = view.findViewById(R.id.text_subtitle);
        subtitleText.setText(getSubtitleText(activity, appSharedPreferences.getKeepReadItemsDays()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = indexOfDays(appSharedPreferences.getKeepReadItemsDays());
            MaterialAlertDialogBuilder materialAlertDialogBuilder = new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder.setTitle(activity.getString(R.string.keep_read_items_period_in_day));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity), checkedItem,
                    (dialogInterface, i) ->
                    {
                        int days = KEEP_READ_ITEMS_DAYS_OPTIONS[i];
                        appSharedPreferences.setKeepReadItemsDays(days);
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
            return activity.getResources().getQuantityString(R.plurals.keep_read_items_after_x_days, days, days);
        }
        return activity.getString(R.string.keep_read_items_off);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[KEEP_READ_ITEMS_DAYS_OPTIONS.length];
        for (int i = 0; i < KEEP_READ_ITEMS_DAYS_OPTIONS.length; i++) {
            int days = KEEP_READ_ITEMS_DAYS_OPTIONS[i];
            if (days > 0) {
                // x_days is the generic shared day-option plural, same as the
                // auto-mark-read menu uses
                options[i] = activity.getResources().getQuantityString(R.plurals.x_days, days, days);
            } else {
                options[i] = activity.getString(R.string.keep_read_items_off);
            }
        }
        return options;
    }

    private static int indexOfDays(int days) {
        for (int i = 0; i < KEEP_READ_ITEMS_DAYS_OPTIONS.length; i++) {
            if (KEEP_READ_ITEMS_DAYS_OPTIONS[i] == days) {
                return i;
            }
        }
        return 0;
    }
}
