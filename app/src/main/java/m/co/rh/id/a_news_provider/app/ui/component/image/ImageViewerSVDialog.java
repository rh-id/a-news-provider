package m.co.rh.id.a_news_provider.app.ui.component.image;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.MimeTypeMap;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.github.chrisbanes.photoview.PhotoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.concurrent.ExecutorService;

import m.co.rh.id.a_news_provider.R;
import m.co.rh.id.a_news_provider.app.provider.StatefulViewProvider;
import m.co.rh.id.a_news_provider.app.provider.component.rx.RxDisposer;
import m.co.rh.id.a_news_provider.app.util.UiUtils;
import m.co.rh.id.a_news_provider.component.network.provider.cache.DiskLruBitmapCache;
import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.anavigator.NavRoute;
import m.co.rh.id.anavigator.StatefulViewDialog;
import m.co.rh.id.anavigator.component.INavigator;
import m.co.rh.id.anavigator.component.NavOnRequestPermissionResult;
import m.co.rh.id.anavigator.component.RequireComponent;
import m.co.rh.id.anavigator.component.RequireNavRoute;
import m.co.rh.id.aprovider.Provider;

/**
 * Full-screen pinch-zoom viewer for an article's hero image (issue #36). Pushed as
 * a dialog route from the detail page: the image loads full-resolution from the
 * offline disk cache when available (via the same {@link DiskLruBitmapCache} instance
 * the Volley ImageLoader writes to), otherwise straight over the network through the
 * Volley ImageLoader. A tap on the image pops the dialog. The bottom row offers
 * saving the image to the public Downloads directory (DownloadManager, same pattern
 * as the detail page's video download) and sharing it through a share chooser.
 */
public class ImageViewerSVDialog extends StatefulViewDialog<Activity> implements RequireNavRoute, RequireComponent<Provider>, NavOnRequestPermissionResult, View.OnClickListener {
    private static final String TAG = ImageViewerSVDialog.class.getName();
    // distinct from RssItemDetailPage's code: the navigator delivers permission
    // results to the current route only, and this dialog IS the current route
    // when its own save flow requests the permission
    private static final int REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE = 2;
    private static final String SHARE_DIR_NAME = "share";
    private static final String SHARE_IMAGE_BASE_NAME = "image";
    private static final String JPEG_EXTENSION = ".jpg";
    private static final String PNG_EXTENSION = ".png";
    private static final int SHARE_JPEG_QUALITY = 90;
    private static final String DEFAULT_IMAGE_EXTENSION = "jpg";

    private transient NavRoute mNavRoute;

    private transient Provider mSvProvider;
    private transient RxDisposer mRxDisposer;
    private transient ExecutorService mExecutorService;
    private transient ILogger mLogger;
    private transient ImageLoader mImageLoader;
    private transient DiskLruBitmapCache mDiskLruBitmapCache;
    private transient ImageLoader.ImageContainer mImageContainer;

    private transient PhotoView mPhotoView;
    private transient ProgressBar mProgressView;
    private transient AppCompatImageView mBrokenImageView;
    private transient Bitmap mCurrentBitmap;

    @Override
    public void provideNavRoute(NavRoute navRoute) {
        mNavRoute = navRoute;
    }

    @Override
    public void provideComponent(Provider provider) {
        mSvProvider = provider.get(StatefulViewProvider.class);
        mRxDisposer = mSvProvider.get(RxDisposer.class);
        mExecutorService = mSvProvider.get(ExecutorService.class);
        mLogger = mSvProvider.get(ILogger.class);
        mImageLoader = mSvProvider.get(ImageLoader.class);
        mDiskLruBitmapCache = mSvProvider.get(DiskLruBitmapCache.class);
    }

    @Override
    protected Dialog createDialog(Activity activity) {
        Dialog dialog = super.createDialog(activity);
        // the default dialog window wraps its content (a centered prompt); this
        // viewer must cover the whole screen instead, so both axes are forced to
        // match_parent and the framed dialog background is swapped for plain black
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return dialog;
    }

