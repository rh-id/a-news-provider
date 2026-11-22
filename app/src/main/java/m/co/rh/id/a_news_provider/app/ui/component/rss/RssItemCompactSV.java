package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.widget.TextView;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.ui.model.RssItemModel;

/**
 * The COMPACT list row layout variant (issue #58): a single-line, ellipsized title
 * with a leading unread dot, no date, no category chips and no description - the
 * densest layout for scanning many headlines.
 * <p>
 * Shared row behavior (RssItem stream + mapping, self-heal, read/favorite toggles,
 * tap-to-detail, long-press read toggle, duplicate-story badge, TalkBack state
 * description + custom actions, dispose contract) lives in {@link BaseRssItemSV};
 * this class only binds the visuals:
 * <ul>
 *     <li>the unread dot is shown while the item is unread (its spoken form stays
 *     the row's shared state description - the dot's own content description is the
 *     explicit "unread indicator" label, not a second state announcement),</li>
 *     <li>the title keeps the app-wide bold-when-unread convention in single-line
 *     form,</li>
 *     <li>the duplicate-story badge is kept inline (collapseDuplicateStories defaults
 *     ON, so the badge is the row's only duplicate affordance - parity decision of
 *     issue #58), including the badge's copies expansion through the shared
 *     machinery.</li>
 * </ul>
 * Swipe actions, mark-read-on-tap and the long-press read toggle come from the base
 * unchanged: the swipe helper gates rows via the shared view-holder contract and the
 * root id is {@code root_layout} like every other variant.
 */
public class RssItemCompactSV extends BaseRssItemSV {

    private transient TextView mTextTitle;
    private transient View mViewUnreadDot;

    public RssItemCompactSV() {
        super();
    }

    @Override
    protected int provideLayoutRes() {
        return R.layout.view_rss_item_compact;
    }

    @Override
    protected void onRowViewCreated(Activity activity, View rowView) {
        mTextTitle = rowView.findViewById(R.id.text_title);
        mViewUnreadDot = rowView.findViewById(R.id.view_unread_dot);
    }

    @Override
    protected void bindRowView(Activity activity, View rowView, RssItemModel rssItemModel) {
        // the dot is the compact layout's at-a-glance unread signal; visibility is
        // set on every bind so a recycled view never shows the previous item's dot
        mViewUnreadDot.setVisibility(rssItemModel.isRead ? View.GONE : View.VISIBLE);
        mTextTitle.setText(rssItemModel.title);
        mTextTitle.setTypeface(rssItemModel.isRead ?
                Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        mTextTitle = null;
        mViewUnreadDot = null;
    }
}
