package m.co.rh.id.a_news_provider.app.ui.component.settings;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.util.ReadingTypography;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.aprovider.Provider;

/**
 * Settings menu for the article body font family. The preference stores the
 * <em>index</em> into the {@link ReadingTypography#FONT_FAMILY_*} constants, not
 * the raw value, so the mapping to {@code Typeface.SERIF} and friends lives in
 * exactly one place.
 */
public class ReadingFontFamilyMenuSV extends StatefulView<Activity> {

    /**
     * Dialog labels in {@link ReadingTypography} family-index order (sans, serif,
     * monospace).
     */
    private static final int[] FAMILY_LABEL_RES_IDS = new int[]{
            R.string.reading_font_family_sans,
            R.string.reading_font_family_serif,
            R.string.reading_font_family_monospace
    };

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient TextView mSubtitleText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_reading_font_family, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSubtitleText = view.findViewById(R.id.text_subtitle);
        mSubtitleText.setText(getSubtitleText(activity,
                mAppSharedPreferences.getReadingFontFamily()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = checkedIndex(mAppSharedPreferences.getReadingFontFamily());
            MaterialAlertDialogBuilder materialAlertDialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder
                    .setTitle(activity.getString(R.string.reading_font_family_title));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity),
                    checkedItem, (dialogInterface, i) -> {
                        mAppSharedPreferences.setReadingFontFamily(i);
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

    private CharSequence getSubtitleText(Activity activity, Integer index) {
        return activity.getString(FAMILY_LABEL_RES_IDS[checkedIndex(index)]);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[FAMILY_LABEL_RES_IDS.length];
        for (int i = 0; i < FAMILY_LABEL_RES_IDS.length; i++) {
            options[i] = activity.getString(FAMILY_LABEL_RES_IDS[i]);
        }
        return options;
    }

    /**
     * Maps a stored preference value onto a valid family index, falling back to
     * the platform sans the article layouts already ship with.
     */
    private static int checkedIndex(Integer index) {
        if (index == null || index < 0
                || index > ReadingTypography.FONT_FAMILY_MONOSPACE) {
            return ReadingTypography.FONT_FAMILY_SANS;
        }
        return index;
    }
}