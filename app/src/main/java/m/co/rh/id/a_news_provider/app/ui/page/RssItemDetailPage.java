package m.co.rh.id.a_news_provider.app.ui.page;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Environment;
import android.text.method.LinkMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.text.HtmlCompat;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.command.RssQueryCmd;
import m.co.rh.id.a_news_provider.app.ui.component.AppBarSV;
import m.co.rh.id.a_news_provider.app.ui.component.rss.EditRssLinkSVDialog;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.base.AppSharedPreferences;
import m.co.rh.id.a_news_provider.base.entity.RssChannel;
import m.co.rh.id.a_news_provider.base.entity.RssItem;
import m.co.rh.id.a_news_provider.base.ui.SwipeGestureDetector;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulView;
import m.co.rh.id.anavigator.annotation.NavInject;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.aprovider.Provider;

public class RssItemDetailPage extends StatefulView<Activity> implements RequireComponent<Provider>, NavOnRequestPermissionResult, View.OnClickListener, Toolbar.OnMenuItemClickListener, AppBarSV.OnMenuCreated {

    private static final String TAG = RssItemDetailPage.class.getName();
    private static final int REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 1;
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
    private transient SwipeGestureDetector mSwipeGestureDetector;

    public RssItemDetailPage() {
        mAppBarSV = new AppBarSV(R.menu.page_rss_item_detail);
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mImageLoader = mSvProvider.get(ImageLoader.class);
        mSwipeGestureDetector = new SwipeGestureDetector(provider.getContext()) {
            @Override
            public void onSwipeRight() {
                mNavigator.pop();
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
        AppSharedPreferences appSharedPreferences = mSvProvider.get(AppSharedPreferences.class);
        if (appSharedPreferences.isOneHandMode()) {
            layoutId = R.layout.one_hand_mode_page_rss_item_detail;
        }
        View view = activity.getLayoutInflater().inflate(layoutId, container, false);
        view.findViewById(R.id.container_swipe_region)
                .setOnTouchListener(mSwipeGestureDetector);
        ViewGroup containerAppBar = view.findViewById(R.id.container_app_bar);
        mAppBarSV.setMenuItemListener(this);
        mAppBarSV.setOnMenuCreated(this);
        containerAppBar.addView(mAppBarSV.buildView(activity, container));
        TextView titleText = view.findViewById(R.id.text_title);
        titleText.setText(HtmlCompat
                .fromHtml(mRssItem.title, HtmlCompat.FROM_HTML_MODE_COMPACT));
        titleText.setOnClickListener(this);
        titleText.setContentDescription(activity.getString(R.string.open_link));
        NetworkImageView networkImageView = view.findViewById(R.id.network_image);
        String imageUrl = null;
        if (mRssItem.mediaImage != null) {
            imageUrl = mRssItem.mediaImage;
        }
        boolean showImage = appSharedPreferences.isDownloadImage() && imageUrl != null;
        if (showImage) {
            Resources resources = activity.getResources();
            Drawable drawable = DrawableCompat.wrap(resources
                    .getDrawable(R.drawable.ic_image_black));
            DrawableCompat.setTint(drawable, resources.getColor(R.color.daynight_black_white));
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
        }
        Button fabOpenLink = view.findViewById(R.id.fab_open_link);
        fabOpenLink.setOnClickListener(this);
        Button fabOpenVideo = view.findViewById(R.id.fab_open_video);
        fabOpenVideo.setOnClickListener(this);
        if (mRssItem.mediaVideo != null) {
            fabOpenVideo.setVisibility(View.VISIBLE);
        } else {
            fabOpenVideo.setVisibility(View.GONE);
        }
        mAppBarSV.setTitle(mRssChannel.feedName);
        return view;
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
        mSwipeGestureDetector = null;
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
        } else if (id == R.id.menu_download_video) {
            Context context = mSvProvider.getContext().getApplicationContext();
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                downloadMediaFile();
            } else {
                ActivityCompat.requestPermissions(mNavigator.getActivity(), new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE);
            }

        }
        return false;
    }

    @Override
    public void onMenuCreated(Menu menu) {
        MenuItem downloadVideoMenu = menu.findItem(R.id.menu_download_video);
        downloadVideoMenu.setVisible(mRssItem.mediaVideo != null);
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
