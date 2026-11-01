package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsFavoriteCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsReadCmd;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.alogger.ILogger;

/**
 * ItemTouchHelper callback adding swipe actions to the rss item list rows (issue #32):
 * <ul>
 *     <li>swipe RIGHT - toggles read/unread (green background + check icon),</li>
 *     <li>swipe LEFT - toggles favorite (orange background + star icon).</li>
 * </ul>
 * While the user drags, a colored background plus a per-direction icon are drawn behind
 * the row (see {@link #onChildDraw}): each direction carries its own background color and
 * icon pair, mirroring the Compose SwipeToDismissBox convention where every direction of
 * a swipe-to-dismiss surface announces its distinct action with a distinct color/icon so
 * the user can read the pending action BEFORE releasing the row.
 * <p>On release the matching update command flips the flag synchronously in memory (it
 * persists in the background), {@link RssItemRecyclerViewAdapter#notifyItemChangedByHolder}
 * snaps the row back in place with the new state, and a Snackbar with a single Undo action
 * is shown (anchored on the row view, which walks up to the outer CoordinatorLayout like
 * the HomePage convention - the FAB is displaced while the Snackbar is visible). Undo
 * re-executes the SAME command with the reverted value; the READ undo additionally
 * reloads the paged list because {@code UpdateRssItemIsReadCmd} emits no
 * {@code RssChangeNotifier} event, so nothing else would refresh the window. That reload
 * is enqueued from INSIDE the undo write's executor task (the command's post-write hook),
 * mirroring the favorite command's write-then-notify idiom, so it can never observe
 * pre-undo data - as two independent tasks on the multi-threaded pool the reload could
 * read the pre-undo snapshot (Room/WAL) and drop the un-done row from the filtered
 * window. The FAVORITE undo needs no reload - {@code UpdateRssItemIsFavoriteCmd} emits
 * {@code updatedRssItem} which self-heals visible rows and the favorites-filter reload.
 * <p>Swipes are only offered to real item rows ({@link RssItemRecyclerViewAdapter.RssItemViewHolder})
 * while the feature is enabled in settings; the empty-state row and every other
 * ViewHolder get no swipe dirs at all. Instances are built once per list view
 * (see {@code RssItemListSV}) and attached with {@code ItemTouchHelper#attachToRecyclerView}.
 */
public class RssItemSwipeHelperCallback extends ItemTouchHelper.SimpleCallback {
    private static final String TAG = RssItemSwipeHelperCallback.class.getName();

    /**
     * Inset between the row's leading/trailing edge and the swipe icon, matching the
     * Material list gutter (16dp) so the revealed icon aligns with the row's own
     * content margins.
     */
    private static final float ICON_INSET_DP = 16f;

    private final Activity mActivity;
    private final AppSharedPreferences mAppSharedPreferences;
    private final UpdateRssItemIsReadCmd mUpdateRssItemIsReadCmd;
    private final UpdateRssItemIsFavoriteCmd mUpdateRssItemIsFavoriteCmd;
    private final RssItemRecyclerViewAdapter mRssItemRecyclerViewAdapter;
    private final PagedRssItemsCmd mPagedRssItemsCmd;
    private final ILogger mLogger;

    /**
     * Single reusable paint for the swipe background rect; color and alpha are
     * re-applied on every {@link #onChildDraw} frame (alpha ramps with the drag
     * distance so the revealed action fades in as the row is pulled).
     */
    private final Paint mBackgroundPaint;

    /**
     * Cached swipe icons: check (read action) and filled star (favorite action),
     * mutated at construction so the per-frame alpha ramp cannot leak into other
     * drawables sharing the same resources constant state.
     */
    private final Drawable mIconCheck;
    private final Drawable mIconStar;

    /**
     * Pixel value of {@link #ICON_INSET_DP}, resolved once against the activity density.
     */
    private final int mIconInsetPx;

