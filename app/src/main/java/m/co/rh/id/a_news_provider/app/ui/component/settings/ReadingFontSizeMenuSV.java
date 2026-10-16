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
 * Settings menu for the article body text size. The preference stores the
 * <em>index</em> into {@link ReadingTypography#FONT_SIZE_SP}, so the array stays
 * the single source of truth for the available steps and an out-of-range stored
 * value is repaired on read instead of crashing the dialog.
 */
public class ReadingFontSizeMenuSV extends StatefulView<Activity> {

    /**
     * Dialog labels in {@link ReadingTypography#FONT_SIZE_SP} index order. One
     * label per step, including the two defaults that are spelled out as
     * "Default" so the current setting is never an unnamed entry.
     */
    private static final int[] SIZE_LABEL_RES_IDS = new int[]{
            R.string.reading_font_size_small,
            R.string.reading_font_size_medium,
            R.string.reading_font_size_default,
            R.string.reading_font_size_large,
            R.string.reading_font_size_extra_large,
            R.string.reading_font_size_huge
    };

    /**
     * How many steps the dialog offers. Bounded by both arrays so the two cannot
     * silently diverge: an index is only ever highlighted, offered or persisted if
     * {@link ReadingTypography#FONT_SIZE_SP} <em>and</em>
     * {@link #SIZE_LABEL_RES_IDS} have an entry for it. If a later release adds a
     * step without its label, the extra label is dropped instead of offering an
     * option that would resolve to the default and highlight the wrong radio.
     */
    private static final int OPTION_COUNT =
            Math.min(SIZE_LABEL_RES_IDS.length, ReadingTypography.FONT_SIZE_SP.length);

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient TextView mSubtitleText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_reading_font_size, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSubtitleText = view.findViewById(R.id.text_subtitle);
        mSubtitleText.setText(getSubtitleText(activity,
                mAppSharedPreferences.getReadingFontSize()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = checkedIndex(mAppSharedPreferences.getReadingFontSize());
            MaterialAlertDialogBuilder materialAlertDialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder
                    .setTitle(activity.getString(R.string.reading_font_size_title));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity),
                    checkedItem, (dialogInterface, i) -> {
                        mAppSharedPreferences.setReadingFontSize(i);
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
        return activity.getString(SIZE_LABEL_RES_IDS[checkedIndex(index)]);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[OPTION_COUNT];
        for (int i = 0; i < OPTION_COUNT; i++) {
            options[i] = activity.getString(SIZE_LABEL_RES_IDS[i]);
        }
        return options;
    }

    /**
     * Maps a stored preference value onto a valid array index. An index that no
     * longer exists (an array trimmed by a later release, a hand-edited prefs
     * file) falls back to the documented default rather than crashing.
     */
    private static int checkedIndex(Integer index) {
        if (index == null || index < 0 || index >= OPTION_COUNT) {
            return ReadingTypography.DEFAULT_FONT_SIZE_INDEX;
        }
        return index;
    }
}