package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.Disposable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.provider.component.AppSharedPreferences;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;

/**
 * Facade over the rss item list adapter, composed as a {@link ConcatAdapter}:
 * <ul>
 *     <li>{@link RssItemsListAdapter} - the real items, a {@link ListAdapter} so list
 *     updates go through {@link DiffUtil} and only changed rows are rebound/moved instead
 *     of the previous full-range {@code notifyItemRangeChanged(0, n)} rebinds. Its row
 *     view type is the "List layout" preference (issue #58): 0 = {@link RssItemSV}
 *     standard rows (default), 1 = {@link RssItemCompactSV}, 2 = {@link RssItemCardSV}.</li>
 *     <li>{@link EmptyStateAdapter} - renders the single {@code no_record} row when the
 *     list is empty. The empty row must NOT be a fake position inside the items adapter:
 *     {@code ListAdapter}'s dispatch ops are computed against the differ's list size, so
 *     an overridden {@code getItemCount()} that disagrees with that size is the classic
 *     "Inconsistency detected: invalid item position" RecyclerView crash. A ConcatAdapter
 *     child with its own (honest) count keeps both invariants while rendering exactly the
 *     same single empty row as before, at the same first/only position.</li>
 * </ul>
 * <p>This class is a facade (not an Adapter subclass; ConcatAdapter is final) keeping the
 * previous public surface: construct once, hand {@link #getConcatAdapter()} to
 * {@code RecyclerView#setAdapter}, push new list snapshots via {@link #submitItems(List)}
 * whenever the source command re-emits, and call {@link #dispose(Activity)} on destroy.
 * Rows are still built through {@code mNavigator.injectRequired(...)} +
 * {@code buildView(...)}; the facade only owns their lifecycle disposal and the LIVE
 * layout switch.
 * <p>The live switch (issue #58): the facade subscribes to the layout
 * {@code Flowable} once (dispose() tears the subscription down with the owning page) and
 * on a real change re-emits {@code notifyDataSetChanged} on the items adapter. That single
 * mechanism is sufficient everywhere: ConcatAdapter forwards child notifies, RecyclerView
 * keeps per-view-type recycle pools so the stale variant's rows are recycled out, and the
 * swipe directions are re-queried through
 * {@code RssItemSwipeHelperCallback#getSwipeDirs} on every drag. Both call sites (home
 * list and search results) share this facade, so the preference applies to both lists
 * automatically. The initial emission is a value-equality no-op: the facade reads the
 * initial value via the getter BEFORE subscribing, so a replayed value never triggers a
 * pointless rebind (a skip(1) would be racey if the user toggles the setting between the
 * getter read and the subscription).
 * <p>{@link #submitItems(List)} must be called on the main thread, same as the previous
 * notify* based version.
 */
public class RssItemRecyclerViewAdapter {
    private final ConcatAdapter mConcatAdapter;
    private final RssItemsListAdapter mItemsAdapter;
    private final EmptyStateAdapter mEmptyStateAdapter;
    private final Disposable mListLayoutDisposable;

    /**
     * Current row layout (a "List layout" pref value: standard 0 / compact 1 /
     * card 2). Read by the items adapter's {@code getItemViewType} and updated
     * only by the subscription below on the main thread. Kept on the facade (not
     * the inner adapter) so there is exactly one source of truth.
     */
    private int mListLayout;

    @SuppressWarnings("rawtypes") // StatefulView is used raw across the codebase
    public RssItemRecyclerViewAdapter(INavigator navigator, StatefulView parentStatefulView,
                                      AppSharedPreferences appSharedPreferences) {
        mListLayout = appSharedPreferences.getListLayout();
        mItemsAdapter = new RssItemsListAdapter(navigator, parentStatefulView);
        mEmptyStateAdapter = new EmptyStateAdapter();
        // ConcatAdapter default isolated view-type mode keeps the empty row's view type
        // disjoint from the item rows', so StaggeredGridLayoutManager (landscape, 2
        // columns) treats them exactly like the previous single-adapter view types
        mConcatAdapter = new ConcatAdapter(mItemsAdapter, mEmptyStateAdapter);
        mListLayoutDisposable = appSharedPreferences.getListLayoutFlow()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(layout -> {
                    if (layout != null && layout != mListLayout) {
                        mListLayout = layout;
                        mItemsAdapter.notifyDataSetChanged();
                    }
                });
    }

    /**
     * @return the composed adapter to pass to {@code RecyclerView#setAdapter}
     */
    public ConcatAdapter getConcatAdapter() {
        return mConcatAdapter;
    }

