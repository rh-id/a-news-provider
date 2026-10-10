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

import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.google.android.material.chip.Chip;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.constants.Routes;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.BaseRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.PagedRssItemsCmd;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsFavoriteCmd;
import m.co.rh.id.a_news_provider.app.provider.command.UpdateRssItemIsReadCmd;
import m.co.rh.id.a_news_provider.app.provider.notifier.RssChangeNotifier;
import m.co.rh.id.a_news_provider.app.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.EditRssLinkSVDialog;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.entity.RssItemCategories;
import m.co.rh.id.a_news_provider.base.ui.SwipeGestureDetector;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.RouteOptions;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class RssItemDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnRequestPermissionResult, View.OnClickListener, Toolbar.OnMenuItemClickListener, AppBarSV.OnMenuCreated {

    private static final String TAG = RssItemDetailPage.class.getName();
    private static final int REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 1;

    /**
     * Uniform gap between category chips in the detail strip, applied as marginEnd
     * on every chip (including the last) - same spacing as the list-row strip.
     */
    private static final float CHIP_SPACING_DP = 8f;

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
    private transient MenuItem mToggleFavoriteMenuItem;
    private transient MenuItem mDownloadVideoMenuItem;

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
        // PagedRssItemsCmd must come from the GLOBAL provider: the home list uses the
        // same app-scoped instance (see RssItemListSV), so its channel/filter/sort
        // describe exactly the list the user navigated from. The page-scoped provider
        // would return a fresh instance with default filter/sort instead.
        mPagedRssItemsCmd = provider.get(PagedRssItemsCmd.class);
        mSwipeGestureDetector = new SwipeGestureDetector(provider.getContext()) {
            @Override
            public void onSwipeRight() {
                // the setting gates the whole edge-gesture pair: back-swipe and
                // next-swipe turn on and off together
                if (mAppSharedPreferences.isSwipeArticleNavigationEnabled()) {
                    mNavigator.pop();
                }
            }

            @Override
            public void onSwipeLeft() {
                // swipe left from the right edge = next article, same setting;
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
        // edge gesture strips overlay the content sides; both directions are
        // gated by the swipe setting (read live in the detector): fling outward
        // from the left edge = back, fling left from the right edge = next
        view.findViewById(R.id.container_swipe_region)
                .setOnTouchListener(mSwipeGestureDetector);
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
        Button fabOpenLink = view.findViewById(R.id.fab_open_link);
        fabOpenLink.setOnClickListener(this);
        Button fabOpenVideo = view.findViewById(R.id.fab_open_video);
        fabOpenVideo.setOnClickListener(this);
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
        boolean showImage = mAppSharedPreferences.isDownloadImage() && imageUrl != null;
        if (showImage) {
            Drawable drawable = DrawableCompat.wrap(ContextCompat
                    .getDrawable(activity, R.drawable.ic_image_black));
            DrawableCompat.setTint(drawable, ContextCompat
                    .getColor(activity, R.color.daynight_black_white));
            networkImageView.setDefaultImageDrawable(drawable);
            networkImageView.setErrorImageResId(R.drawable.ic_broken_image_red);
            networkImageView.setImageUrl(imageUrl, mImageLoader);
            networkImageView.setVisibility(View.VISIBLE);
        } else {
            networkImageView.setVisibility(View.GONE);
        }
        TextView textView = view.findViewById(R.id.text_content);
        String desc = mRssItem.description;
        if (desc != null && !desc.isEmpty()) {
            textView.setText(HtmlCompat.fromHtml(desc, HtmlCompat.FROM_HTML_MODE_LEGACY));
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            // clear leftover content when re-binding to an item without a description
            textView.setText("");
        }
        HorizontalScrollView scrollCategories = view.findViewById(R.id.scroll_categories);
        LinearLayout containerCategories = view.findViewById(R.id.container_categories);
        bindCategoryChips(activity, scrollCategories, containerCategories);
        Button fabOpenVideo = view.findViewById(R.id.fab_open_video);
        if (mRssItem.mediaVideo != null) {
            fabOpenVideo.setVisibility(View.VISIBLE);
        } else {
            fabOpenVideo.setVisibility(View.GONE);
        }
        mAppBarSV.setTitle(mRssChannel.feedName);
        updateFavoriteIcon(mRssItem.isFavorite);
        // refresh the overflow entry too: an in-place swap can land on an item
        // with different video availability than the one the page opened with
        if (mDownloadVideoMenuItem != null) {
            mDownloadVideoMenuItem.setVisible(mRssItem.mediaVideo != null);
        }
        ScrollView scrollView = view.findViewById(R.id.scroll_content);
        scrollView.scrollTo(0, 0);
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

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
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
        mToggleFavoriteMenuItem = null;
        mDownloadVideoMenuItem = null;
    }

    @Override
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.text_title || viewId == R.id.fab_open_link) {
            Activity activity = UiUtils.getActivity(view);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.link));
            activity.startActivity(browserIntent);
        } else if (viewId == R.id.fab_open_video) {
            Activity activity = UiUtils.getActivity(view);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(mRssItem.mediaVideo));
            activity.startActivity(browserIntent);
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
     *
     * @param next true to walk to the next article, false for the previous one
     */
    private void navigateToNeighbor(boolean next) {
        Integer filterType = mPagedRssItemsCmd.getFilterType().orElse(null);
        Integer isRead = BaseRssItemsCmd.toIsRead(filterType);
        Integer isFavorite = BaseRssItemsCmd.toIsFavorite(filterType);
        Long channelId = mPagedRssItemsCmd.getSelectedChannelId();
        Integer sortOrder = mPagedRssItemsCmd.getSortOrder();
        boolean asc = sortOrder != null && sortOrder == BaseRssItemsCmd.SORT_ORDER_OLDEST;
        // newest-first (DESC) list: next = older neighbor; oldest-first (ASC): next = newer
        boolean findOlder = next != asc;
        Single<Optional<RssItem>> neighborSingle = findOlder
                ? mRssQueryCmd.findOlderRssItem(channelId, isRead, isFavorite, mRssItem)
                : mRssQueryCmd.findNewerRssItem(channelId, isRead, isFavorite, mRssItem);
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
                        }, throwable ->
                                mLogger.e(TAG, throwable.getMessage(), throwable)));
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
