package m.co.rh.id.a_news_provider.app.util;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Layout;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import m.co.rh.id.a_news_provider.base.AppSharedPreferences;

/**
 * Immutable snapshot of the user's reading preferences, plus the two appliers that
 * push it onto a view. Everything reads prefs through this one object, so a future
 * switch of the article renderer (for example a WebView) only adds a method here
 * rather than re-plumbing preferences and UI.
 *
 * <p>Indices, not raw values: the four preferences stored on
 * {@link AppSharedPreferences} are positions in the arrays below, which makes an
 * out-of-range setting impossible by construction at write time and recoverable by
 * clamping at read time.
 */
public final class ReadingTypography {

    /** Body text size steps in sp. Index 2 (18sp) is the default and matches the current XML. */
    public static final int[] FONT_SIZE_SP = {14, 16, 18, 20, 22, 24};
    public static final int DEFAULT_FONT_SIZE_INDEX = 2;

    /** Line-spacing multipliers. Index 1 (1.0) is the default and matches the current XML. */
    public static final float[] LINE_SPACING_MULTIPLIER = {0.9f, 1.0f, 1.25f, 1.5f, 1.9f};
    public static final int DEFAULT_LINE_SPACING_INDEX = 1;

    public static final int FONT_FAMILY_SANS = 0;
    public static final int FONT_FAMILY_SERIF = 1;
    public static final int FONT_FAMILY_MONOSPACE = 2;

    /** Current hardcoded heading size in both detail layouts; 18sp is the body baseline. */
    public static final int BASE_TITLE_SP = 24;

    private final int mFontSizeIndex;
    private final int mFontFamily;
    private final int mLineSpacingIndex;
    private final boolean mJustified;

    private ReadingTypography(int fontSizeIndex, int fontFamily, int lineSpacingIndex,
                              boolean justified) {
        mFontSizeIndex = clamp(fontSizeIndex, FONT_SIZE_SP.length, DEFAULT_FONT_SIZE_INDEX);
        mFontFamily = clamp(fontFamily, 3, FONT_FAMILY_SANS);
        mLineSpacingIndex = clamp(lineSpacingIndex, LINE_SPACING_MULTIPLIER.length,
                DEFAULT_LINE_SPACING_INDEX);
        mJustified = justified;
    }

    /**
     * Reads the current user preferences. Android-dependent; unit-test the pure
     * {@link #of(int, int, int, boolean)} overload instead, which needs no Context.
     *
     * @param prefs the app's shared preferences
     * @return the typography snapshot those preferences describe
     */
    public static ReadingTypography from(AppSharedPreferences prefs) {
        return of(orDefault(prefs.getReadingFontSize(), DEFAULT_FONT_SIZE_INDEX),
                orDefault(prefs.getReadingFontFamily(), FONT_FAMILY_SANS),
                orDefault(prefs.getReadingLineSpacing(), DEFAULT_LINE_SPACING_INDEX),
                prefs.isReadingJustification());
    }

    /**
     * Pure constructor for JVM unit tests — no Android types, no pref mocking
     * needed. Out-of-range indices clamp to the default.
     *
     * @param fontSizeIndex    index into {@link #FONT_SIZE_SP}
     * @param fontFamily       one of {@link #FONT_FAMILY_SANS},
     *                         {@link #FONT_FAMILY_SERIF}, {@link #FONT_FAMILY_MONOSPACE}
     * @param lineSpacingIndex index into {@link #LINE_SPACING_MULTIPLIER}
     * @param justified        whether the body text should be justified
     * @return the clamped typography snapshot
     */
    public static ReadingTypography of(int fontSizeIndex, int fontFamily,
                                       int lineSpacingIndex, boolean justified) {
        return new ReadingTypography(fontSizeIndex, fontFamily, lineSpacingIndex, justified);
    }

    /**
     * The resolved body text size, in sp.
     *
     * @return the body size this snapshot selects
     */
    public int getFontSizeSp() {
        return FONT_SIZE_SP[mFontSizeIndex];
    }

