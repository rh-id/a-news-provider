package m.co.rh.id.a_news_provider.app.ui.page;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.OptIn;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.ui.PlayerView;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.playback.PlaybackManager;
import m.co.rh.id.a_news_provider.app.playback.PlaybackState;
import m.co.rh.id.a_news_provider.app.playback.VideoUrlPolicy;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.BaseRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.FetchArticleContentCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsFavoriteCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsReadCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.image.ImageViewerSVDialog;
import m.co.rh.id.a_news_provider.app.ui.component.rss.EditRssLinkSVDialog;
import m.co.rh.id.a_news_provider.app.ui.KeyEventHandler;
import m.co.rh.id.a_news_provider.app.util.ReadingTypography;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.ArticleContent;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.entity.RssItemCategories;
import m.co.rh.id.a_news_provider.base.provider.notifier.DeviceStatusNotifier;
import m.co.rh.id.a_news_provider.base.ui.SwipeGestureDetector;
import m.co.rh.id.a_news_provider.base.util.UrlSanitizer;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavActivityLifecycle;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class RssItemDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnRequestPermissionResult, View.OnClickListener, Toolbar.OnMenuItemClickListener, AppBarSV.OnMenuCreated, KeyEventHandler, NavActivityLifecycle<Activity> {

    private static final String TAG = RssItemDetailPage.class.getName();
    private static final int REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 1;

    /**
     * Uniform gap between category chips in the detail strip, applied as marginEnd
     * on every chip (including the last) - same spacing as the list-row strip.
     */
    private static final float CHIP_SPACING_DP = 8f;

    /**
     * Height of the app-wide mini-player bar (mini_player_bar.xml): the extra
     * bottom margin the FABs receive while the bar is visible on this page so
     * they sit above it (issue #69 phase 2).
     */
    private static final float MINI_PLAYER_BAR_LIFT_DP = 56f;

    @NavInject
    private AppBarSV mAppBarSV;
    @NavInject
    private transient INavigator mNavigator;
    @NavInject
    private transient NavRoute mNavRoute;
    private RssItem mRssItem;
    private RssChannel mRssChannel;
    private transient Provider mSvProvider;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient ImageLoader mImageLoader;
    private transient AppSharedPreferences mAppSharedPreferences;
    private transient SwipeGestureDetector mSwipeGestureDetector;
    private transient RxDisposer mRxDisposer;
    private transient UpdateRssItemIsFavoriteCmd mUpdateRssItemIsFavoriteCmd;
    private transient UpdateRssItemIsReadCmd mUpdateRssItemIsReadCmd;
    private transient PagedRssItemsCmd mPagedRssItemsCmd;
    private transient RssQueryCmd mRssQueryCmd;
    private transient FetchArticleContentCmd mFetchArticleContentCmd;
    private transient MenuItem mToggleFavoriteMenuItem;
    private transient MenuItem mDownloadVideoMenuItem;
    private transient MenuItem mReadFullArticleMenuItem;
    // the page's content view, captured in createView for the reader-mode
    // render paths (onMenuItemClick has no view parameter). transient: page
    // fields participate in NavRoute serialization
    private transient View mPageView;
    // reader mode (issue #28): true while text_content shows the extracted
    // full article instead of the original feed description
    private transient boolean mReaderMode;
    // in-flight guard for the full-text fetch: one fetch at a time, the
    // progress indicator is driven by it
    private transient boolean mFetchInFlight;
    // in-flight guard for the SILENT background refresh of already-rendered
    // reader content (cache-first menu tap while online, issue #28): separate
    // from mFetchInFlight so a background refresh never drives the progress
    // indicator and never blocks rendering the cached row
    private transient boolean mBackgroundRefreshInFlight;
    // the reader-mode content string currently rendered (issue #28): the
    // background refresh re-renders only when the fetched content differs
    // from it, so a no-change refresh never jumps the scroll position
    private transient String mRenderedArticleContent;
    // in-flight guard for navigateToNeighbor: volume keys are easier to spam than
    // the right-edge swipe, and each press is a db query followed by a full page
    // replace — a second press while the first navigation resolves must not
    // double-replace. transient: page fields participate in NavRoute serialization
    private transient boolean mNeighborNavigationInProgress;
    // podcast playback (issue #69, phase 2): the media controller moved OUT of
    // the page into the app-scoped PlaybackManager - playback now outlives the
    // page that started it (mini-player bar, picture-in-picture). The page only
    // REFLECTS the shared session's state (audio FAB, inline video slot, FAB
    // lift for the mini-player bar) through the subscription added in
    // createView; dispose unsubscribes but NEVER releases the controller.
    // transient: page fields participate in NavRoute serialization
    private transient PlaybackManager mPlaybackManager;
    // stateless inline-video decision policy: resolved alongside the playback
    // manager from the same global provider (transient: page fields
    // participate in NavRoute serialization)
    private transient VideoUrlPolicy mVideoUrlPolicy;

    public RssItemDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_rss_item_detail);
    }

    /**
     * Builds the RouteOptions used when opening the rss item detail page: Material
     * transitions. Shared by the list's open-detail push (RssItemSV) and the detail
     * page's next/previous replace so both move identically.
     *
     * @return the route options for showing the rss item detail page
     */
    public static RouteOptions detailPageRouteOptions() {
        return RouteOptions.withTransition(R.transition.page_rss_item_detail_enter,
                R.transition.page_rss_item_detail_exit);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mImageLoader = mSvProvider.get(ImageLoader.class);
        mAppSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mUpdateRssItemIsFavoriteCmd = mSvProvider.get(UpdateRssItemIsFavoriteCmd.class);
        mUpdateRssItemIsReadCmd = mSvProvider.get(UpdateRssItemIsReadCmd.class);
        mRssQueryCmd = mSvProvider.get(RssQueryCmd.class);
        mFetchArticleContentCmd = mSvProvider.get(FetchArticleContentCmd.class);
        // the playback manager is APP-SCOPED: resolved from the global provider
        // (like PagedRssItemsCmd below), so this page and the mini-player bar /
        // MainActivity PiP logic observe the same session (registered in both
        // AppProviderModule and IntegrationTestAppProviderModule)
        mPlaybackManager = provider.get(PlaybackManager.class);
        mVideoUrlPolicy = provider.get(VideoUrlPolicy.class);
        // PagedRssItemsCmd must come from the GLOBAL provider: the home list uses the
        // same app-scoped instance (see RssItemListSV), so its channel/filter/sort
        // describe exactly the list the user navigated from. The page-scoped provider
        // would return a fresh instance with default filter/sort instead.
        mPagedRssItemsCmd = provider.get(PagedRssItemsCmd.class);
        mSwipeGestureDetector = new SwipeGestureDetector(provider.getContext()) {
            @Override
            public void onSwipeLeft() {
                // swipe left near the right edge = next article; the rightward
                // fling (pop) override was removed: it duplicated system back
                // and its strip sat inside the system back-gesture zone
                if (mAppSharedPreferences.isSwipeArticleNavigationEnabled()) {
                    navigateToNeighbor(true);
                }
            }
        };
    }

    @Override
    protected void initState(Activity activity) {
        super.initState(activity);
        Args args = Args.of(mNavRoute);
        if (args != null) {
            mRssItem = args.getRssItem();
            mRssChannel = args.getRssChannel();
        }
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        int layoutId = R.layout.page_rss_item_detail;
        if (mAppSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_rss_item_detail;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        mPageView = view;
        // single right-edge strip overlaying the content side; fling left =
        // next article (gated live by the swipe setting in the detector). The
        // strip is inset beyond the system back-gesture zone (>= ~25dp measured
        // on API 36) via layout_marginEnd, so it never steals system back
        // touches at the very edge — there the system predictive back runs
        view.findViewById(R.id.container_swipe_region_right)
                .setOnTouchListener(mSwipeGestureDetector);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        mAppBarSV.setMenuItemListener(this);
        mAppBarSV.setOnMenuCreated(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        mRxDisposer.add("rssChangeNotifier.updatedRssItem.favoriteIcon",
                mSvProvider.get(RssChangeNotifier.class).getUpdatedRssItem()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(rssItem -> {
                            if (mRssItem != null && rssItem.id.equals(mRssItem.id)
                                    && rssItem.isFavorite != mRssItem.isFavorite) {
                                mRssItem.isFavorite = rssItem.isFavorite;
                                updateFavoriteIcon(mRssItem.isFavorite);
                            }
                        }));
        // article reader mode (issue #28): when the fetch of THIS item's link
        // completes anywhere in the app (this page's manual action, auto-fetch,
        // or a previous page instance that was replaced), load the cached
        // content and switch into reader mode. A popped/replaced page never
        // sees the event: its subscriptions are disposed with the page, while
        // the content still lands in the database for the next open.
        mRxDisposer.add("rssChangeNotifier.articleContentUpdated",
                mSvProvider.get(RssChangeNotifier.class).getArticleContentUpdated()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(link -> {
                            if (mRssItem != null && link != null
                                    && link.equals(mRssItem.link)) {
                                if (mBackgroundRefreshInFlight) {
                                    // the silent background refresh reports
                                    // through its own completion path, which
                                    // re-renders only when the content changed
                                    // and never flips the user back to reader
                                    // mode - handling the event here too would
                                    // render twice and override an explicit
                                    // "Show original description" tap
                                    return;
                                }
                                loadAndRenderFullContent();
                            }
                        }));
        // SettingsPage can be pushed on top of this page (menu_reading_settings) and
        // a-navigator's popInternal only disposes the popped route, so this page's
        // retained View is re-shown without createView() re-running. Without this
        // subscription the article would come back with stale typography. Each
        // subject replays its current value, so this also fires once up front and
        // keeps the initial application in one place.
        mRxDisposer.add("readingTypography.changed",
                Flowable.combineLatest(mAppSharedPreferences.getReadingFontSizeFlow(),
                                mAppSharedPreferences.getReadingFontFamilyFlow(),
                                mAppSharedPreferences.getReadingLineSpacingFlow(),
                                mAppSharedPreferences.getIsReadingJustificationFlow(),
                                ReadingTypography::of)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(typography -> {
                            typography.applyToBody(view.findViewById(R.id.text_content));
                            typography.applyToTitle(view.findViewById(R.id.text_title));
                        }));
        // app-scoped playback (issue #69): reflects the shared session onto the
        // audio FAB, the inline video slot and the FAB lift for the mini-player
        // bar. The subject replays its current value, so the initial application
        // happens here too. The subscription dies with the page (RxDisposer);
        // the CONTROLLER does not - playback outlives the page by design.
        mRxDisposer.add("playbackManager.state",
                mPlaybackManager.state()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(this::onPlaybackStateChanged));
        Button fabOpenLink = view.findViewById(R.id.fab_open_link);
        fabOpenLink.setOnClickListener(this);
        Button fabOpenVideo = view.findViewById(R.id.fab_open_video);
        fabOpenVideo.setOnClickListener(this);
        Button fabPlayAudio = view.findViewById(R.id.fab_play_audio);
        fabPlayAudio.setOnClickListener(this);
        bindRssItem(activity, view);
        return view;
    }

    /**
     * Binds the current rss item and channel to the content views on page creation.
     *
     * @param activity the current activity, used to inflate chips
     * @param view     the page's content view
     */
    private void bindRssItem(Activity activity, View view) {
        TextView titleText = view.findViewById(R.id.text_title);
        ViewCompat.setTransitionName(titleText, "title_" + mRssItem.id);
        titleText.setText(HtmlCompat
                .fromHtml(mRssItem.title, HtmlCompat.FROM_HTML_MODE_COMPACT));
        titleText.setOnClickListener(this);
        titleText.setContentDescription(activity.getString(R.string.open_link));
        NetworkImageView networkImageView = view.findViewById(R.id.network_image);
        String imageUrl = null;
        if (mRssItem.mediaImage != null) {
            imageUrl = mRssItem.mediaImage;
        }
        // inline video (issue #69 phase 2): when the item's video enclosure is
        // probably a directly playable file, the video slot replaces the hero
        // image AND the open-in-browser FAB - see bindVideoSlot. The video slot
        // never inherits the isDownloadImage() gate: the user tapped into an
        // article whose video can play right here, independent of the image
        // saving preference.
        boolean showInlineVideo = isDirectVideoItem();
        boolean showImage = mAppSharedPreferences.isDownloadImage()
                && imageUrl != null && !showInlineVideo;
        if (showImage) {
            Drawable drawable = DrawableCompat.wrap(ContextCompat
                    .getDrawable(activity, R.drawable.ic_image_black));
            DrawableCompat.setTint(drawable, ContextCompat
                    .getColor(activity, R.color.daynight_black_white));
            networkImageView.setDefaultImageDrawable(drawable);
            networkImageView.setErrorImageResId(R.drawable.ic_broken_image_red);
            networkImageView.setImageUrl(imageUrl, mImageLoader);
            networkImageView.setVisibility(View.VISIBLE);
            // tap the hero image to open the full-screen pinch-zoom viewer (issue #36);
            // both detail layouts share R.id.network_image so one code path covers
            // normal + one-hand mode
            networkImageView.setContentDescription(
                    activity.getString(R.string.open_image_viewer));
            networkImageView.setOnClickListener(v -> mNavigator.push(
                    (args, activity1) -> new ImageViewerSVDialog(),
                    ImageViewerSVDialog.Args.with(mRssItem.mediaImage,
                            mRssChannel.feedName, mRssItem.title)));
        } else {
            networkImageView.setVisibility(View.GONE);
        }
        renderDescription();
        HorizontalScrollView scrollCategories = view.findViewById(R.id.scroll_categories);
        LinearLayout containerCategories = view.findViewById(R.id.container_categories);
        bindCategoryChips(activity, scrollCategories, containerCategories);
        Button fabOpenVideo = view.findViewById(R.id.fab_open_video);
        if (mRssItem.mediaVideo != null && !showInlineVideo) {
            fabOpenVideo.setVisibility(View.VISIBLE);
        } else {
            fabOpenVideo.setVisibility(View.GONE);
        }
        // audio play/pause follows the same rebind contract: neighbor navigation
        // swaps items in place on this page, so visibility AND icon must refresh
        // here, not only at view creation (issue #69)
        ExtendedFloatingActionButton fabPlayAudio = view.findViewById(R.id.fab_play_audio);
        if (mRssItem.mediaAudio != null) {
            fabPlayAudio.setVisibility(View.VISIBLE);
            updateAudioFabVisual(fabPlayAudio, mPlaybackManager.getCurrentState());
        } else {
            fabPlayAudio.setVisibility(View.GONE);
        }
        bindVideoSlot(view, showInlineVideo);
        // lift the FABs above the mini-player bar while the bar is visible on
        // this page (issue #69 phase 2); restores automatically otherwise
        updateFabBottomMargins(view);
        mAppBarSV.setTitle(mRssChannel.feedName);
        updateFavoriteIcon(mRssItem.isFavorite);
        // refresh the overflow entry too: an in-place swap can land on an item
        // with different video availability than the one the page opened with
        if (mDownloadVideoMenuItem != null) {
            mDownloadVideoMenuItem.setVisible(mRssItem.mediaVideo != null);
        }
        ScrollView scrollView = view.findViewById(R.id.scroll_content);
        scrollView.scrollTo(0, 0);
        // the user's reading preferences are the last word on how the article
        // renders: apply them after every bind so a freshly bound item never shows
        // the layout's hardcoded 24sp/18sp baseline
        ReadingTypography typography = ReadingTypography.from(mAppSharedPreferences);
        typography.applyToBody(view.findViewById(R.id.text_content));
        typography.applyToTitle(view.findViewById(R.id.text_title));
        // auto-fetch full text (issue #28, opt-in): when the setting is on and
        // nothing is cached for this item's link yet, kick off a fetch. The
        // subscription dies with the page, and the command is app-scoped: even
        // if the user navigates away, the content still lands in the database
        // for the next open.
        maybeAutoFetchFullText();
        // PiP chrome (issue #69 phase 2, R4): a PiP window resize fires
        // onConfigurationChanged -> reBuildAllRoute, so a REBUILT view must
        // hide its reading chrome too; outside PiP this restores the
        // binding-driven visibilities set above untouched.
        applyPipChrome(activity);
    }

    /**
     * Renders the ORIGINAL feed description into text_content (the
     * non-reader-mode view of the article body). Reader mode and the
     * description share the exact same treatment: HtmlCompat.fromHtml
     * (FROM_HTML_MODE_LEGACY), URL spans routed through the "Open links in"
     * preference and the user's reading typography.
     */
    private void renderDescription() {
        if (mPageView == null || mRssItem == null) {
            return;
        }
        TextView textView = mPageView.findViewById(R.id.text_content);
        String desc = mRssItem.description;
        if (desc != null && !desc.isEmpty()) {
            textView.setText(HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
            // the description's URLSpans would fire a raw ACTION_VIEW internally;
            // swap them for spans that honor the "Open links in" preference (issue #35)
            UiUtils.routeUrlSpans(textView);
        } else {
            // clear leftover content when re-binding to an item without a description
            textView.setText("");
        }
        ReadingTypography.from(mAppSharedPreferences).applyToBody(textView);
        ScrollView scrollView = mPageView.findViewById(R.id.scroll_content);
        scrollView.scrollTo(0, 0);
    }

    /**
     * Auto-fetch full text (issue #28): when the opt-in setting is on, the
     * item has a fetchable link and no fetch is running, look the content up
     * first - a cache hit switches to reader mode immediately, a miss starts
     * the fetch. The manual toolbar action has its own cache-first path
     * ({@link #openReaderMode()}); both meet in the same render/fetch paths.
     */
    private void maybeAutoFetchFullText() {
        if (mAppSharedPreferences == null || !mAppSharedPreferences.isFetchFullTextAutomatically()) {
            return;
        }
        if (mRssItem == null || mFetchInFlight) {
            return;
        }
        String link = mRssItem.link;
        if (link == null || link.trim().isEmpty()) {
            return;
        }
        mRxDisposer.add("autoFetchArticleContent_lookup",
                mRssQueryCmd.getArticleContentByLink(link)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(articleContentOptional -> {
                            if (mRssItem == null || mFetchInFlight) {
                                return;
                            }
                            if (articleContentOptional.isPresent()) {
                                renderReaderContent(articleContentOptional.get());
                            } else {
                                startFullTextFetch(false);
                            }
                        }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    /**
     * Enters reader mode from the toolbar action (issue #28, cache-first):
     * looks the item's link up in the {@code article_content} cache FIRST - a
     * cached row renders immediately (so tapping "Read full article" offline
     * restores the article), then, only while the device is online, a silent
     * background refresh re-fetches the page. With no cached row the plain
     * network fetch runs in the foreground (loading indicator, failure toast),
     * exactly like before the cache-first change. The lookup error also falls
     * back to the network fetch: the tap must not die silently on a db hiccup.
     */
    private void openReaderMode() {
        if (mFetchInFlight || mRssItem == null) {
            return;
        }
        String link = mRssItem.link;
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(mSvProvider.getContext(),
                    R.string.full_article_fetch_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        mRxDisposer.add("openReaderMode_lookup",
                mRssQueryCmd.getArticleContentByLink(link)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(articleContentOptional -> {
                            if (mRssItem == null || mFetchInFlight) {
                                return;
                            }
                            if (articleContentOptional.isPresent()) {
                                renderReaderContent(articleContentOptional.get());
                                maybeStartBackgroundRefresh();
                            } else {
                                startFullTextFetch(true);
                            }
                        }, throwable -> {
                            mLogger.e(TAG, throwable.getMessage(), throwable);
                            startFullTextFetch(true);
                        }));
    }

    /**
     * Silently re-fetches the current item's already-rendered content in the
     * background (issue #28): runs only while the device is online (checked
     * synchronously through {@link DeviceStatusNotifier#isOnline()}, the same
     * state SyncRssCmd gates on) and at most once at a time. No loading
     * indicator and no failure toast - the cached content is already on
     * screen, so a refresh is best-effort. The completion re-renders through
     * {@link #loadAndRenderFullContent()}, whose comparison skips the render
     * when the fetched content equals what is displayed: an unchanged refresh
     * never jumps the scroll position, and a user who toggled back to the
     * description meanwhile is never flipped back to reader mode.
     */
    private void maybeStartBackgroundRefresh() {
        if (mBackgroundRefreshInFlight || mFetchInFlight || mRssItem == null) {
            return;
        }
        if (!mSvProvider.get(DeviceStatusNotifier.class).isOnline()) {
            return;
        }
        mBackgroundRefreshInFlight = true;
        mRxDisposer.add("backgroundRefreshArticleContent",
                mFetchArticleContentCmd.executeForce(mRssItem)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(success -> {
                            mBackgroundRefreshInFlight = false;
                            if (success && mReaderMode) {
                                // the command's articleContentUpdated event is
                                // ignored while the refresh is in flight -
                                // load here instead; unchanged content skips
                                // the render
                                loadAndRenderFullContent();
                            }
                        }, throwable -> {
                            mBackgroundRefreshInFlight = false;
                            mLogger.e(TAG, throwable.getMessage(), throwable);
                        }));
    }

    /**
     * Starts the FOREGROUND full-text fetch (issue #28): guards re-entry with
     * {@code mFetchInFlight}, shows the loading indicator and runs the command.
     * Reached from the toolbar action only when the cache-first lookup found
     * no row (or the lookup itself failed), and from auto-fetch on a cache
     * miss. A successful fetch resolves through the notifier event
     * (articleContentUpdated) which re-enters via loadAndRenderFullContent();
     * a failure toasts and returns the page to idle description rendering.
     *
     * @param force true to re-fetch even when content is already cached,
     *              false to skip when cached
     */
    private void startFullTextFetch(boolean force) {
        if (mFetchInFlight || mRssItem == null) {
            return;
        }
        String link = mRssItem.link;
        if (link == null || link.trim().isEmpty()) {
            Toast.makeText(mSvProvider.getContext(),
                    R.string.full_article_fetch_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        mFetchInFlight = true;
        setLoadingIndicator(true);
        mRxDisposer.add("fetchArticleContent",
                (force ? mFetchArticleContentCmd.executeForce(mRssItem)
                        : mFetchArticleContentCmd.execute(mRssItem))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(success -> {
                            resetFetchState();
                            if (success) {
                                // the command already emitted
                                // articleContentUpdated, which loadAndRenderFullContent
                                // is subscribed to - but this page might not be
                                // the one that triggered the fetch (or the event
                                // may have raced ahead), so load directly too;
                                // the lookup is idempotent
                                loadAndRenderFullContent();
                            } else {
                                Toast.makeText(mSvProvider.getContext(),
                                        R.string.full_article_fetch_failed,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }, throwable -> {
                            resetFetchState();
                            mLogger.e(TAG, throwable.getMessage(), throwable);
                            Toast.makeText(mSvProvider.getContext(),
                                    R.string.full_article_fetch_failed,
                                    Toast.LENGTH_SHORT).show();
                        }));
    }

    /**
     * Loads the cached article content of the current item's link from the
     * database (through RssQueryCmd, the page's existing live-lookup path) and
     * switches to reader mode when present. Used by the notifier subscription,
     * the foreground fetch-completion path and the background refresh
     * completion. While reader mode is active, content identical to what is
     * already rendered is skipped: the redundant notifier event of a
     * foreground fetch and a no-change background refresh then never re-render
     * (no scroll jump).
     */
    private void loadAndRenderFullContent() {
        if (mRssItem == null) {
            return;
        }
        String link = mRssItem.link;
        if (link == null || link.trim().isEmpty()) {
            return;
        }
        mRxDisposer.add("loadArticleContent",
                mRssQueryCmd.getArticleContentByLink(link)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(articleContentOptional -> {
                            if (articleContentOptional.isPresent()) {
                                ArticleContent articleContent = articleContentOptional.get();
                                if (mReaderMode && isRenderedReaderContent(articleContent)) {
                                    return;
                                }
                                renderReaderContent(articleContent);
                            }
                        }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    /**
     * Returns true when the given content row's body equals the reader-mode
     * content currently rendered (issue #28): the background refresh uses the
     * check to skip re-rendering unchanged content.
     *
     * @param articleContent a non-null cached content row of the current link
     * @return true when its content equals the rendered content string
     */
    private boolean isRenderedReaderContent(ArticleContent articleContent) {
        String rendered = mRenderedArticleContent;
        return rendered != null
                && articleContent.content != null
                && rendered.equals(articleContent.content);
    }

    /**
     * Switches the page into reader mode and renders the extracted article
     * HTML into text_content. The heavy HTML-to-span conversion runs on the
     * executor (large documents), only the resulting CharSequence is posted to
     * the main thread via the page's disposed Rx subscription. The finished
     * content receives the identical treatment as the description: URL spans
     * routed through the "Open links in" preference and the user's reading
     * typography.
     *
     * @param articleContent the cached content row of the current item's link
     */
    private void renderReaderContent(ArticleContent articleContent) {
        if (mPageView == null || articleContent == null) {
            return;
        }
        mReaderMode = true;
        // remember what is on screen: the background refresh (issue #28)
        // compares against this before re-rendering
        mRenderedArticleContent = articleContent.content;
        updateReadArticleMenuItem();
        mRxDisposer.add("renderArticleContent",
                Single.fromCallable(() -> HtmlCompat.fromHtml(articleContent.content,
                        HtmlCompat.FROM_HTML_MODE_LEGACY))
                        .subscribeOn(Schedulers.from(mExecutorService))
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(spanned -> {
                            if (mPageView == null) {
                                return;
                            }
                            TextView textView = mPageView.findViewById(R.id.text_content);
                            textView.setText(spanned);
                            textView.setMovementMethod(LinkMovementMethod.getInstance());
                            UiUtils.routeUrlSpans(textView);
                            ReadingTypography.from(mAppSharedPreferences).applyToBody(textView);
                            ScrollView scrollView =
                                    mPageView.findViewById(R.id.scroll_content);
                            scrollView.scrollTo(0, 0);
                        }, throwable -> mLogger.e(TAG, throwable.getMessage(), throwable)));
    }

    /**
     * Returns the page to the original feed description (reader mode off).
     */
    private void showOriginalDescription() {
        mReaderMode = false;
        mRenderedArticleContent = null;
        updateReadArticleMenuItem();
        renderDescription();
    }

    /**
     * Swaps the toolbar action's label to reflect the mode: "Read full
     * article" while the description is shown, "Show original description"
     * while reader mode is active.
     */
    private void updateReadArticleMenuItem() {
        if (mReadFullArticleMenuItem != null) {
            mReadFullArticleMenuItem.setTitle(mReaderMode
                    ? R.string.menu_show_original_description
                    : R.string.menu_read_full_article);
        }
    }

    private void setLoadingIndicator(boolean loading) {
        if (mPageView != null) {
            View progress = mPageView.findViewById(R.id.progress_article);
            if (progress != null) {
                progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void resetFetchState() {
        mFetchInFlight = false;
        setLoadingIndicator(false);
    }

    /**
     * Fills the detail page's single-line category chip strip. Chips never wrap and
     * overflow scrolls horizontally (same pattern as the list-row strip); the strip
     * stays hidden when the item has no categories.
     *
     * @param activity            the current activity, used to inflate the chips
     * @param scrollCategories    the strip's scroll container
     * @param containerCategories the container hosting the chips
     */
    private void bindCategoryChips(Activity activity, HorizontalScrollView scrollCategories,
                                   LinearLayout containerCategories) {
        containerCategories.removeAllViews();
        List<String> categories = RssItemCategories.parse(mRssItem.categories);
        if (categories.isEmpty()) {
            scrollCategories.setVisibility(View.GONE);
            return;
        }
        scrollCategories.setVisibility(View.VISIBLE);
        int chipSpacingPx = (int) (CHIP_SPACING_DP
                * activity.getResources().getDisplayMetrics().density + 0.5f);
        for (String category : categories) {
            Chip chip = new Chip(activity);
            chip.setText(category);
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setFocusable(true);
            // 48dp TOUCH target via Material's touch delegate; the visual pill keeps
            // the ~32dp Material default height (text stays at the 14sp chip default)
            chip.setEnsureMinTouchTargetSize(true);
            // deterministic, uniform 8dp gap on every chip (including the last)
            LinearLayout.LayoutParams chipLayoutParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipLayoutParams.setMarginEnd(chipSpacingPx);
            chip.setLayoutParams(chipLayoutParams);
            chip.setContentDescription(
                    activity.getString(R.string.search_articles_in_category, category));
            chip.setOnClickListener(v -> mNavigator.push(Routes.SEARCH_RSS_PAGE,
                    SearchRssPage.Args.withSearchTerm(category), null, null));
            containerCategories.addView(chip);
        }
    }

    /**
     * SEMANTICS CHANGE (issue #69 phase 2): the page no longer owns the media
     * controller. Phase 1 released the page-scoped controller here; playback
     * is now app-scoped ({@link PlaybackManager}) and OUTLIVES this page -
     * the mini-player bar and picture-in-picture keep using the session.
     * dispose only detaches the page's UI from the session:
     * <ul>
     * <li>the RxDisposer disposal (below, via mSvProvider) ends the playback
     * state subscription</li>
     * <li>the inline video slot's PlayerView is detached from the shared
     * controller (the session itself keeps playing)</li>
     * </ul>
     * The SERVICE stops via the media notification's deleteIntent, the
     * mini-player bar's close button ({@link PlaybackManager#stop()}), the
     * playback-ENDED release, or onTaskRemoved-when-idle.
     */
    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mPageView != null) {
            PlayerView playerView = mPageView.findViewById(R.id.player_view);
            if (playerView != null) {
                playerView.setPlayer(null);
                playerView.setOnClickListener(null);
            }
        }
        mPlaybackManager = null;
        mVideoUrlPolicy = null;
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        mAppBarSV.dispose(activity);
        mAppBarSV = null;
        mRssItem = null;
        mRssChannel = null;
        mAppSharedPreferences = null;
        mSwipeGestureDetector = null;
        mRxDisposer = null;
        mUpdateRssItemIsFavoriteCmd = null;
        mUpdateRssItemIsReadCmd = null;
        mPagedRssItemsCmd = null;
        mRssQueryCmd = null;
        mFetchArticleContentCmd = null;
        mToggleFavoriteMenuItem = null;
        mDownloadVideoMenuItem = null;
        mReadFullArticleMenuItem = null;
        mPageView = null;
        // reader-mode page state never outlives the page
        mReaderMode = false;
        mFetchInFlight = false;
        mBackgroundRefreshInFlight = false;
        mRenderedArticleContent = null;
        // protects against a late navigateToNeighbor callback after this page was
        // already replaced by another detail page instance
        mNeighborNavigationInProgress = false;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.text_title || viewId == R.id.fab_open_link) {
            UiUtils.openLink(UiUtils.getActivity(view), mRssItem.link);
        } else if (viewId == R.id.fab_open_video) {
            // media enclosure / YouTube deep link: deliberately EXCLUDED from the
            // "Open links in" setting (issue #35) - a Custom Tab would regress them
            Activity activity = UiUtils.getActivity(view);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.mediaVideo));
            activity.startActivity(browserIntent);
        } else if (viewId == R.id.fab_play_audio) {
            // routed through the app-scoped manager (issue #69 phase 2): the
            // decision (pause / resume in place / fresh load) is made against
            // the SHARED session, not this page's view
            if (mRssItem != null && mRssItem.mediaAudio != null) {
                mPlaybackManager.togglePlay(mRssItem, mRssChannel, mRssItem.mediaAudio, false);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        if (id == R.id.menu_edit_link) {
            mNavigator.push((args, activity) -> new EditRssLinkSVDialog(),
                    EditRssLinkSVDialog.Args.newArgs(mRssItem),
                    (navigator, navRoute, activity, currentView) -> {
                        StatefulView sv = navigator.getCurrentRoute().getStatefulView();
                        if (sv instanceof RssItemDetailPage) {
                            Provider provider = (Provider) navigator.getNavConfiguration().getRequiredComponent();
                            CompositeDisposable compositeDisposable = new CompositeDisposable();
                            compositeDisposable.add(provider.get(RssQueryCmd.class)
                                    .getRssItemById(((RssItemDetailPage) sv).mRssItem.id)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe((rssItem, throwable) -> {
                                        if (throwable != null) {
                                            provider.get(ILogger.class).e(TAG, throwable.getMessage(), throwable);
                                        } else {
                                            ((RssItemDetailPage) sv).mRssItem = rssItem;
                                        }
                                        compositeDisposable.dispose();
                                    })
                            );
                        }
                    });
        } else if (id == R.id.menu_copy_link) {
            Context context = mSvProvider.getContext();
            String link = mRssItem.link;
            // copy the SANITIZED link when the toggle is on (issue #76): a local
            // copy only - mRssItem.link stays untouched as the read/favorite/
            // dedup key. Read at click time, like openLink does.
            if (mAppSharedPreferences.isStripTrackingParamsEnabled()) {
                link = mSvProvider.get(UrlSanitizer.class).sanitize(link);
            }
            boolean copied = UiUtils.copyToClipboard(context, context.getString(R.string.menu_copy_link), link);
            Toast.makeText(context,
                    copied ? R.string.copied_to_clipboard : android.R.string.cancel,
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.menu_toggle_favorite) {
            Context context = mSvProvider.getContext();
            boolean newIsFavorite = !mRssItem.isFavorite;
            mUpdateRssItemIsFavoriteCmd.execute(mRssItem, newIsFavorite);
            updateFavoriteIcon(newIsFavorite);
            Toast.makeText(context,
                    newIsFavorite ? R.string.favorite_added : R.string.favorite_removed,
                    Toast.LENGTH_SHORT).show();
        } else if (id == R.id.menu_previous_article) {
            navigateToNeighbor(false);
        } else if (id == R.id.menu_next_article) {
            navigateToNeighbor(true);
        } else if (id == R.id.menu_download_video) {
            Context context = mSvProvider.getContext().getApplicationContext();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: scoped storage — DownloadManager needs no permission
                downloadMediaFile();
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                // API 21-28: permission already granted
                downloadMediaFile();
            } else {
                // API 21-28: request permission before downloading
                ActivityCompat.requestPermissions(mNavigator.getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE);
            }

        } else if (id == R.id.menu_read_full_article) {
            // reader mode toggle (issue #28): while the full article is
            // displayed the same action returns to the original description;
            // entering reader mode is CACHE-FIRST: a cached row renders
            // immediately (restores the article offline too) and, while the
            // device is online, refreshes from the network in the background;
            // with no cached row it fetches from the network regardless of
            // the auto-fetch setting
            if (mReaderMode) {
                showOriginalDescription();
            } else {
                openReaderMode();
            }
        } else if (id == R.id.menu_reading_settings) {
            // door into the reading settings while the article stays behind it;
            // the mRxDisposer subscription in createView() re-applies the new
            // typography when this page is shown again on pop
            mNavigator.push(Routes.SETTINGS_PAGE);
        }
        return false;
    }

    @Override
    public void onMenuCreated(Menu menu) {
        mDownloadVideoMenuItem = menu.findItem(R.id.menu_download_video);
        mDownloadVideoMenuItem.setVisible(mRssItem.mediaVideo != null);
        mToggleFavoriteMenuItem = menu.findItem(R.id.menu_toggle_favorite);
        mToggleFavoriteMenuItem.setIcon(mRssItem.isFavorite ?
                R.drawable.ic_star_filled_white : R.drawable.ic_star_outline_white);
        mReadFullArticleMenuItem = menu.findItem(R.id.menu_read_full_article);
        updateReadArticleMenuItem();
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator INavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadMediaFile();
            } else {
                mLogger.i(TAG, activity.getString(R.string.error_permission_denied));
            }
        }
    }

    /**
     * Swaps the favorite menu icon to reflect the given favorite state.
     *
     * @param isFavorite the favorite state to reflect
     */
    private void updateFavoriteIcon(boolean isFavorite) {
        if (mToggleFavoriteMenuItem != null) {
            mToggleFavoriteMenuItem.setIcon(isFavorite ?
                    R.drawable.ic_star_filled_white : R.drawable.ic_star_outline_white);
        }
    }

    /**
     * Navigates to the neighboring article in the home list context (channel,
     * filter and sort order as currently held by {@link PagedRssItemsCmd}), the
     * same list the user opened this page from. Direction follows the list's sort
     * order: in a newest-first list "next" walks to the older item, and the
     * mapping inverts when the list is sorted oldest-first. At the edges of the
     * list a short toast is shown and the page stays put.
     * <p>
     * Duplicate-story collapsing (issue #45): when the home list shows the
     * collapsed all-channels view (no channel selected AND the collapse setting
     * on), the GROUPED neighbor queries are used so next/previous walks exactly
     * the visible collapsed order - each step lands on the neighboring group's
     * representative, skipping the duplicate copies the list hides. In every
     * other context (a channel is selected, or collapsing is off) the anchor item
     * itself is a plain list row and the flat neighbor queries apply. The anchor
     * is always the item the page currently shows, which the list itself
     * dispatched here - a representative in grouped contexts, a plain row
     * otherwise - so both walks are exact.
     *
     * @param next true to walk to the next article, false for the previous one
     */
    private void navigateToNeighbor(boolean next) {
        if (mNeighborNavigationInProgress) {
            return;
        }
        mNeighborNavigationInProgress = true;
        Integer filterType = mPagedRssItemsCmd.getFilterType().orElse(null);
        Integer isRead = BaseRssItemsCmd.toIsRead(filterType);
        Integer isFavorite = BaseRssItemsCmd.toIsFavorite(filterType);
        Long channelId = mPagedRssItemsCmd.getSelectedChannelId();
        Integer sortOrder = mPagedRssItemsCmd.getSortOrder();
        boolean asc = sortOrder != null && sortOrder == BaseRssItemsCmd.SORT_ORDER_OLDEST;
        // newest-first (DESC) list: next = older neighbor; oldest-first (ASC): next = newer
        boolean findOlder = next != asc;
        boolean collapsedListContext = channelId == null
                && mAppSharedPreferences.isCollapseDuplicateStories();
        Single<Optional<RssItem>> neighborSingle;
        if (collapsedListContext) {
            neighborSingle = findOlder
                    ? mRssQueryCmd.findOlderRssItemGroupedByLink(channelId, isRead, isFavorite, mRssItem)
                    : mRssQueryCmd.findNewerRssItemGroupedByLink(channelId, isRead, isFavorite, mRssItem);
        } else {
            neighborSingle = findOlder
                    ? mRssQueryCmd.findOlderRssItem(channelId, isRead, isFavorite, mRssItem)
                    : mRssQueryCmd.findNewerRssItem(channelId, isRead, isFavorite, mRssItem);
        }
        mRxDisposer.add(next ? "navigateToNeighbor_next" : "navigateToNeighbor_prev",
                neighborSingle
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(neighborOptional -> {
                            if (neighborOptional.isPresent()) {
                                openNeighbor(neighborOptional.get());
                            } else {
                                // edge of the home list, nothing to walk to
                                Toast.makeText(mSvProvider.getContext(),
                                        R.string.no_more_articles, Toast.LENGTH_SHORT).show();
                            }
                            // reset here (end of onNext), NOT inside openNeighbor's
                            // callbacks: openNeighbor's error branch only logs and
                            // would never reset (stuck-guard risk). Resetting before
                            // openNeighbor's async channel-query hop is safe because
                            // openNeighbor subscribes under the single shared key
                            // "openNeighbor_getRssChannelById", where a re-add
                            // disposes the prior pending replace — no double-replace.
                            mNeighborNavigationInProgress = false;
                        }, throwable -> {
                            mLogger.e(TAG, throwable.getMessage(), throwable);
                            mNeighborNavigationInProgress = false;
                        }));
    }

    /**
     * Marks the given neighbor read before navigating (mirrors the list's
     * open-detail click flow), loads its channel, then replaces this page with
     * the neighbor's, so the back stack still returns to the list.
     *
     * @param neighbor the neighboring rss item to open
     */
    private void openNeighbor(RssItem neighbor) {
        if (!neighbor.isRead) {
            mUpdateRssItemIsReadCmd.execute(neighbor, true);
        }
        mRxDisposer.add("openNeighbor_getRssChannelById",
                mRssQueryCmd.getRssChannelById(neighbor.channelId)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe((rssChannel, throwable) -> {
                            if (throwable != null) {
                                mLogger.e(TAG, throwable.getMessage(), throwable);
                            } else {
                                mNavigator.replace(Routes.RSS_ITEM_DETAIL_PAGE,
                                        Args.withRss(neighbor, rssChannel), null,
                                        RssItemDetailPage.detailPageRouteOptions());
                            }
                        }));
    }

    /**
     * Volume-key article navigation (issue #60), gated in MainActivity by the
     * volumeKeyNavigationEnabled setting: volume down walks to the next article
     * ("read forward"), volume up to the previous one. Reuses the exact path of
     * the {@code menu_next_article}/{@code menu_previous_article} toolbar
     * actions. Note: TalkBack's own volume-key reading shortcuts (a user setting)
     * may consume volume keys before they reach the app — not a regression.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            navigateToNeighbor(true); // volume down = next ("read forward")
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            navigateToNeighbor(false); // volume up = previous
            return true;
        }
        return false;
    }

    private void downloadMediaFile() {
        Context context = mSvProvider.getContext().getApplicationContext();
        String url = mRssItem.mediaVideo;
        String title = mRssItem.title;
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(ext);
        String subDir = mRssChannel.feedName + "/" + title + "." + ext;
        mExecutorService.execute(() -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                        .setTitle(title)
                        .setMimeType(mimeType)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subDir);
                DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                downloadManager.enqueue(request);
                mLogger.i(TAG, context.getString(R.string.begin_downloading));
            } catch (Exception e) {
                mLogger.e(TAG, e.getMessage(), e);
            }
        });
    }

    /**
     * True when the current item's video enclosure is probably a directly
     * playable file (issue #69 phase 2) - the only case rendered inline; hub
     * URLs (YouTube, Vimeo, ...) stay on the open-in-browser FAB.
     */
    private boolean isDirectVideoItem() {
        return mRssItem != null && mRssItem.mediaVideo != null
                && mVideoUrlPolicy.isProbablyDirectVideoUrl(mRssItem.mediaVideo);
    }

    /**
     * Binds the inline video slot (issue #69 phase 2): player_view + hero
     * image + open-video FAB + swipe strip as one mutually consistent set,
     * driven by the given inline-video decision. Called from bindRssItem
     * (view build / neighbor swap) and from the playback state subscription
     * (video failure revert).
     * <p>
     * Single-player semantics: loading this video through the shared session
     * inherently replaces whatever audio is playing - the app owns exactly
     * one player (PlaybackManager).
     *
     * @param view            the page's content view
     * @param showInlineVideo true to render the video slot (a state already
     *                        marked failed for THIS video is downgraded back
     *                        to FAB-only here)
     */
    @OptIn(markerClass = UnstableApi.class)
    private void bindVideoSlot(View view, boolean showInlineVideo) {
        if (mRssItem == null) {
            return;
        }
        PlayerView playerView = view.findViewById(R.id.player_view);
        View networkImageView = view.findViewById(R.id.network_image);
        View swipeRegionRight = view.findViewById(R.id.container_swipe_region_right);
        PlaybackState state = mPlaybackManager.getCurrentState();
        // a fatal error for THIS video reverts the slot to FAB-only mode until
        // the next rebind (the toast itself is emitted by the state handler)
        if (showInlineVideo && state.failed && state.matchesUrl(mRssItem.mediaVideo)) {
            showInlineVideo = false;
        }
        if (showInlineVideo) {
            playerView.setVisibility(View.VISIBLE);
            // the video slot replaces the hero image, which keeps its own
            // isDownloadImage gate when the slot hides again
            networkImageView.setVisibility(View.GONE);
            view.findViewById(R.id.fab_open_video).setVisibility(View.GONE);
            // R7: the 64dp right-edge swipe strip overlays the player controls
            // and a swipe there would replace the page mid-video - hide it
            // while the video is inline, restore when the slot hides
            swipeRegionRight.setVisibility(View.GONE);
            // render the session-remote video: PlayerView accepts any Player,
            // including the app-scoped MediaController whose timeline crossed
            // the binder boundary (null while the session holds no controller)
            playerView.setPlayer(mPlaybackManager.getControllerIfConnected());
            // first tap on the surface (or tap after a failure) loads + plays
            // through the manager - strictly tap-initiated, no autoplay
            // anywhere. Once THIS video is loaded the built-in controller's
            // play/pause button drives pause/resume directly on the shared
            // session (PlayerView.performClick already toggled the controls
            // before this listener ran), and this tap must not toggle again.
            playerView.setOnClickListener(v -> {
                if (mRssItem == null || mRssItem.mediaVideo == null) {
                    return;
                }
                PlaybackState playbackState = mPlaybackManager.getCurrentState();
                if (!playbackState.matchesUrl(mRssItem.mediaVideo) || playbackState.failed) {
                    mPlaybackManager.togglePlay(mRssItem, mRssChannel,
                            mRssItem.mediaVideo, true);
                }
            });
        } else {
            playerView.setVisibility(View.GONE);
            playerView.setPlayer(null);
            playerView.setOnClickListener(null);
            // restore the hero image per its own gate (idempotent with the
            // bindRssItem path, correct on the failure-revert path)
            boolean showImage = mAppSharedPreferences != null
                    && mAppSharedPreferences.isDownloadImage()
                    && mRssItem.mediaImage != null;
            networkImageView.setVisibility(showImage ? View.VISIBLE : View.GONE);
            swipeRegionRight.setVisibility(View.VISIBLE);
            // restore the browser FAB too: the inline branch hid it, and the
            // failure-revert path lands HERE without a rebind - without this
            // restore a failed direct video left the page with no way to open
            // the video at all (E2E api24: 403 fixture hid both player_view
            // and fab_open_video)
            view.findViewById(R.id.fab_open_video).setVisibility(
                    mRssItem.mediaVideo != null ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Re-attaches the inline video slot to the CURRENT session controller.
     * Runs on every playback state emission while the slot is visible, so
     * controller-level events that change what the view should render (the
     * ENDED release nulls the controller, a reconnect replaces it) keep the
     * view consistent without a rebind.
     */
    @OptIn(markerClass = UnstableApi.class)
    private void refreshVideoPlayerAttachment() {
        if (mPageView == null || mRssItem == null) {
            return;
        }
        PlayerView playerView = mPageView.findViewById(R.id.player_view);
        if (playerView.getVisibility() == View.VISIBLE) {
            playerView.setPlayer(mPlaybackManager.getControllerIfConnected());
        }
    }

    /**
     * True while the inline video slot of this page is currently visible.
     */
    private boolean isVideoSlotShowing() {
        if (mPageView == null) {
            return false;
        }
        View playerView = mPageView.findViewById(R.id.player_view);
        return playerView != null && playerView.getVisibility() == View.VISIBLE;
    }

    /**
     * Playback-state handler (issue #69 phase 2): refreshes the audio FAB,
     * keeps the inline video slot attached to the session, toasts + reverts
     * the slot when THIS item's video fatally fails and re-evaluates the FAB
     * lift for the mini-player bar. Emissions arrive on the main thread.
     *
     * @param state the new app-wide playback state
     */
    private void onPlaybackStateChanged(PlaybackState state) {
        if (mPageView == null || mRssItem == null) {
            return;
        }
        updateAudioFabVisual(state);
        if (state.failed && state.item != null
                && state.matchesUrl(mRssItem.mediaVideo)
                && isVideoSlotShowing()) {
            Toast.makeText(mSvProvider.getContext(),
                    R.string.video_playback_failed, Toast.LENGTH_SHORT).show();
            bindVideoSlot(mPageView, isDirectVideoItem());
        } else {
            refreshVideoPlayerAttachment();
        }
        updateFabBottomMargins(mPageView);
    }

    /**
     * Refreshes the audio FAB's icon/label from the given playback state.
     *
     * @param state the current app-wide playback state
     */
    private void updateAudioFabVisual(PlaybackState state) {
        if (mPageView == null) {
            return;
        }
        updateAudioFabVisual(mPageView.findViewById(R.id.fab_play_audio), state);
    }

    /**
     * Updates the audio play/pause FAB's icon, text and content description.
     * The pause state shows ONLY while THIS item's audio is actively playing in
     * the shared session: another item's ongoing playback renders as a fresh
     * play state - binding a new article never hijacks audio the user is
     * already listening to, playback only moves on an explicit tap (issue #69).
     *
     * @param fabPlayAudio the page view's audio FAB
     * @param state        the current app-wide playback state
     */
    private void updateAudioFabVisual(ExtendedFloatingActionButton fabPlayAudio,
                                      PlaybackState state) {
        boolean isPlayingThisAudio = state != null
                && mRssItem != null
                && mRssItem.mediaAudio != null
                && state.matchesUrl(mRssItem.mediaAudio)
                && state.isPlaying;
        int labelRes = isPlayingThisAudio
                ? R.string.fab_pause_audio
                : R.string.fab_play_audio;
        String label = mSvProvider.getContext().getString(labelRes);
        fabPlayAudio.setIcon(ContextCompat.getDrawable(mSvProvider.getContext(),
                isPlayingThisAudio
                        ? R.drawable.ic_pause_white
                        : R.drawable.ic_play_arrow_white));
        fabPlayAudio.setText(label);
        fabPlayAudio.setContentDescription(label);
    }

    /**
     * Lifts the three FABs above the mini-player bar while the bar is visible
     * on THIS page (issue #69 phase 2), restoring the plain text_margin
     * baseline otherwise. "Bar visible on this page" is exactly the bar's own
     * visibility rule evaluated against this page's item (a session is loaded
     * AND its media is not this page's current audio/video): whenever this
     * page is the top route both computations agree, and margins computed
     * while the page is covered are refreshed by the next state emission
     * before the page can become visible again.
     */
    private void updateFabBottomMargins(View view) {
        if (view == null) {
            return;
        }
        PlaybackState state = mPlaybackManager.getCurrentState();
        boolean barVisibleOnThisPage = state != null && state.item != null
                && mRssItem != null
                && !state.matchesUrl(mRssItem.mediaAudio)
                && !state.matchesUrl(mRssItem.mediaVideo);
        int extraMarginPx = barVisibleOnThisPage
                ? (int) (MINI_PLAYER_BAR_LIFT_DP
                * mSvProvider.getContext().getResources()
                .getDisplayMetrics().density + 0.5f)
                : 0;
        applyExtraBottomMargin(view.findViewById(R.id.fab_play_audio), extraMarginPx);
        applyExtraBottomMargin(view.findViewById(R.id.fab_open_video), extraMarginPx);
        applyExtraBottomMargin(view.findViewById(R.id.fab_open_link), extraMarginPx);
    }

    /**
     * Adds the given extra pixels to one FAB's bottom margin (on top of the
     * {@code text_margin} baseline both detail layouts share).
     */
    private void applyExtraBottomMargin(View fab, int extraMarginPx) {
        if (fab == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = fab.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        int baseMarginPx = mSvProvider.getContext().getResources()
                .getDimensionPixelSize(R.dimen.text_margin);
        int newBottomMargin = baseMarginPx + extraMarginPx;
        if (marginLayoutParams.bottomMargin != newBottomMargin) {
            marginLayoutParams.bottomMargin = newBottomMargin;
            fab.setLayoutParams(marginLayoutParams);
        }
    }

    /**
     * PiP chrome refresh (issue #69 phase 2, R4): the toolbar and the FABs
     * are reading chrome, not video - hide them while the activity is in
     * picture-in-picture mode and restore them otherwise. The FAB
     * visibilities stay BINDING-driven (an item without audio/video keeps its
     * FAB hidden even outside PiP); the swipe strip stays hidden whenever the
     * inline video hides it (R7). Called from bindRssItem (covers the
     * reBuildAllRoute mid-PiP rebuild) and from the activity
     * paused/resumed forwards (the lifecycle boundary where
     * isInPictureInPictureMode() has settled).
     * <p>
     * PiP params themselves are NEVER computed here - they stay with
     * MainActivity, the activity-owned API.
     *
     * @param activity the current activity
     */
    private void applyPipChrome(Activity activity) {
        if (mPageView == null || mRssItem == null) {
            return;
        }
        // Activity#isInPictureInPictureMode exists since API 24; PiP itself is
        // an API 26+ feature so the flag can only be true far above minSdk 21
        boolean inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && activity.isInPictureInPictureMode();
        mPageView.findViewById(R.id.container_app_bar)
                .setVisibility(inPip ? View.GONE : View.VISIBLE);
        updateFabVisibility(mPageView.findViewById(R.id.fab_play_audio),
                mRssItem.mediaAudio != null, inPip);
        updateFabVisibility(mPageView.findViewById(R.id.fab_open_video),
                mRssItem.mediaVideo != null && !isDirectVideoItem(), inPip);
        updateFabVisibility(mPageView.findViewById(R.id.fab_open_link), true, inPip);
        boolean swipeVisible = !inPip && !isVideoSlotShowing();
        mPageView.findViewById(R.id.container_swipe_region_right)
                .setVisibility(swipeVisible ? View.VISIBLE : View.GONE);
    }

    /**
     * Applies one FAB's binding-driven visibility, suppressed in PiP.
     */
    private void updateFabVisibility(View fab, boolean bindingVisible, boolean inPip) {
        if (fab == null) {
            return;
        }
        fab.setVisibility(bindingVisible && !inPip ? View.VISIBLE : View.GONE);
    }

    /**
     * Activity resumed (issue #69 phase 2, PiP chrome only): re-evaluates
     * which chrome this page shows for the current picture-in-picture state.
     * No PiP params are touched here (MainActivity owns them).
     */
    @Override
    public void onNavActivityResumed(Activity activity) {
        applyPipChrome(activity);
    }

    /**
     * Activity paused (issue #69 phase 2, PiP chrome only): entering
     * picture-in-picture pauses the activity - hide this page's reading
     * chrome for the shrunken window. No PiP params are touched here.
     */
    @Override
    public void onNavActivityPaused(Activity activity) {
        applyPipChrome(activity);
    }

    public static class Args implements Serializable {
        public static Args withRss(RssItem rssItem, RssChannel rssChannel) {
            Args args = new Args();
            args.mRssItem = rssItem;
            args.mRssChannel = rssChannel;
            return args;
        }

        public static Args of(NavRoute navRoute) {
            if (navRoute != null) {
                return of(navRoute.getRouteArgs());
            }
            return null;
        }

        public static Args of(Serializable serializable) {
            if (serializable instanceof Args) {
                return (Args) serializable;
            }
            return null;
        }

        private RssItem mRssItem;
        private RssChannel mRssChannel;

        public RssItem getRssItem() {
            return mRssItem;
        }

        public RssChannel getRssChannel() {
            return mRssChannel;
        }
    }
}