    /**
     * Submits a new snapshot of the rss items; DiffUtil diffs it against the previously
     * submitted list off the main thread and dispatches only the minimal insert/remove/
     * move/change ops. The empty row is toggled in {@code submitList}'s commit callback,
     * i.e. exactly when the diff lands on the main thread, so the empty state never
     * flickers out of (or into) sync with the items during the async diff. A superseded
     * submission never runs its callback, so only the latest submitted list decides the
     * empty-state visibility.
     * <p>The incoming list receives a defensive copy because it is typically a
     * {@code BehaviorSubject} payload owned (and possibly mutated on reload) by the
     * command; AsyncListDiffer must never observe a list mutated after submission.
     *
     * @param rssItems the new full list of items, may be null (treated as empty)
     */
    public void submitItems(List<RssItem> rssItems) {
        List<RssItem> items = rssItems == null ? new ArrayList<>() : new ArrayList<>(rssItems);
        mItemsAdapter.submitList(items,
                () -> mEmptyStateAdapter.setVisible(items.isEmpty()));
    }

    /**
     * Disposes every row StatefulView created for a row and the live layout-switch
     * subscription. RecyclerView only detaches views on destroy; the
     * navigator-injected StatefulViews must be disposed explicitly so their
     * providers/subscriptions are torn down.
     *
     * @param activity the activity being destroyed
     */
    public void dispose(Activity activity) {
        if (mListLayoutDisposable != null && !mListLayoutDisposable.isDisposed()) {
            mListLayoutDisposable.dispose();
        }
        mItemsAdapter.disposeSvs(activity);
    }