    @Override
    protected View createView(Activity activity, ViewGroup container) {
        View rootLayout = activity.getLayoutInflater().inflate(R.layout.dialog_image_viewer, container, false);
        mPhotoView = rootLayout.findViewById(R.id.photo_image_viewer);
        mProgressView = rootLayout.findViewById(R.id.progress_image_viewer);
        mBrokenImageView = rootLayout.findViewById(R.id.broken_image_viewer);
        mPhotoView.setOnClickListener(this);
        AppCompatImageButton saveButton = rootLayout.findViewById(R.id.button_save_image);
        saveButton.setOnClickListener(this);
        AppCompatImageButton shareButton = rootLayout.findViewById(R.id.button_share_image);
        shareButton.setOnClickListener(this);
        loadImage();
        return rootLayout;
    }

    @Override
    public void dispose(Activity activity) {
        super.dispose(activity);
        if (mSvProvider != null) {
            mSvProvider.dispose();
            mSvProvider = null;
        }
        if (mImageContainer != null) {
            mImageContainer.cancelRequest();
            mImageContainer = null;
        }
        mRxDisposer = null;
        mExecutorService = null;
        mLogger = null;
        mImageLoader = null;
        mDiskLruBitmapCache = null;
        mCurrentBitmap = null;
        mPhotoView = null;
        mProgressView = null;
        mBrokenImageView = null;
    }

