package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings menu for the "Open links in" preference (issue #35): two modes,
 * the device default browser (raw ACTION_VIEW, the pre-existing behavior and
 * default) or a Custom Tabs session when a supporting browser is installed.
 * The final availability fallback is decided per click by app/util/OpenLinkPolicy.
 * <p>
 * Same dialog-with-single-choice-items pattern as {@link ReadingFontFamilyMenuSV}
 * since this is a multi-choice value, not a switch.
 */
public class OpenLinksInMenuSV extends StatefulView<Activity> {

    /**
     * Dialog labels in AppSharedPreferences.MODE_* order (default browser,
     * custom tabs).
     */
    private static final int[] MODE_LABEL_RES_IDS = new int[]{
            R.string.open_links_default_browser,
            R.string.open_links_custom_tabs
    };

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient TextView mSubtitleText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_open_links_in, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSubtitleText = view.findViewById(R.id.text_subtitle);
        mSubtitleText.setText(getSubtitleText(activity, mAppSharedPreferences.getOpenLinksMode()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = checkedIndex(mAppSharedPreferences.getOpenLinksMode());
            MaterialAlertDialogBuilder materialAlertDialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder
                    .setTitle(activity.getString(R.string.menu_open_links_in));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity),
                    checkedItem, (dialogInterface, i) -> {
                        mAppSharedPreferences.setOpenLinksMode(i);
                        mSubtitleText.setText(getSubtitleText(activity, i));
                        dialogInterface.dismiss();
                    });
            materialAlertDialogBuilder.setNegativeButton(android.R.string.cancel,
                    (dialogInterface, i) -> {
                        // leave blank
                    });
            materialAlertDialogBuilder.create().show();
        });

        return view;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mProvider = null;
        mAppSharedPreferences = null;
        mSubtitleText = null;
    }

    private CharSequence getSubtitleText(Activity activity, int mode) {
        return activity.getString(MODE_LABEL_RES_IDS[checkedIndex(mode)]);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[MODE_LABEL_RES_IDS.length];
        for (int i = 0; i < MODE_LABEL_RES_IDS.length; i++) {
            options[i] = activity.getString(MODE_LABEL_RES_IDS[i]);
        }
        return options;
    }

    /**
     * Maps a stored preference value onto a valid mode index, falling back to
     * the default-browser mode (the pre-existing behavior).
     */
    private static int checkedIndex(int mode) {
        if (mode < 0 || mode >= MODE_LABEL_RES_IDS.length) {
            return AppSharedPreferences.MODE_DEFAULT_BROWSER;
        }
        return mode;
    }
}