    /**
     * The resolved heading size in sp: the 24sp baseline scaled by the same factor
     * as the body, so the 4:3 title-to-body relationship holds at every step and the
     * two never collapse to the same size.
     *
     * @return the title size this snapshot selects
     */
    public float getTitleSp() {
        float ratio = (float) getFontSizeSp() / FONT_SIZE_SP[DEFAULT_FONT_SIZE_INDEX];
        return BASE_TITLE_SP * ratio;
    }

    /**
     * The resolved line-spacing multiplier.
     *
     * @return the line-spacing multiplier this snapshot selects
     */
    public float getLineSpacingMultiplier() {
        return LINE_SPACING_MULTIPLIER[mLineSpacingIndex];
    }

    /**
     * The resolved font family constant.
     *
     * @return one of {@link #FONT_FAMILY_SANS}, {@link #FONT_FAMILY_SERIF} or
     * {@link #FONT_FAMILY_MONOSPACE}
     */
    public int getFontFamily() {
        return mFontFamily;
    }

    /**
     * Whether the body text should be justified. Only honoured on API 26+.
     *
     * @return true when justification is requested
     */
    public boolean isJustified() {
        return mJustified;
    }

    /**
     * Applies typography to the article body. Safe on all API levels; the
     * justification branch is skipped below API 26.
     *
     * @param view the article body text view
     */
    public void applyToBody(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, getFontSizeSp());
        applyFontFamily(view);
        applyLineSpacing(view);
        applyJustification(view);
    }

    /**
     * Title keeps its 24sp heading but scales proportionally to the chosen body
     * size. Justification is deliberately not applied here: a flushed-both-edges
     * heading reads worse than a ragged one and the setting is about body copy.
     *
     * @param view the article title text view
     */
    public void applyToTitle(TextView view) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, getTitleSp());
        applyFontFamily(view);
        applyLineSpacing(view);
    }

    private void applyFontFamily(TextView view) {
        switch (mFontFamily) {
            case FONT_FAMILY_SERIF:
                view.setTypeface(Typeface.SERIF);
                break;
            case FONT_FAMILY_MONOSPACE:
                view.setTypeface(Typeface.MONOSPACE);
                break;
            default:
                view.setTypeface(Typeface.DEFAULT);
                break;
        }
    }

    /**
     * Line spacing uses the {@code mult} parameter and a zero {@code add}.
     *
     * <p>The reason is that {@code add} is an absolute pixel offset that does not
     * track {@code fontScale}, whereas {@code mult} is applied to the paint's
     * measured line height, which is already derived from the sp text size and
     * therefore already scales with both the system Accessibility font size and the
     * user's chosen body size. Passing a fixed-sp {@code add} instead would need a
     * {@code TypedValue.applyDimension} conversion to stay accessible, and would
     * still add the same absolute gap at 14sp and 24sp — and the tightest preset
     * (0.9) would drive {@code add} negative, which can clip tall glyphs.
     *
     * @param view the text view to set leading on
     */
    private void applyLineSpacing(TextView view) {
        view.setLineSpacing(0f, getLineSpacingMultiplier());
    }

    private void applyJustification(TextView view) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            applyJustificationFromO(view);
        }
        // Below O there is no justification mode at all, so there is nothing to
        // reset: the platform never justifies and a re-bound view cannot carry a
        // stale mode in from another API level. The "reset to NONE defensively"
        // requirement only bites on O+, where a view reused after the setting is
        // switched off would otherwise keep justifying.
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("WrongConstant")
    private void applyJustificationFromO(TextView view) {
        // Layout.JUSTIFICATION_MODE_* and LineBreaker.JUSTIFICATION_MODE_* are the
        // same three values, but the LineBreaker spelling is API 29 while
        // TextView.setJustificationMode() is API 26 - using it would move this
        // feature's floor from 26 to 29. Hence the Layout constants plus the
        // suppression lint demands for the IntDef mismatch.
        view.setJustificationMode(mJustified
                ? Layout.JUSTIFICATION_MODE_INTER_WORD
                : Layout.JUSTIFICATION_MODE_NONE);
    }

    private static int clamp(int value, int length, int fallback) {
        if (value < 0 || value >= length) {
            return fallback;
        }
        return value;
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}