    public Args getArgs() {
        return Args.of(mNavRoute);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.photo_image_viewer) {
            getNavigator().pop();
        } else if (id == R.id.button_save_image) {
            saveImage();
        } else if (id == R.id.button_share_image) {
            shareImage();
        }
    }

    @Override
    public void onRequestPermissionsResult(View currentView, Activity activity, INavigator iNavigator, int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                downloadImageFile();
            } else {
                mLogger.i(TAG, activity.getString(R.string.error_permission_denied));
            }
        }
    }

    /**
     * Loads the image: the full-resolution file from the offline disk cache when it
     * is already there, the network path otherwise (also the fall-through when the
     * disk decode fails).
     */
    private void loadImage() {
        Args args = getArgs();
        if (args == null || args.getImageUrl() == null) {
            showBrokenImage();
            return;
        }
        String url = args.getImageUrl();
        if (mDiskLruBitmapCache.isOnDisk(url)) {
            mDiskLruBitmapCache.decodeFullResolution(url, new DiskLruBitmapCache.FullResolutionListener() {
                @Override
                public void onBitmapReady(Bitmap bitmap) {
                    showBitmap(bitmap);
                }

                @Override
                public void onError(Exception error) {
                    // may fire after dispose() nulled the fields (decode is async)
                    if (mLogger != null) {
                        mLogger.e(TAG, error.getMessage(), error);
                    }
                    loadFromNetwork(url);
                }
            });
        } else {
            loadFromNetwork(url);
        }
    }

    private void loadFromNetwork(String url) {
        // the dialog may have been dismissed while the disk decode was in flight
        if (mImageLoader == null) {
            return;
        }
        if (mImageContainer != null) {
            mImageContainer.cancelRequest();
        }
        // Volley decodes the full-size bitmap when maxWidth/maxHeight are defaulted;
        // a null-bitmap onResponse is Volley's "no placeholder yet" delivery: keep waiting
        mImageContainer = mImageLoader.get(url, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                Bitmap bitmap = response.getBitmap();
                if (bitmap != null) {
                    showBitmap(bitmap);
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                if (mLogger != null) {
                    mLogger.e(TAG, error.getMessage(), error);
                }
                showBrokenImage();
            }
        });
    }

    private void showBitmap(Bitmap bitmap) {
        mCurrentBitmap = bitmap;
        if (mPhotoView != null) {
            mPhotoView.setImageBitmap(bitmap);
        }
        if (mProgressView != null) {
            mProgressView.setVisibility(View.GONE);
        }
    }

    private void showBrokenImage() {
        if (mBrokenImageView != null) {
            mBrokenImageView.setVisibility(View.VISIBLE);
        }
        if (mProgressView != null) {
            mProgressView.setVisibility(View.GONE);
        }
    }

    /**
     * Saves the image through the DownloadManager into the public Downloads
     * directory under feedName/itemTitle.ext, mirroring the detail page's video
     * download (including the API 21-28 storage permission gate). Completion is
     * announced by the DownloadManager's own system notification.
     */
    private void saveImage() {
        Args args = getArgs();
        if (args == null || args.getImageUrl() == null) {
            return;
        }
        Context context = mSvProvider.getContext().getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: scoped storage — DownloadManager needs no permission
            downloadImageFile();
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
            // API 21-28: permission already granted
            downloadImageFile();
        } else {
            // API 21-28: request permission before downloading, the result comes
            // back through onRequestPermissionsResult (this dialog is the current route)
            ActivityCompat.requestPermissions(getNavigator().getActivity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_PERMISSION_WRITE_EXTERNAL_STORAGE);
        }
    }

    private void downloadImageFile() {
        Args args = getArgs();
        if (args == null || args.getImageUrl() == null) {
            return;
        }
        Context context = mSvProvider.getContext().getApplicationContext();
        String url = args.getImageUrl();
        String title = args.getItemTitle();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        String ext = MimeTypeMap.getFileExtensionFromUrl(url);
        String mimeType = mimeTypeMap.getMimeTypeFromExtension(ext);
        if (ext == null || ext.isEmpty()) {
            ext = DEFAULT_IMAGE_EXTENSION;
        }
        String subDir = args.getFeedName() + "/" + title + "." + ext;
        // captured on the main thread: the dialog may be dismissed (dispose() nulls
        // the fields) while the download enqueue runs on the executor
        final ILogger logger = mLogger;
        final Handler mainHandler = mSvProvider.get(Handler.class);
        mExecutorService.execute(() -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                        .setTitle(title)
                        .setMimeType(mimeType)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, subDir);
                DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                downloadManager.enqueue(request);
                if (logger != null) {
                    logger.i(TAG, context.getString(R.string.begin_downloading));
                }
                // toast must run on a looper thread: post it onto the scoped main handler
                mainHandler.post(() ->
                        Toast.makeText(context, R.string.image_saved, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                if (logger != null) {
                    logger.e(TAG, e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Shares the currently displayed bitmap: compressed to a temp file under the
     * cache dir (JPEG, or PNG when the bitmap has alpha) and handed to the system
     * share chooser via {@link UiUtils#shareFile}. Ignored (logged only) when no
     * bitmap has loaded yet.
     */
    private void shareImage() {
        // local capture: dispose() may null the field on the main thread while this runs
        Bitmap bitmap = mCurrentBitmap;
        if (bitmap == null) {
            mLogger.i(TAG, "Share ignored, no image loaded yet");
            return;
        }
        Context context = mSvProvider.getContext().getApplicationContext();
        String chooserMessage = context.getString(R.string.title_share_image);
        // captured on the main thread: dispose() may null mLogger while this runs
        final ILogger logger = mLogger;
        mExecutorService.execute(() -> {
            try {
                File shareDir = new File(context.getCacheDir(), SHARE_DIR_NAME);
                if (!shareDir.exists() && !shareDir.mkdirs()) {
                    throw new IOException("Failed to create share dir: " + shareDir);
                }
                boolean hasAlpha = bitmap.hasAlpha();
                File shareFile = new File(shareDir, SHARE_IMAGE_BASE_NAME + (hasAlpha ? PNG_EXTENSION : JPEG_EXTENSION));
                try (FileOutputStream outputStream = new FileOutputStream(shareFile)) {
                    bitmap.compress(hasAlpha ? Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG,
                            SHARE_JPEG_QUALITY, outputStream);
                }
                UiUtils.shareFile(context, shareFile, chooserMessage);
            } catch (Exception e) {
                if (logger != null) {
                    logger.e(TAG, e.getMessage(), e);
                }
            }
        });
    }

    public static class Args implements Serializable {
        public static Args with(String imageUrl, String feedName, String itemTitle) {
            Args args = new Args();
            args.mImageUrl = imageUrl;
            args.mFeedName = feedName;
            args.mItemTitle = itemTitle;
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

        private String mImageUrl;
        private String mFeedName;
        private String mItemTitle;

        public String getImageUrl() {
            return mImageUrl;
        }

        public String getFeedName() {
            return mFeedName;
        }

        public String getItemTitle() {
            return mItemTitle;
        }
    }
}
