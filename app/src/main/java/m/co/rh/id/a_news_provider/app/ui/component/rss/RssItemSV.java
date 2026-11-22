package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import com.google.android.material.chip.Chip;

import java.util.List;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.ui.model.RssItemModel;
import m.co.rh.id.a_news_provider.app.ui.page.SearchRssPage;

/**
 * The STANDARD list row layout of an rss item (the layout every list showed before
 * issue #58 introduced the layout variants): date line, bold 20sp title, favorite
 * star and the horizontally scrollable category chip strip, inside a rounded
 * CardView with an 8dp outer margin.
 * <p>
 * ALL row behavior (RssItem stream + mapping, self-heal, read/favorite toggles,
 * tap-to-detail, long-press read toggle, duplicate-story badge, TalkBack state
 * description + custom actions, dispose contract) lives in the shared base
 * {@link BaseRssItemSV}; this class only contributes the standard visuals: the
 * date/title/chip binding and the category chip strip (chip taps route into the
 * category search page, consumed so they never trigger the row's open-detail
 * click). Row visuals are deliberately unchanged from the pre-extraction
 * {@code RssItemSV} - the description is not rendered, and every id the tests
 * touch ({@code text_date}, {@code text_title}, {@code button_favorite},
 * {@code chip_duplicates}, ...) keeps its meaning.
 */
public class RssItemSV extends BaseRssItemSV {

    /**
     * Uniform gap between category chips in the row strip, applied as marginEnd on
     * every chip (including the last) so spacing never depends on Material's internal
     * chip bounds or the touch-target expansion.
     */
    private static final float CHIP_SPACING_DP = 8f;

    private transient TextView mTextDate;
    private transient TextView mTextTitle;
    private transient HorizontalScrollView mScrollCategories;
    private transient LinearLayout mContainerCategories;

    public RssItemSV() {
        super();
    }

    @Override
    protected int provideLayoutRes() {
        return R.layout.list_item_rss_item;
    }

    @Override
    protected void onRowViewCreated(Activity activity, View rowView) {
        mTextDate = rowView.findViewById(R.id.text_date);
        mTextTitle = rowView.findViewById(R.id.text_title);
        mScrollCategories = rowView.findViewById(R.id.scroll_categories);
        mContainerCategories = rowView.findViewById(R.id.container_categories);
    }

    @Override
    protected void bindRowView(Activity activity, View rowView, RssItemModel rssItemModel) {
        ViewCompat.setTransitionName(mTextTitle, "title_" + rssItemModel.id);
        mTextDate.setText(rssItemModel.pubDate);
        mTextTitle.setText(rssItemModel.title);
        if (rssItemModel.isRead) {
            mTextDate.setTypeface(Typeface.DEFAULT);
            mTextTitle.setTypeface(Typeface.DEFAULT);
        } else {
            mTextDate.setTypeface(Typeface.DEFAULT_BOLD);
            mTextTitle.setTypeface(Typeface.DEFAULT_BOLD);
        }
        bindCategoryChips(activity, rssItemModel.categories);
    }

    /**
     * Rebuilds the single-line category chip strip for the given categories.
     * The strip is hidden entirely when the item has no categories; chip taps are
     * consumed so they never trigger the row's open-detail click.
     *
     * @param activity   the current activity, used to inflate the chips
     * @param categories the item's category terms, may be null or empty
     */
    private void bindCategoryChips(Activity activity, List<String> categories) {
        mContainerCategories.removeAllViews();
        if (categories == null || categories.isEmpty()) {
            mScrollCategories.setVisibility(View.GONE);
            return;
        }
        mScrollCategories.setVisibility(View.VISIBLE);
        for (String category : categories) {
            mContainerCategories.addView(buildCategoryChip(activity, category));
        }
    }

    private Chip buildCategoryChip(Activity activity, String category) {
        Chip chip = new Chip(activity);
        chip.setText(category);
        chip.setCheckable(false);
        // consume the tap: the row root has click and long-click listeners, the chip
        // must not trigger the row's open-detail navigation
        chip.setClickable(true);
        chip.setFocusable(true);
        // 48dp TOUCH target via Material's touch delegate; the visual pill keeps the
        // ~32dp Material default height (text stays at the 14sp chip default)
        chip.setEnsureMinTouchTargetSize(true);
        // deterministic, uniform 8dp gap on every chip (including the last) - the
        // perceived spacing must not depend on Material's internal chip bounds
        int chipSpacingPx = (int) (CHIP_SPACING_DP
                * activity.getResources().getDisplayMetrics().density + 0.5f);
        LinearLayout.LayoutParams chipLayoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLayoutParams.setMarginEnd(chipSpacingPx);
        chip.setLayoutParams(chipLayoutParams);
        chip.setContentDescription(
                activity.getString(R.string.search_articles_in_category, category));
        chip.setOnClickListener(v -> mNavigator.push(Routes.SEARCH_RSS_PAGE,
                SearchRssPage.Args.withSearchTerm(category), null, null));
        return chip;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mTextDate = null;
        mTextTitle = null;
        mScrollCategories = null;
        mContainerCategories = null;
    }
}
