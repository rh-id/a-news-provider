package m.co.rh.id.a_news_provider.app.ui.component.rss;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
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
import m.co.rh.id.a_news_provider.app.provider.component.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.provider.component.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.provider.component.repository.RssRepository;
import m.co.rh.id.a_news_provider.app.ui.model.RssItemModel;
import m.co.rh.id.a_news_provider.app.ui.page.RssItemDetailPage;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.util.RssItemCategories;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavigator;
import m.co.rh.id.aprovider.Provider;

/**
 * Shared base of every rss item list row variant (issue #58). Owns ALL row behavior -
 * the things every layout needs identically - while each concrete variant
 * ({@link RssItemSV} standard, {@link RssItemCompactSV} compact,
 * {@link RssItemCardSV} card) only supplies its own row layout and its visual
 * binding. Behavior owned here (extracted verbatim from the former monolithic
 * {@code RssItemSV}):
 * <ul>
 *     <li>the {@link SerialBehaviorSubject}&lt;RssItem&gt; plus the RssItem to
 *     RssItemModel mapping pipeline (executor-scheduled, main-thread observed), now
 *     also carrying the channel display data (name / icon url / media image) the card
 *     layout renders,</li>
 *     <li>the {@code RssChangeNotifier.getUpdatedRssItem()} self-heal subscription that
 *     re-emits the row's item when its read/favorite state changed elsewhere,</li>
 *     <li>the read/favorite toggles, the tap-to-detail navigation (with the channel
 *     lookup that feeds the detail page args) and the long-press read toggle,</li>
 *     <li>the TalkBack semantics: row state description with its pre-R content
 *     description fallback plus the state-correct mark read/unread and add/remove
 *     favorite custom accessibility actions,</li>
 *     <li>the duplicate-story badge with its copies expansion (issue #45) - all three
 *     layouts embed the same badge views so the spoken semantics stay identical,</li>
 *     <li>the dispose contract: the page-scoped {@link StatefulViewProvider} is disposed
 *     with the row (the row subscriptions live in its {@link RxDisposer}).</li>
 * </ul>
 * A variant implements exactly three hooks:
 * <ul>
 *     <li>{@link #provideLayoutRes()} - the variant's row layout resource; the root
 *     view id MUST be {@code root_layout} (the click/long-click gates below check it)
 *     and it MUST embed {@code button_favorite} plus the duplicate-badge views
 *     ({@code container_duplicates}/{@code chip_duplicates}/{@code container_copies}),</li>
 *     <li>{@link #onRowViewCreated(Activity, View)} - one-time view lookup/wiring per
 *     inflation (cached into variant fields; there is deliberately no per-bind
 *     findViewById),</li>
 *     <li>{@link #bindRowView(Activity, View, RssItemModel)} - visual binding of a
 *     freshly mapped model, always called on the main thread. The favorite star image
 *     is updated by the base after it (identical across all layouts).</li>
 * </ul>
 * The variants must NOT re-register accessibility actions or touch the state
 * description - the shared subscribe tail below owns those, exactly as the former
 * {@code RssItemSV} did, so every layout variant announces the same state words and
 * offers the same custom actions (the {@code AccessibilityAuditTest} semantics).
 * <p>
 * RecyclerView recycling is safe because every per-item visual flows through
 * {@link #bindRowView} and the expansion state is keyed by item id
 * ({@link #mExpandedItemIds}), never held on the view.
 */
public abstract class BaseRssItemSV extends StatefulView<Activity> implements RequireNavigator, RequireComponent<Provider>, View.OnClickListener, View.OnLongClickListener {

    protected static final String TAG = BaseRssItemSV.class.getName();

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

    protected transient INavigator mNavigator;
    protected transient Provider mSvProvider;
    protected transient ExecutorService mExecutorService;
    protected transient RxDisposer mRxDisposer;
    protected transient RssChangeNotifier mRssChangeNotifier;
    private transient RssQueryCmd mRssQueryCmd;
    private transient UpdateRssItemIsReadCmd mUpdateRssItemIsReadCmd;
    private transient UpdateRssItemIsFavoriteCmd mUpdateRssItemIsFavoriteCmd;
    private transient RssRepository mRssRepository;

    private transient ImageButton mButtonFavorite;
    private transient Chip mChipDuplicates;
    private transient ViewGroup mContainerDuplicates;
    private transient ViewGroup mContainerCopies;

    /**
     * Ids of the items whose sources expansion is currently open. Keyed by item id
     * (NOT held as a boolean on the view) because RecyclerView recycles row views:
     * the same row SV instance renders different items over its lifetime, and
     * an un-keyed flag would leak one item's expansion state onto the recycled
     * item bound next.
     */
    private final Set<Long> mExpandedItemIds;