    /**
     * Notifies the items adapter that the row held by the given ViewHolder has changed
     * and must be rebound. Required by the ItemTouchHelper contract
     * ({@code RssItemSwipeHelperCallback}): after {@code onSwiped} the helper keeps the
     * swiped child translated off-screen until the adapter announces a change - without
     * this notification the row would stay invisible as a permanent blank slot.
     * {@code notifyItemChanged} rebinds the row with the ALREADY synchronously mutated
     * {@link RssItem} instance (the update commands flip the flag on the calling thread
     * before persisting in the background, and {@link #submitItems(List)} shares the
     * element references), which is the ONLY reliable read-state refresh here:
     * {@code UpdateRssItemIsReadCmd} deliberately emits no {@code RssChangeNotifier}
     * event, so nothing else would redraw the row.
     *
     * @param holder the ViewHolder that was swiped (or whose state was reverted)
     */
    void notifyItemChangedByHolder(RecyclerView.ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION) {
            mItemsAdapter.notifyItemChanged(position);
        }
    }

    /**
     * The items rows. {@code getItemCount()} is exactly {@code getCurrentList().size()}
     * as required by ListAdapter's differ-dispatched operations; the empty row lives in
     * {@link EmptyStateAdapter} instead of a fake position here.
     * <p>Deliberately an INNER (non-static) class: {@code getItemViewType} must read the
     * facade's live {@code mListLayout} pref value, and the facade is the single source
     * of truth for it.
     */
    private class RssItemsListAdapter extends ListAdapter<RssItem, RssItemViewHolder> {
        /**
         * Identity for DiffUtil: rows are the same article across reloads when their
         * primary key matches, so read/favorite toggles rebind in place (and keep their
         * scroll position) instead of remove+insert.
         */
        private static final DiffUtil.ItemCallback<RssItem> DIFF_CALLBACK =
                new DiffUtil.ItemCallback<RssItem>() {
                    @Override
                    public boolean areItemsTheSame(@NonNull RssItem oldItem,
                                                   @NonNull RssItem newItem) {
                        return Objects.equals(oldItem.id, newItem.id);
                    }

                    /**
                     * Content equality over every field the UI may render or carry into
                     * the detail page on row tap: title, link, description, pubDate (with
                     * createdDateTime as the displayed-date fallback in the row SVs),
                     * mediaImage (the card layout's thumbnail, issue #58), mediaVideo,
                     * categories, isRead (bold/unread dot) and isFavorite (star) and
                     * duplicateCount (the duplicate-story badge, issue #45).
                     * {@code updatedDateTime} is deliberately excluded: it is bumped on
                     * every DB write (mark-read, favorite toggle) without ever being
                     * rendered, so comparing it would rebind every touched row and defeat
                     * the purpose of diffing. {@code channelId} is likewise excluded: the
                     * row's {@code onClick} consumes it to look up the channel feeding
                     * the detail-page args, but it is immutable for a given {@code id}
                     * (a channel change deletes and re-inserts the row, producing a new
                     * id), so two rows sharing that id can never disagree on it and it
                     * can never go stale.
                     */
                    @Override
                    public boolean areContentsTheSame(@NonNull RssItem oldItem,
                                                      @NonNull RssItem newItem) {
                        return Objects.equals(oldItem.title, newItem.title)
                                && Objects.equals(oldItem.link, newItem.link)
                                && Objects.equals(oldItem.description, newItem.description)
                                && Objects.equals(oldItem.pubDate, newItem.pubDate)
                                && Objects.equals(oldItem.createdDateTime, newItem.createdDateTime)
                                && Objects.equals(oldItem.mediaImage, newItem.mediaImage)
                                && Objects.equals(oldItem.mediaVideo, newItem.mediaVideo)
                                && Objects.equals(oldItem.categories, newItem.categories)
                                && oldItem.isRead == newItem.isRead
                                && oldItem.isFavorite == newItem.isFavorite
                                && oldItem.duplicateCount == newItem.duplicateCount;
                    }
                };

        private final INavigator mNavigator;
        // StatefulView is used raw across the codebase, so the parent handle and the
        // created-row list are intentionally raw; only rawtypes is suppressed here so
        // unchecked warnings elsewhere in the class stay visible
        @SuppressWarnings("rawtypes")
        private final StatefulView mParentStatefulView;
        @SuppressWarnings("rawtypes")
        private final List<StatefulView> mCreatedSvs;

        @SuppressWarnings("rawtypes") // StatefulView is used raw across the codebase
        RssItemsListAdapter(INavigator navigator, StatefulView parentStatefulView) {
            super(DIFF_CALLBACK);
            mNavigator = navigator;
            mParentStatefulView = parentStatefulView;
            mCreatedSvs = new ArrayList<>();
        }

        /**
         * The row layout is the "List layout" pref (issue #58): the three values map
         * 1:1 onto the three row view types. Every variant is created + injected via
         * {@code mNavigator.injectRequired} exactly like the former single
         * {@code RssItemSV} path and tracked in {@code mCreatedSvs} for disposal.
         */
        @NonNull
        @Override
        public RssItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Activity activity = UiUtils.getActivity(parent);
            BaseRssItemSV rowSv;
            if (viewType == AppSharedPreferences.LIST_LAYOUT_COMPACT) {
                rowSv = new RssItemCompactSV();
            } else if (viewType == AppSharedPreferences.LIST_LAYOUT_CARD) {
                rowSv = new RssItemCardSV();
            } else {
                rowSv = new RssItemSV();
            }
            mNavigator.injectRequired(mParentStatefulView, rowSv);
            mCreatedSvs.add(rowSv);
            View view = rowSv.buildView(activity, parent);
            return new RssItemViewHolder(view, rowSv);
        }

        @Override
        public void onBindViewHolder(@NonNull RssItemViewHolder holder, int position) {
            holder.setRssItem(getItem(position));
        }

        @Override
        public int getItemViewType(int position) {
            return mListLayout;
        }

        /**
         * Disposes every row StatefulView created in
         * {@link #onCreateViewHolder(ViewGroup, int)}. RecyclerView only detaches views
         * on destroy; the navigator-injected StatefulViews must be disposed explicitly
         * so their providers/subscriptions are torn down.
         *
         * @param activity the activity being destroyed
         */
        @SuppressWarnings({"unchecked", "rawtypes"}) // StatefulView is used raw across the codebase
        void disposeSvs(Activity activity) {
            if (!mCreatedSvs.isEmpty()) {
                for (StatefulView sv : mCreatedSvs) {
                    sv.dispose(activity);
                }
                mCreatedSvs.clear();
            }
        }
    }

    /**
     * Renders the single {@code no_record} row while the list is empty, mirroring the
     * legacy adapter's empty behavior: the row is present from the moment the adapter is
     * attached (legacy {@code getItemCount()} returned 1 when empty) and is toggled with
     * matching insert/remove ops so counts never go negative on double calls.
     */
    private static class EmptyStateAdapter extends RecyclerView.Adapter<EmptyViewHolder> {
        private boolean mVisible = true;

        @NonNull
        @Override
        public EmptyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = UiUtils.getActivity(parent).getLayoutInflater()
                    .inflate(R.layout.no_record, parent, false);
            return new EmptyViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull EmptyViewHolder holder, int position) {
            // Static "no record" text; nothing to bind
        }

        @Override
        public int getItemCount() {
            return mVisible ? 1 : 0;
        }

        /**
         * Shows or hides the empty row with a matching insert/remove notification.
         *
         * @param visible true to show the single {@code no_record} row
         */
        void setVisible(boolean visible) {
            if (mVisible == visible) {
                return;
            }
            mVisible = visible;
            if (visible) {
                notifyItemInserted(0);
            } else {
                notifyItemRemoved(0);
            }
        }
    }

    protected static class RssItemViewHolder extends RecyclerView.ViewHolder {
        private final BaseRssItemSV mRssItemSV;

        public RssItemViewHolder(@NonNull View itemView, @NonNull BaseRssItemSV rssItemSV) {
            super(itemView);
            mRssItemSV = rssItemSV;
        }

        void setRssItem(RssItem rssItem) {
            mRssItemSV.setRssItem(rssItem);
        }

        /**
         * Read access to the currently bound rss item for the swipe callback
         * ({@code RssItemSwipeHelperCallback}), which needs the row's in-memory
         * state to flip the read/favorite flag on.
         *
         * @return the currently bound rss item, or null before the first bind
         */
        RssItem getRssItem() {
            return mRssItemSV.getRssItem();
        }
    }

    protected static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
