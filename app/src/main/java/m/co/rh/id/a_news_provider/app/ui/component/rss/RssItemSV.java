package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.text.HtmlCompat;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import co.rh.id.lib.rx3_utils.subject.SerialBehaviorSubject;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsFavoriteCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsReadCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.repository.RssRepository;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.model.RssItemModel;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.app.ui.page.SearchRssPage;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.entity.RssItemCategories;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

public class RssItemSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = RssItemSV.class.getName();

    /**
     * Uniform gap between category chips in the row strip, applied as marginEnd on
     * every chip (including the last) so spacing never depends on Material's internal
     * chip bounds or the touch-target expansion.
     */
    private static final float CHIP_SPACING_DP = 8f;

    /**
     * RxDisposer key of the duplicate-copies load (issue #45). Repeated expansions
     * reuse the same key, so a tap while a load is in flight replaces the previous
     * subscription instead of stacking stale renders.
     */
    private static final String KEY_LOAD_OTHER_COPIES = "loadOtherCopies";

    /**
     * The text of the duplicate badge: the multiplication sign followed by the
     * absolute copy count. Kept as a literal format (not a resource) on purpose:
     * U+00D7 is script-neutral, the count renders through the platform's locale
     * digit handling via String.format, and only the spoken form needs
     * translation (the also_in_n_feeds plural carries it).
     */
    private static final String DUPLICATE_BADGE_FORMAT = "×%d";

    /**
     * Date rendering of the copies expansion lines - identical pattern to the
     * row's own date so the two never disagree visually.
     */
    private static final String COPY_DATE_FORMAT = "E, d MMM yyyy";

    private transient INavigator mNavigator;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient RxDisposer mRxDisposer;
    private transient RssChangeNotifier mRssChangeNotifier;
    private transient RssQueryCmd mRssQueryCmd;
    private transient UpdateRssItemIsReadCmd mUpdateRssItemIsReadCmd;
    private transient UpdateRssItemIsFavoriteCmd mUpdateRssItemIsFavoriteCmd;
    private transient RssRepository mRssRepository;
    private transient Chip mChipDuplicates;
    private transient ViewGroup mContainerDuplicates;
    private transient ViewGroup mContainerCopies;

    /**
     * Ids of the items whose sources expansion is currently open. Keyed by item id
     * (NOT held as a boolean on the view) because RecyclerView recycles row views:
     * the same RssItemSV instance renders different items over its lifetime, and
     * an un-keyed flag would leak one item's expansion state onto the recycled
     * item bound next.
     */
    private final Set<Long> mExpandedItemIds;

    private SerialBehaviorSubject<RssItem> mRssItemSubject;
    private transient RouteOptions mGetRssChannelByIdAndOpenDetail_routeOptions;
    private transient Observable<RssItemModel> mRssItemModelObservable;
    private transient int mMarkReadUnreadAccessibilityActionId;
    private transient int mAddRemoveFavoriteAccessibilityActionId;

    public RssItemSV() {
        mRssItemSubject = new SerialBehaviorSubject<>();
        mExpandedItemIds = new HashSet<>();
    }

    @Override
    public void provideNavigator(INavigator navigator) {
        mNavigator = navigator;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mRssChangeNotifier = mSvProvider.get(RssChangeNotifier.class);
        mRssQueryCmd = mSvProvider.get(RssQueryCmd.class);
        mUpdateRssItemIsReadCmd = mSvProvider.get(UpdateRssItemIsReadCmd.class);
        mUpdateRssItemIsFavoriteCmd = mSvProvider.get(UpdateRssItemIsFavoriteCmd.class);
        // app-scoped repository (the provideComponent parameter is the global
        // provider for injected row components) - backs the duplicate-copies
        // expansion, whose lookups must never run on the main thread
        mRssRepository = provider.get(RssRepository.class);
        mRssItemModelObservable = mRssItemSubject.getSubject().map(
                rssItem -> {
                    RssItemModel rssItemModel = new RssItemModel();
                    rssItemModel.id = rssItem.id;
                    SimpleDateFormat dateFormat = new SimpleDateFormat("E, d MMM yyyy");
                    if (rssItem.pubDate != null) {
                        rssItemModel.pubDate = dateFormat.format(rssItem.pubDate);
                    } else if (rssItem.createdDateTime != null) {
                        rssItemModel.pubDate = dateFormat.format(rssItem.createdDateTime);
                    }
                    rssItemModel.title = HtmlCompat
                            .fromHtml(rssItem.title, HtmlCompat.FROM_HTML_MODE_COMPACT);
                    rssItemModel.categories = RssItemCategories.parse(rssItem.categories);
                    rssItemModel.isRead = rssItem.isRead;
                    rssItemModel.isFavorite = rssItem.isFavorite;
                    rssItemModel.duplicateCount = rssItem.duplicateCount;

                    return rssItemModel;
                }
        );
        // shared with the detail page's next/previous replace so both open identically
        mGetRssChannelByIdAndOpenDetail_routeOptions =
                RssItemDetailPage.detailPageRouteOptions();
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(R.layout.list_item_rss_item, container, false);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        setupRowAccessibilityDelegate(view);
        TextView textDate = view.findViewById(R.id.text_date);
        TextView textTitle = view.findViewById(R.id.text_title);
        ImageButton buttonFavorite = view.findViewById(R.id.button_favorite);
        HorizontalScrollView scrollCategories = view.findViewById(R.id.scroll_categories);
        LinearLayout containerCategories = view.findViewById(R.id.container_categories);
        mContainerDuplicates = view.findViewById(R.id.container_duplicates);
        mChipDuplicates = view.findViewById(R.id.chip_duplicates);
        mContainerCopies = view.findViewById(R.id.container_copies);
        mChipDuplicates.setOnClickListener(v -> toggleCopiesExpansion(activity));
        buttonFavorite.setOnClickListener(v -> toggleFavorite());
        ViewCompat.setAccessibilityDelegate(buttonFavorite, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                RssItem rssItem = mRssItemSubject.getValue();
                // expose as toggle so that screen reader announces checked/unchecked on all API levels
                info.setCheckable(true);
                info.setChecked(rssItem != null && rssItem.isFavorite);
            }
        });
        addReadAction(view);
        addFavoriteAction(view);
        mRxDisposer.add("mRssItemSubject",
                mRssItemModelObservable
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItemModel -> {
                            ViewCompat.setTransitionName(textTitle, "title_" + rssItemModel.id);
                            textDate.setText(rssItemModel.pubDate);
                            textTitle.setText(rssItemModel.title);
                            if (rssItemModel.isRead) {
                                textDate.setTypeface(Typeface.DEFAULT);
                                textTitle.setTypeface(Typeface.DEFAULT);
                            } else {
                                textDate.setTypeface(Typeface.DEFAULT_BOLD);
                                textTitle.setTypeface(Typeface.DEFAULT_BOLD);
                            }
                            buttonFavorite.setImageResource(rssItemModel.isFavorite ?
                                    R.drawable.ic_star_filled_orange : R.drawable.ic_star_outline_gray);
                            bindCategoryChips(activity, scrollCategories, containerCategories,
                                    rssItemModel.categories);
                            bindDuplicateBadge(activity, rssItemModel);
                            // setStateDescription is ignored below API 30, so there the state
                            // word must live on the row view itself; a view-level content
                            // description change fires its own accessibility event, which the
                            // end-to-end a11y tree (including the node cache) serves reliably
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // state is announced purely via stateDescription, avoid doubling
                                view.setContentDescription(null);
                            } else {
                                view.setContentDescription(mSvProvider.getContext().getString(
                                        rssItemModel.isRead ?
                                                R.string.state_read : R.string.state_unread));
                            }
                            // re-read the focused node(s), checked state and action labels may have changed
                            buttonFavorite.sendAccessibilityEvent(
                                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
                            // action labels are fixed at registration, re-register with state-correct labels
                            addReadAction(view);
                            addFavoriteAction(view);
                        })
        );
        mRxDisposer.add("createView_onRssItemUpdated",
                mRssChangeNotifier.getUpdatedRssItem()
                        .subscribe(rssItem -> {
                            RssItem currentRssItem = mRssItemSubject.getValue();
                            if (currentRssItem != null && rssItem.id.equals(currentRssItem.id)) {
                                mRssItemSubject.onNext(rssItem);
                            }
                        }));
        return view;
    }

    /**
     * Rebuilds the single-line category chip strip for the given categories.
     * The strip is hidden entirely when the item has no categories; chip taps are
     * consumed so they never trigger the row's open-detail click.
     *
     * @param activity            the current activity, used to inflate the chips
     * @param scrollCategories    the strip's scroll container
     * @param containerCategories the container hosting the chips
     * @param categories          the item's category terms, may be null or empty
     */
    private void bindCategoryChips(Activity activity, HorizontalScrollView scrollCategories,
                                   LinearLayout containerCategories, List<String> categories) {
        containerCategories.removeAllViews();
        if (categories == null || categories.isEmpty()) {
            scrollCategories.setVisibility(View.GONE);
            return;
        }
        scrollCategories.setVisibility(View.VISIBLE);
        for (String category : categories) {
            containerCategories.addView(buildCategoryChip(activity, category));
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

    /**
     * Binds the duplicate-story badge (issue #45) for the given model. The badge
     * shows ONLY when the loaded page carried an absolute copy count &gt; 1 for a
     * usable link - every other row keeps the exact pre-feature layout (the whole
     * container collapses). The spoken form is the also_in_n_feeds plural over the
     * OTHER copies (count - 1): the visible "×3" badge already says the story has
     * 3 copies, so "also in 2 feeds" is what a screen reader must announce.
     * Re-binding re-applies the expansion state from {@link #mExpandedItemIds}
     * (keyed by item id), so RecyclerView recycling neither loses nor leaks an
     * open expansion.
     *
     * @param activity      the current activity, used for resources
     * @param rssItemModel  the freshly mapped model of the bound item
     */
    // deliberate default-locale String.format so the ×N badge renders digits in
    // the device locale; no Locale override intended (see DUPLICATE_BADGE_FORMAT)
    @SuppressLint("DefaultLocale")
    private void bindDuplicateBadge(Activity activity, RssItemModel rssItemModel) {
        if (mContainerDuplicates == null || mChipDuplicates == null
                || mContainerCopies == null) {
            return;
        }
        boolean hasDuplicates = rssItemModel.duplicateCount > 1
                && rssItemModel.id != null;
        if (hasDuplicates) {
            RssItem rssItem = mRssItemSubject.getValue();
            hasDuplicates = rssItem != null && rssItem.link != null
                    && !rssItem.link.isEmpty();
        }
        if (!hasDuplicates) {
            mExpandedItemIds.remove(rssItemModel.id);
            collapseOtherCopies();
            mContainerDuplicates.setVisibility(View.GONE);
            return;
        }
        mContainerDuplicates.setVisibility(View.VISIBLE);
        mChipDuplicates.setText(String.format(DUPLICATE_BADGE_FORMAT,
                rssItemModel.duplicateCount));
        int otherCopies = rssItemModel.duplicateCount - 1;
        mChipDuplicates.setContentDescription(
                activity.getResources().getQuantityString(R.plurals.also_in_n_feeds,
                        otherCopies, otherCopies));
        if (mExpandedItemIds.contains(rssItemModel.id)) {
            // already open (or re-opened after a rebind wiped the view): only fetch
            // again when the expansion has no rendered rows yet, so same-item
            // re-emissions (favorite/read toggles) do not flicker the list
            if (mContainerCopies.getChildCount() == 0) {
                loadOtherCopies(activity, rssItemModel.id);
            } else {
                mContainerCopies.setVisibility(View.VISIBLE);
            }
        } else {
            collapseOtherCopies();
        }
    }

    /**
     * Opens or closes the duplicate-copies expansion of the currently bound item.
     * State is tracked per item id so a recycled view never shows a previous
     * item's expansion.
     *
     * @param activity the current activity, used to build the copy rows
     */
    private void toggleCopiesExpansion(Activity activity) {
        RssItem rssItem = mRssItemSubject.getValue();
        if (rssItem == null || rssItem.id == null) {
            return;
        }
        if (mExpandedItemIds.remove(rssItem.id)) {
            collapseOtherCopies();
        } else {
            mExpandedItemIds.add(rssItem.id);
            loadOtherCopies(activity, rssItem.id);
        }
    }

    /**
     * Loads the OTHER copies (every rss_item sharing the bound item's link except
     * the displayed row itself) off the main thread and renders them. The render
     * is dropped when the row was re-bound to another item meanwhile or the
     * expansion was closed - the recycled-view guard against stale async results.
     * The load goes through {@link RssRepository#findRssItemsByLink(String)}
     * (now backed by the rss_item.link index, migration 11→12) and
     * {@link RssRepository#findRssChannelById(long)} to resolve feed names;
     * both are DAO-backed synchronous reads, hence the executor requirement.
     * Strictly display-only: no per-copy interaction in v1.
     *
     * @param activity the current activity, used to build the copy rows
     * @param itemId   id of the currently bound (expanded) item
     */
    private void loadOtherCopies(Activity activity, long itemId) {
        mRxDisposer.add(KEY_LOAD_OTHER_COPIES,
                Single.fromCallable(() -> buildOtherCopyLines(itemId))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(copyLines -> {
                            RssItem current = mRssItemSubject.getValue();
                            if (current == null || current.id == null
                                    || current.id != itemId
                                    || !mExpandedItemIds.contains(itemId)) {
                                return;
                            }
                            showOtherCopies(activity, copyLines);
                        }, throwable ->
                                mSvProvider.get(ILogger.class)
                                        .e(TAG, throwable.getMessage(), throwable)));
    }

    /**
     * Resolves the expansion's display lines on a background thread: one line per
     * other copy, "feed name - date", dates in the row's date pattern.
     *
     * @param itemId id of the currently bound item, excluded from the result
     * @return display lines for the other copies, in DAO result order
     */
    private List<String> buildOtherCopyLines(long itemId) {
        RssItem boundItem = mRssItemSubject.getValue();
        if (boundItem == null || boundItem.link == null || boundItem.link.isEmpty()) {
            return new ArrayList<>();
        }
        List<RssItem> copies = mRssRepository.findRssItemsByLink(boundItem.link);
        SimpleDateFormat dateFormat = new SimpleDateFormat(COPY_DATE_FORMAT);
        // feed-name lookups are memoized per call: several copies often share one feed
        Map<Long, String> channelNames = new HashMap<>();
        ArrayList<String> copyLines = new ArrayList<>();
        if (copies != null) {
            for (RssItem copy : copies) {
                if (copy.id != null && copy.id == itemId) {
                    continue;
                }
                String channelName = channelNames.get(copy.channelId);
                if (channelName == null) {
                    channelName = resolveChannelName(copy.channelId);
                    channelNames.put(copy.channelId, channelName);
                }
                Date date = copy.pubDate != null ? copy.pubDate : copy.createdDateTime;
                copyLines.add(channelName + " - "
                        + (date != null ? dateFormat.format(date) : ""));
            }
        }
        return copyLines;
    }

    /**
     * Resolves a display name for the given channel id, falling back to the
     * channel title and finally to an empty string (a deleted channel must not
     * break the expansion render).
     *
     * @param channelId the channel id to resolve, may be null
     * @return the channel's feed name (or title), or "" when unresolvable
     */
    private String resolveChannelName(Long channelId) {
        if (channelId == null) {
            return "";
        }
        RssChannel rssChannel = mRssRepository.findRssChannelById(channelId);
        if (rssChannel == null) {
            return "";
        }
        if (rssChannel.feedName != null) {
            return rssChannel.feedName;
        }
        return rssChannel.title != null ? rssChannel.title : "";
    }

    /**
     * Renders the loaded copy lines into the expansion container (main thread).
     *
     * @param activity  the current activity, used to build the copy rows
     * @param copyLines the resolved display lines
     */
    private void showOtherCopies(Activity activity, List<String> copyLines) {
        mContainerCopies.removeAllViews();
        if (copyLines == null || copyLines.isEmpty()) {
            mContainerCopies.setVisibility(View.GONE);
            return;
        }
        for (String copyLine : copyLines) {
            TextView copyTextView = new TextView(activity);
            copyLine = copyLine.trim();
            if (copyLine.endsWith("-")) {
                // a copy without any date leaves a dangling separator
                copyLine = copyLine.substring(0, copyLine.length() - 1).trim();
            }
            copyTextView.setText(copyLine);
            mContainerCopies.addView(copyTextView);
        }
        mContainerCopies.setVisibility(View.VISIBLE);
    }

    /**
     * Closes the expansion: drops the rendered rows and hides the container. The
     * expanded-id bookkeeping is owned by the callers.
     */
    private void collapseOtherCopies() {
        if (mContainerCopies != null) {
            mContainerCopies.removeAllViews();
            mContainerCopies.setVisibility(View.GONE);
        }
    }

    private void setupRowAccessibilityDelegate(View rowView) {
        ViewCompat.setAccessibilityDelegate(rowView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                RssItem rssItem = mRssItemSubject.getValue();
                if (rssItem == null) {
                    return;
                }
                // setStateDescription is ignored below API 30, there the state word is
                // set as the row view's content description in the Rx subscribe block,
                // which the framework copies into the node via standard population
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    String stateText = mSvProvider.getContext().getString(rssItem.isRead ?
                            R.string.state_read : R.string.state_unread);
                    info.setStateDescription(stateText);
                }
            }
        });
    }

    private void toggleFavorite() {
        RssItem rssItem = mRssItemSubject.getValue();
        if (rssItem == null) {
            return;
        }
        boolean newVal = !rssItem.isFavorite;
        mUpdateRssItemIsFavoriteCmd.execute(rssItem, newVal);
        mRssItemSubject.onNext(rssItem);
    }

    private void toggleReadState() {
        RssItem rssItem = mRssItemSubject.getValue();
        if (rssItem == null) {
            return;
        }
        Context context = mSvProvider.getContext();
        boolean newIsRead = !rssItem.isRead;
        mUpdateRssItemIsReadCmd.execute(rssItem, newIsRead);
        mRssItemSubject.onNext(rssItem);
        Toast.makeText(context, context.getString(newIsRead ?
                R.string.mark_as_read : R.string.mark_as_unread), Toast.LENGTH_SHORT)
                .show();
    }

    private void addReadAction(View rowView) {
        RssItem rssItem = mRssItemSubject.getValue();
        boolean isRead = rssItem != null && rssItem.isRead;
        if (mMarkReadUnreadAccessibilityActionId != 0) {
            ViewCompat.removeAccessibilityAction(rowView, mMarkReadUnreadAccessibilityActionId);
        }
        mMarkReadUnreadAccessibilityActionId = ViewCompat.addAccessibilityAction(rowView,
                mSvProvider.getContext().getString(isRead ?
                        R.string.mark_as_unread : R.string.mark_as_read),
                (view, arguments) -> {
                    toggleReadState();
                    return true;
                });
    }

    private void addFavoriteAction(View rowView) {
        RssItem rssItem = mRssItemSubject.getValue();
        boolean isFavorite = rssItem != null && rssItem.isFavorite;
        if (mAddRemoveFavoriteAccessibilityActionId != 0) {
            ViewCompat.removeAccessibilityAction(rowView, mAddRemoveFavoriteAccessibilityActionId);
        }
        mAddRemoveFavoriteAccessibilityActionId = ViewCompat.addAccessibilityAction(rowView,
                mSvProvider.getContext().getString(isFavorite ?
                        R.string.favorite_remove : R.string.favorite_add),
                (view, arguments) -> {
                    toggleFavorite();
                    return true;
                });
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mGetRssChannelByIdAndOpenDetail_routeOptions = null;
        mRssItemModelObservable = null;
        mChipDuplicates = null;
        mContainerDuplicates = null;
        mContainerCopies = null;
        mExpandedItemIds.clear();
        mRssRepository = null;
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            RssItem rssItem = mRssItemSubject.getValue();
            if (!rssItem.isRead) {
                mUpdateRssItemIsReadCmd.execute(rssItem, true);
                mRssItemSubject.onNext(rssItem);
            }
            mRxDisposer
                    .add("onClick_getRssChannelById",
                            mRssQueryCmd
                                    .getRssChannelById(rssItem.channelId)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((rssChannel, throwable) -> {
                                        if (throwable != null) {
                                            mSvProvider.get(ILogger.class)
                                                    .e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            mNavigator.push(Routes.RSS_ITEM_DETAIL_PAGE,
                                                    RssItemDetailPage.Args.withRss(rssItem, rssChannel), null
                                                    , mGetRssChannelByIdAndOpenDetail_routeOptions);
                                        }
                                    })
                    );
        }
    }

    public void setRssItem(RssItem rssItem) {
        mRssItemSubject.onNext(rssItem);
    }

    @Override
    public boolean onLongClick(View view) {
        int id = view.getId();
        if (id == R.id.root_layout) {
            toggleReadState();
            return true;
        }
        return false;
    }
}