    private final SerialBehaviorSubject<RssItem> mRssItemSubject;
    private transient RouteOptions mGetRssChannelByIdAndOpenDetail_routeOptions;
    private transient Observable<RssItemModel> mRssItemModelObservable;
    private transient int mMarkReadUnreadAccessibilityActionId;
    private transient int mAddRemoveFavoriteAccessibilityActionId;

    protected BaseRssItemSV() {
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
        // expansion AND the channel display data of the mapping below, whose
        // lookups must never run on the main thread
        mRssRepository = provider.get(RssRepository.class);
        mRssItemModelObservable = mRssItemSubject.getSubject()
                // observeOn(executor) BEFORE the map - deliberately NOT subscribeOn:
                // for a Subject source the map lambda runs on whatever thread calls
                // onNext (setRssItem from onBindViewHolder and the read/favorite
                // toggles = the MAIN thread), while subscribeOn only moves the
                // subscribe-time work. The former RssItemSV map was pure CPU and
                // thread-agnostic, so that nuance never mattered; since issue #58
                // the mapping performs DAO-backed channel lookups, which must
                // genuinely run off-main - Room throws on a main-thread read, the
                // map operator turns that throw into onError, and (with the
                // swallow-only global Rx error handler) the row's whole bind chain
                // silently dies and never re-registers its a11y actions. Emissions
                // hop to the executor for the mapping and back to main afterwards.
                .observeOn(Schedulers.from(mExecutorService))
                .map(
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
                    // issue #58: channel display data for the layouts that render it
                    // (card), resolved on THIS (now truly executor-side) mapping pass
                    // with a per-pass memoized channel cache, because several visible
                    // items usually share one channel (same precedent as
                    // buildOtherCopyLines' per-call memoization)
                    Map<Long, RssChannel> channelCache = new HashMap<>();
                    RssChannel rssChannel = resolveRssChannel(rssItem.channelId, channelCache);
                    rssItemModel.mediaImage = rssItem.mediaImage;
                    rssItemModel.channelName = resolveChannelDisplayName(rssChannel);
                    rssItemModel.channelIconUrl = rssChannel == null
                            ? null : rssChannel.imageUrl;
                    return rssItemModel;
                }
        )
                .observeOn(AndroidSchedulers.mainThread());
        // shared with the detail page's next/previous replace so both open identically
        mGetRssChannelByIdAndOpenDetail_routeOptions =
                RssItemDetailPage.detailPageRouteOptions();
    }

    @Override
    protected final View createView(Activity activity, ViewGroup container) {
        View view = activity.getLayoutInflater().inflate(provideLayoutRes(), container, false);
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        setupRowAccessibilityDelegate(view);
        mContainerDuplicates = view.findViewById(R.id.container_duplicates);
        mChipDuplicates = view.findViewById(R.id.chip_duplicates);
        mContainerCopies = view.findViewById(R.id.container_copies);
        if (mChipDuplicates != null) {
            mChipDuplicates.setOnClickListener(v -> toggleCopiesExpansion(activity));
        }
        onRowViewCreated(activity, view);
        mButtonFavorite = view.findViewById(R.id.button_favorite);
        mButtonFavorite.setOnClickListener(v -> toggleFavorite());
        ViewCompat.setAccessibilityDelegate(mButtonFavorite, new AccessibilityDelegateCompat() {
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
                            bindRowView(activity, view, rssItemModel);
                            bindDuplicateBadge(activity, rssItemModel);
                            mButtonFavorite.setImageResource(rssItemModel.isFavorite ?
                                    R.drawable.ic_star_filled_orange : R.drawable.ic_star_outline_gray);
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
                            mButtonFavorite.sendAccessibilityEvent(
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
     * @return the variant's row layout resource; see the class javadoc for the
     * layout contract (root_layout id, button_favorite, duplicate badge views)
     */
    protected abstract int provideLayoutRes();

    /**
     * One-time per-inflation hook: lookup and wire the variant's own views.
     * Runs before the first model emission, on the main thread, right after the
     * shared click/long-click listeners and accessibility delegate are attached.
     *
     * @param activity the current activity
     * @param rowView  the freshly inflated row root
     */
    protected abstract void onRowViewCreated(Activity activity, View rowView);

    /**
     * Binds the variant's visuals from a freshly mapped model (main thread).
     * Called for EVERY emission - including re-emissions of the same item after a
     * read/favorite toggle - so it must set every per-item visual unconditionally
     * (RecyclerView recycling reuses the view for another item).
     *
     * @param activity     the current activity, used for resources/inflation
     * @param rowView      the row root
     * @param rssItemModel the freshly mapped model of the bound item
     */
    protected abstract void bindRowView(Activity activity, View rowView,
                                        RssItemModel rssItemModel);

    /**
     * Resolves the bound item's channel once per mapping pass, memoized by the
     * caller's per-pass cache (a null result is memoized too - a deleted channel
     * must not turn into repeated DAO hits).
     *
     * @param channelId    the channel id to resolve, may be null
     * @param channelCache the mapping pass' memoization cache
     * @return the channel, or null when unknown/deleted
     */
    private RssChannel resolveRssChannel(Long channelId, Map<Long, RssChannel> channelCache) {
        if (channelId == null) {
            return null;
        }
        if (channelCache.containsKey(channelId)) {
            return channelCache.get(channelId);
        }
        RssChannel rssChannel = mRssRepository.findRssChannelById(channelId);
        channelCache.put(channelId, rssChannel);
        return rssChannel;
    }

    /**
     * Resolves a display name for the given channel, falling back to the channel
     * title and finally to an empty string (a deleted channel must not break the
     * row render).
     *
     * @param rssChannel the channel to name, may be null
     * @return the channel's feed name (or title), or "" when unresolvable
     */
    protected String resolveChannelDisplayName(RssChannel rssChannel) {
        if (rssChannel == null) {
            return "";
        }
        if (rssChannel.feedName != null) {
            return rssChannel.feedName;
        }
        return rssChannel.title != null ? rssChannel.title : "";
    }

    /**
     * Resolves a display name for the given channel id via a synchronous DAO
     * lookup - background-thread only (the copies expansion uses it on the
     * executor).
     *
     * @param channelId the channel id to resolve, may be null
     * @return the channel's feed name (or title), or "" when unresolvable
     */
    private String resolveChannelName(Long channelId) {
        if (channelId == null) {
            return "";
        }
        return resolveChannelDisplayName(mRssRepository.findRssChannelById(channelId));
    }

    /**
     * Binds the duplicate-story badge (issue #45) for the given model. The badge
     * shows ONLY when the loaded page carried an absolute copy count &gt; 1 for a
     * row that is expandable - since phase 2 that is a row with a non-empty
     * dedup_key (the copies expansion resolves the whole story cluster through
     * the key, even when the row itself has no link) OR a non-null non-empty
     * link (the phase-1 unkeyed rows) - every other row keeps the exact
     * pre-feature layout (the whole container collapses). The spoken form is the
     * also_in_n_feeds plural over the OTHER copies (count - 1): the visible "×3"
     * badge already says the story has 3 copies, so "also in 2 feeds" is what a
     * screen reader must announce.
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
            hasDuplicates = rssItem != null
                    && ((rssItem.dedupKey != null && !rssItem.dedupKey.isEmpty())
                    || (rssItem.link != null && !rssItem.link.isEmpty()));
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
     * The load goes through the two-tier copy lookup of
     * {@link #buildOtherCopyLines(long)} (dedup-key cluster lookup for keyed
     * rows, the link-indexed {@link RssRepository#findRssItemsByLink(String)}
     * for unkeyed ones) and
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
     * other copy, "feed name - date", dates in the row's date pattern. Since the
     * phase-2 title-similarity collapse (issue #45), the copy lookup is two-tier
     * like the counting: a bound row with a non-empty dedup_key resolves the
     * WHOLE story cluster through
     * {@link RssRepository#findRssItemsByDedupKey(String)} - copies with
     * different links are part of the story and would be invisible to a per-link
     * lookup - while an unkeyed row keeps the phase-1
     * {@link RssRepository#findRssItemsByLink(String)}.
     *
     * @param itemId id of the currently bound item, excluded from the result
     * @return display lines for the other copies, in DAO result order
     */
    private List<String> buildOtherCopyLines(long itemId) {
        RssItem boundItem = mRssItemSubject.getValue();
        if (boundItem == null) {
            return new ArrayList<>();
        }
        List<RssItem> copies;
        if (boundItem.dedupKey != null && !boundItem.dedupKey.isEmpty()) {
            copies = mRssRepository.findRssItemsByDedupKey(boundItem.dedupKey);
        } else if (boundItem.link != null && !boundItem.link.isEmpty()) {
            copies = mRssRepository.findRssItemsByLink(boundItem.link);
        } else {
            return new ArrayList<>();
        }
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
        mButtonFavorite = null;
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
            // a click can land before the first bind (or after dispose/rebind of a
            // recycled row) and a late a11y action likewise: a null item means
            // nothing to open - silent no-op instead of a main-thread NPE that
            // aborts instrumentation batches
            if (rssItem == null) {
                return;
            }
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

    /**
     * Read access to the currently bound rss item for the swipe callback
     * ({@link RssItemSwipeHelperCallback}, which acts on the row's in-memory
     * read/favorite flags) and the layout variants' self-heal subscriptions
     * (e.g. the card layout's channel-rename refresh). The swipe gestures act on
     * the row's in-memory state, which lives in the row's subject - there was
     * previously only the write-side {@link #setRssItem(RssItem)}.
     *
     * @return the currently bound rss item, or null before the first bind
     */
    protected RssItem getRssItem() {
        return mRssItemSubject.getValue();
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