    public RssItemSwipeHelperCallback(Activity activity,
                                      AppSharedPreferences appSharedPreferences,
                                      UpdateRssItemIsReadCmd updateRssItemIsReadCmd,
                                      UpdateRssItemIsFavoriteCmd updateRssItemIsFavoriteCmd,
                                      RssItemRecyclerViewAdapter rssItemRecyclerViewAdapter,
                                      PagedRssItemsCmd pagedRssItemsCmd,
                                      ILogger logger) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        mActivity = activity;
        mAppSharedPreferences = appSharedPreferences;
        mUpdateRssItemIsReadCmd = updateRssItemIsReadCmd;
        mUpdateRssItemIsFavoriteCmd = updateRssItemIsFavoriteCmd;
        mRssItemRecyclerViewAdapter = rssItemRecyclerViewAdapter;
        mPagedRssItemsCmd = pagedRssItemsCmd;
        mLogger = logger;
        mBackgroundPaint = new Paint();
        mIconCheck = ContextCompat.getDrawable(activity, R.drawable.ic_check_white);
        mIconStar = ContextCompat.getDrawable(activity, R.drawable.ic_star_filled_white);
        if (mIconCheck != null) {
            mIconCheck.mutate();
        }
        if (mIconStar != null) {
            mIconStar.mutate();
        }
        mIconInsetPx = Math.round(ICON_INSET_DP
                * activity.getResources().getDisplayMetrics().density);
    }

    /**
     * Only real rss item rows may swipe, and only while the feature is enabled:
     * the {@code EmptyStateAdapter}'s no_record row lives in the same ConcatAdapter
     * and must never swipe, and the settings toggle has to disable every row's
     * gesture immediately (the helper re-queries dirs through here before each drag).
     */
    @Override
    public int getSwipeDirs(@NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder) {
        if (viewHolder instanceof RssItemRecyclerViewAdapter.RssItemViewHolder
                && mAppSharedPreferences.isSwipeRowActionsEnabled()) {
            return super.getSwipeDirs(recyclerView, viewHolder);
        }
        return 0;
    }

    /**
     * No drag support (dragDirs = 0): this callback handles swipes only, so this
     * abstract requirement can never be reached.
     */
    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView,
                          @NonNull RecyclerView.ViewHolder viewHolder,
                          @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    /**
     * Commits the swipe: flips the in-memory flag via the matching update command,
     * notifies the adapter (ItemTouchHelper contract - without this the swiped row
     * stays translated off-screen as a blank slot) and shows the Undo Snackbar.
     * Link-less items are skipped entirely: both update commands persist by link
     * (mirroring the {@code MarkItemsReadOnScrollCmd} guard), so a link-less row
     * would only flip in memory and silently revert on the next reload - a ghost
     * state worse than no action.
     *
     * @param viewHolder the swiped row's ViewHolder
     * @param direction  {@link ItemTouchHelper#RIGHT} for read/unread,
     *                   {@link ItemTouchHelper#LEFT} for favorite
     */
    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        if (!(viewHolder instanceof RssItemRecyclerViewAdapter.RssItemViewHolder)) {
            return;
        }
        RssItem rssItem = ((RssItemRecyclerViewAdapter.RssItemViewHolder) viewHolder)
                .getRssItem();
        if (rssItem == null) {
            // unbound row: still snap it back, the helper keeps swiped children
            // off-screen until the adapter announces a change
            mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
            return;
        }
        if (rssItem.link == null || rssItem.link.isEmpty()) {
            mLogger.i(TAG, "Skipped swipe action on link-less rss item: " + rssItem.title);
            // snap back WITHOUT any state change or Snackbar - see the guard above
            mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
            return;
        }
        if (direction == ItemTouchHelper.RIGHT) {
            boolean newIsRead = !rssItem.isRead;
            mUpdateRssItemIsReadCmd.execute(rssItem, newIsRead);
            mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
            Snackbar.make(viewHolder.itemView,
                            mActivity.getString(newIsRead ?
                                    R.string.mark_as_read : R.string.mark_as_unread),
                            Snackbar.LENGTH_SHORT)
                    .setAction(R.string.undo, v -> {
                        // revert in memory + persist, then rebind the row immediately
                        // (main thread, in-memory only); the reload is passed as the
                        // command's post-write hook so it is enqueued INSIDE the
                        // write's executor task, after the DB commit - it can never
                        // read the pre-undo snapshot (read changes item visibility,
                        // e.g. under the unread filter)
                        mUpdateRssItemIsReadCmd.execute(rssItem, !newIsRead,
                                () -> mPagedRssItemsCmd.load());
                        mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
                    })
                    .show();
        } else if (direction == ItemTouchHelper.LEFT) {
            boolean newIsFavorite = !rssItem.isFavorite;
            mUpdateRssItemIsFavoriteCmd.execute(rssItem, newIsFavorite);
            mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
            Snackbar.make(viewHolder.itemView,
                            mActivity.getString(newIsFavorite ?
                                    R.string.favorite_add : R.string.favorite_remove),
                            Snackbar.LENGTH_SHORT)
                    .setAction(R.string.undo, v -> {
                        // revert in memory + persist and rebind immediately; no reload
                        // needed - updatedRssItem events self-heal the rows and the
                        // favorites filter
                        mUpdateRssItemIsFavoriteCmd.execute(rssItem, !newIsFavorite);
                        mRssItemRecyclerViewAdapter.notifyItemChangedByHolder(viewHolder);
                    })
                    .show();
        }
    }

    /**
     * Draws the swipe feedback UNDER the row (RecyclerView draws this decoration
     * before the children): a full-bounds background rect in the direction's action
     * color (green = read, orange = favorite, the Compose SwipeToDismissBox
     * per-direction-color convention) plus the direction's icon at the revealed
     * fixed edge - for a right swipe the icon sits near the row's start edge (inset
     * {@link #ICON_INSET_DP}, vertically centered, left of the moving divider),
     * mirrored for a left swipe. Both rect and icon fade in with the drag distance.
     * Must run BEFORE {@code super.onChildDraw}, which applies the row translation -
     * the opaque card then covers its own share of the background, leaving exactly
     * the revealed strip visible.
     */
    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0) {
            View itemView = viewHolder.itemView;
            float alpha = Math.min(1f, Math.abs(dX) / itemView.getWidth());
            mBackgroundPaint.setColor(mActivity.getResources().getColor(dX > 0 ?
                    R.color.green_500 : R.color.orange_600));
            mBackgroundPaint.setAlpha((int) (alpha * 255));
            c.drawRect(itemView.getLeft(), itemView.getTop(),
                    itemView.getRight(), itemView.getBottom(), mBackgroundPaint);
            Drawable icon = dX > 0 ? mIconCheck : mIconStar;
            if (icon != null) {
                int iconSize = icon.getIntrinsicWidth();
                int iconTop = itemView.getTop() + (itemView.getHeight() - iconSize) / 2;
                int iconLeft;
                if (dX > 0) {
                    iconLeft = itemView.getLeft() + mIconInsetPx;
                } else {
                    iconLeft = itemView.getRight() - mIconInsetPx - iconSize;
                }
                icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
                icon.setAlpha((int) (alpha * 255));
                icon.draw(c);
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    // getSwipeThreshold: left at the ItemTouchHelper default (0.5f of the row width) -
    // committing only past the halfway point feels deliberate and prevents accidental
    // triggers while scrolling; lower it to ~0.35f only if usability testing says so.
}
