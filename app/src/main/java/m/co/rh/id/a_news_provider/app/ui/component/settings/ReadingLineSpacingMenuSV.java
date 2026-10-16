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
 * Settings menu for the article body line spacing. The preference stores the
 * <em>index</em> into {@link ReadingTypography#LINE_SPACING_MULTIPLIER}; the
 * multiplier itself is applied through {@code TextView#setLineSpacing}'s
 * {@code mult} parameter so it scales with both the system font scale and the
 * chosen body size.
 */
public class ReadingLineSpacingMenuSV extends StatefulView<Activity> {

    /**
     * Dialog labels in {@link ReadingTypography#LINE_SPACING_MULTIPLIER} index
     * order, tightest first, with the untouched 1.0 step spelled out as "Default".
     */
    private static final int[] LINE_SPACING_LABEL_RES_IDS = new int[]{
            R.string.reading_line_spacing_tight,
            R.string.reading_line_spacing_default,
            R.string.reading_line_spacing_relaxed,
            R.string.reading_line_spacing_loose,
            R.string.reading_line_spacing_very_loose
    };

    /**
     * How many steps the dialog offers. Bounded by both arrays so the two cannot
     * silently diverge: an index is only ever highlighted, offered or persisted if
     * {@link ReadingTypography#LINE_SPACING_MULTIPLIER} <em>and</em>
     * {@link #LINE_SPACING_LABEL_RES_IDS} have an entry for it. If a later release
     * adds a step without its label, the extra label is dropped instead of
     * offering an option that would resolve to the default and highlight the
     * wrong radio.
     */
    private static final int OPTION_COUNT = Math.min(LINE_SPACING_LABEL_RES_IDS.length,
            ReadingTypography.LINE_SPACING_MULTIPLIER.length);

    @NavInject
    private transient Provider mProvider;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient TextView mSubtitleText;

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater()
                .inflate(R.layout.menu_reading_line_spacing, container, false);
        mAppSharedPreferences = mProvider.get(AppSharedPreferences.class);
        mSubtitleText = view.findViewById(R.id.text_subtitle);
        mSubtitleText.setText(getSubtitleText(activity,
                mAppSharedPreferences.getReadingLineSpacing()));
        View containerMenu = view.findViewById(R.id.container_menu);
        containerMenu.setOnClickListener(view1 -> {
            int checkedItem = checkedIndex(mAppSharedPreferences.getReadingLineSpacing());
            MaterialAlertDialogBuilder materialAlertDialogBuilder =
                    new MaterialAlertDialogBuilder(activity);
            materialAlertDialogBuilder
                    .setTitle(activity.getString(R.string.reading_line_spacing_title));
            materialAlertDialogBuilder.setSingleChoiceItems(buildOptionLabels(activity),
                    checkedItem, (dialogInterface, i) -> {
                        mAppSharedPreferences.setReadingLineSpacing(i);
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
        return activity.getString(LINE_SPACING_LABEL_RES_IDS[checkedIndex(index)]);
    }

    private static CharSequence[] buildOptionLabels(Activity activity) {
        CharSequence[] options = new CharSequence[OPTION_COUNT];
        for (int i = 0; i < OPTION_COUNT; i++) {
            options[i] = activity.getString(LINE_SPACING_LABEL_RES_IDS[i]);
        }
        return options;
    }

    /**
     * Maps a stored preference value onto a valid array index, falling back to the
     * 1.0 multiplier the article layouts already ship with.
     */
    private static int checkedIndex(Integer index) {
        if (index == null || index < 0 || index >= OPTION_COUNT) {
            return ReadingTypography.DEFAULT_LINE_SPACING_INDEX;
        }
        return index;
    }
}