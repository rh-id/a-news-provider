package m.co.rh.id.a_news_provider.app.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests for {@link ReadingTypography}, covering the pure index-to-value
 * resolution only.
 *
 * <p>Deliberately tests {@link ReadingTypography#of(int, int, int, boolean)}
 * rather than {@code from(prefs)}: the latter needs a real Android
 * {@code Context} to build an {@code AppSharedPreferences}, and the appliers
 * ({@code applyToBody} / {@code applyToTitle}) need a real {@code TextView}.
 * Everything reachable without a device is reachable through {@code of()}.
 */
public class ReadingTypographyTest {

    private static final int SANS = ReadingTypography.FONT_FAMILY_SANS;
    private static final int DEFAULT_SIZE_INDEX = ReadingTypography.DEFAULT_FONT_SIZE_INDEX;
    private static final int DEFAULT_SPACING_INDEX = ReadingTypography.DEFAULT_LINE_SPACING_INDEX;

    // ========================================================================
    // index -> value resolution
    // ========================================================================

    @Test
    public void testEveryFontSizeIndexResolvesToItsStep() {
        int[] sizes = ReadingTypography.FONT_SIZE_SP;
        assertEquals(6, sizes.length);
        for (int index = 0; index < sizes.length; index++) {
            ReadingTypography typography = ReadingTypography.of(index, SANS,
                    DEFAULT_SPACING_INDEX, false);
            assertEquals("index " + index + " must resolve to " + sizes[index] + "sp",
                    sizes[index], typography.getFontSizeSp());
        }
    }

    @Test
    public void testFontSizeStepsAreStrictlyIncreasing() {
        int[] sizes = ReadingTypography.FONT_SIZE_SP;
        for (int i = 1; i < sizes.length; i++) {
            assertTrue("step " + i + " (" + sizes[i] + ") must be larger than step "
                            + (i - 1) + " (" + sizes[i - 1] + ")",
                    sizes[i] > sizes[i - 1]);
        }
    }

    @Test
    public void testDefaultFontSizeIndexIsTheBaselineBodySize() {
        // index 2 must stay the 18sp the detail layouts ship with, otherwise the
        // "Default" label in the settings dialog stops describing what an
        // untouched install renders
        assertEquals(18, ReadingTypography.FONT_SIZE_SP[DEFAULT_SIZE_INDEX]);
        ReadingTypography typography = ReadingTypography.of(DEFAULT_SIZE_INDEX, SANS,
                DEFAULT_SPACING_INDEX, false);
        assertEquals(18, typography.getFontSizeSp());
    }

    @Test
    public void testEveryLineSpacingIndexResolvesToItsMultiplier() {
        float[] multipliers = ReadingTypography.LINE_SPACING_MULTIPLIER;
        assertEquals(5, multipliers.length);
        for (int index = 0; index < multipliers.length; index++) {
            ReadingTypography typography = ReadingTypography.of(DEFAULT_SIZE_INDEX, SANS,
                    index, false);
            assertEquals("index " + index + " must resolve to " + multipliers[index],
                    multipliers[index], typography.getLineSpacingMultiplier(), 0f);
        }
    }

    @Test
    public void testDefaultLineSpacingIndexIsNeutralLeading() {
        assertEquals(1.0f,
                ReadingTypography.LINE_SPACING_MULTIPLIER[DEFAULT_SPACING_INDEX], 0f);
        ReadingTypography typography = ReadingTypography.of(DEFAULT_SIZE_INDEX, SANS,
                DEFAULT_SPACING_INDEX, false);
        assertEquals(1.0f, typography.getLineSpacingMultiplier(), 0f);
    }

    @Test
    public void testEveryFontFamilyIndexIsAcceptedUnchanged() {
        for (int family = ReadingTypography.FONT_FAMILY_SANS;
             family <= ReadingTypography.FONT_FAMILY_MONOSPACE; family++) {
            ReadingTypography typography = ReadingTypography.of(DEFAULT_SIZE_INDEX, family,
                    DEFAULT_SPACING_INDEX, false);
            assertEquals("family index " + family + " must survive unchanged",
                    family, typography.getFontFamily());
        }
    }

    @Test
    public void testJustificationFlagRoundTrips() {
        assertTrue(ReadingTypography
                .of(DEFAULT_SIZE_INDEX, SANS, DEFAULT_SPACING_INDEX, true).isJustified());
        assertFalse(ReadingTypography
                .of(DEFAULT_SIZE_INDEX, SANS, DEFAULT_SPACING_INDEX, false).isJustified());
    }

    @Test
    public void testIndicesAreIndependentOfEachOther() {
        // picking one setting must not shift the resolution of the others
        ReadingTypography typography = ReadingTypography.of(5, ReadingTypography.FONT_FAMILY_MONOSPACE,
                4, true);
        assertEquals(24, typography.getFontSizeSp());
        assertEquals(ReadingTypography.FONT_FAMILY_MONOSPACE, typography.getFontFamily());
        assertEquals(1.9f, typography.getLineSpacingMultiplier(), 0f);
        assertTrue(typography.isJustified());
    }

    // ========================================================================
    // out-of-range clamping
    // ========================================================================

    @Test
    public void testFontSizeIndexAboveRangeFallsBackToDefault() {
        ReadingTypography typography = ReadingTypography.of(
                ReadingTypography.FONT_SIZE_SP.length, SANS, DEFAULT_SPACING_INDEX, false);
        assertEquals("one past the end must clamp to the default",
                ReadingTypography.FONT_SIZE_SP[DEFAULT_SIZE_INDEX], typography.getFontSizeSp());
    }

    @Test
    public void testNegativeFontSizeIndexFallsBackToDefault() {
        ReadingTypography typography = ReadingTypography.of(-1, SANS, DEFAULT_SPACING_INDEX, false);
        assertEquals(ReadingTypography.FONT_SIZE_SP[DEFAULT_SIZE_INDEX], typography.getFontSizeSp());
    }

    @Test
    public void testLineSpacingIndexOutOfRangeFallsBackToDefault() {
        assertEquals(1.0f, ReadingTypography
                .of(DEFAULT_SIZE_INDEX, SANS, Integer.MAX_VALUE, false)
                .getLineSpacingMultiplier(), 0f);
        assertEquals(1.0f, ReadingTypography
                .of(DEFAULT_SIZE_INDEX, SANS, -7, false)
                .getLineSpacingMultiplier(), 0f);
    }

    @Test
    public void testFontFamilyOutOfRangeFallsBackToSans() {
        assertEquals(ReadingTypography.FONT_FAMILY_SANS,
                ReadingTypography.of(DEFAULT_SIZE_INDEX, 3, DEFAULT_SPACING_INDEX, false)
                        .getFontFamily());
        assertEquals(ReadingTypography.FONT_FAMILY_SANS,
                ReadingTypography.of(DEFAULT_SIZE_INDEX, -2, DEFAULT_SPACING_INDEX, false)
                        .getFontFamily());
    }

    @Test
    public void testClampingDoesNotDiscardTheJustificationFlag() {
        // clamping repairs only the indices it owns; a boolean that arrived true
        // must not be silently reset to the default
        assertTrue(ReadingTypography
                .of(99, 99, 99, true).isJustified());
    }

    // ========================================================================
    // title scaling
    // ========================================================================

    @Test
    public void testTitleSpAtDefaultSizeIsTheHardcodedHeading() {
        assertEquals(ReadingTypography.BASE_TITLE_SP, ReadingTypography
                .of(DEFAULT_SIZE_INDEX, SANS, DEFAULT_SPACING_INDEX, false)
                .getTitleSp(), 0f);
    }

    @Test
    public void testTitleSpScalesWithBodySizeAtEveryStep() {
        int[] sizes = ReadingTypography.FONT_SIZE_SP;
        float baselineBody = sizes[DEFAULT_SIZE_INDEX];
        for (int index = 0; index < sizes.length; index++) {
            float expectedTitle = ReadingTypography.BASE_TITLE_SP * sizes[index] / baselineBody;
            assertEquals("index " + index + " title must scale with the body",
                    expectedTitle, ReadingTypography
                            .of(index, SANS, DEFAULT_SPACING_INDEX, false)
                            .getTitleSp(), 0.0001f);
        }
    }

    @Test
    public void testTitleToBodyRatioStaysFourToThreeAtSmallestAndLargestStep() {
        // the 24sp heading over the 18sp body is a 4:3 relationship; applying the
        // same scale factor must preserve it at both ends of the range
        assertTitleToBodyRatio(0);
        assertTitleToBodyRatio(ReadingTypography.FONT_SIZE_SP.length - 1);
    }

    @Test
    public void testTitleNeverCollapsesOntoTheBodySize() {
        // picking the largest body must not make the heading the same size as the
        // body: that would destroy the visual hierarchy the setting exists to keep
        int largestIndex = ReadingTypography.FONT_SIZE_SP.length - 1;
        ReadingTypography typography = ReadingTypography.of(largestIndex, SANS,
                DEFAULT_SPACING_INDEX, false);
        assertTrue("title must stay strictly larger than the body",
                typography.getTitleSp() > typography.getFontSizeSp());
    }

    private static void assertTitleToBodyRatio(int fontSizeIndex) {
        ReadingTypography typography = ReadingTypography.of(fontSizeIndex, SANS,
                DEFAULT_SPACING_INDEX, false);
        float expectedRatio = ReadingTypography.BASE_TITLE_SP
                / (float) ReadingTypography.FONT_SIZE_SP[DEFAULT_SIZE_INDEX];
        float actualRatio = typography.getTitleSp() / typography.getFontSizeSp();
        assertEquals("index " + fontSizeIndex + " must preserve the "
                        + ReadingTypography.BASE_TITLE_SP + ":"
                        + ReadingTypography.FONT_SIZE_SP[DEFAULT_SIZE_INDEX] + " ratio",
                expectedRatio, actualRatio, 0.0001f);
    }
}