package m.co.rh.id.a_news_provider.component.network.provider.cache;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import androidx.annotation.Nullable;

import com.android.volley.toolbox.ImageLoader;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;

/**
 * Two-level (memory + disk) bitmap cache for Volley's {@link ImageLoader}.
 * L1 is an in-memory {@link LruCache} sized in bytes, L2 is a size-capped
 * directory under the app cache dir so images stay available offline.
 * All disk I/O runs on a single background thread to serialize operations,
 * the memory LruCache is already thread-safe on its own.
 */
public class DiskLruBitmapCache implements ImageLoader.ImageCache {
    private static final String TAG = DiskLruBitmapCache.class.getName();

    /**
     * Maximum bytes the disk cache directory may occupy before eviction starts
     */
    public static final long DISK_CACHE_CAPACITY_BYTES = 50L * 1024L * 1024L;
    // after exceeding the capacity, oldest files are deleted until this share of the capacity remains
    private static final long DISK_CACHE_EVICTED_BYTES = DISK_CACHE_CAPACITY_BYTES * 80 / 100;
    private static final long MIN_MEMORY_CACHE_BYTES = 4L * 1024L * 1024L;
    private static final long MAX_MEMORY_CACHE_BYTES = 16L * 1024L * 1024L;
    private static final String IMAGE_DIR_NAME = "images";
    private static final String JPEG_EXTENSION = ".jpg";
    private static final String PNG_EXTENSION = ".png";
    private static final int JPEG_COMPRESSION_QUALITY = 90;
    // decoded bitmaps returned by getBitmap are downsampled so their longest side fits this bound
    private static final int MAX_DECODED_DIMENSION_PX = 2048;
    // Volley's ImageLoader prefixes the url it hands to its ImageCache with the
    // requesting view's bounds and scale type: "#W" + maxWidth + "#H" + maxHeight
    // + "#S" + scaleType.ordinal() + url (see ImageLoader#getCacheKey). The same
    // url therefore reaches putBitmap/getBitmap with different prefixes depending
    // on which view loaded it (the detail hero passes its measured pixel size, the
    // viewer passes 0/0), while isOnDisk and
    // decodeFullResolution are handed the bare url. The disk level strips that
    // prefix before hashing so every consumer maps to the same file per url; the
    // memory level keeps the full Volley key so a full-resolution viewer load is
    // not shadowed by the hero's smaller decode.
    private static final Pattern VOLLEY_CACHE_KEY_PREFIX =
            Pattern.compile("^#W-?\\d+#H-?\\d+#S\\d+");

    private final File mCacheDir;
    private final ExecutorService mDiskExecutor;
    private final Handler mMainHandler;
    private final LruCache<String, Bitmap> mMemoryCache;
    private final ILogger mLogger;

    /**
     * @param provider the provider, its application context's cache dir is used
     *                 for the disk level and the {@link ILogger} is resolved from it
     */
    public DiskLruBitmapCache(Provider provider) {
        mLogger = provider.get(ILogger.class);
        mCacheDir = new File(provider.getContext().getApplicationContext().getCacheDir(), IMAGE_DIR_NAME);
        mDiskExecutor = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        long maxMemory = Runtime.getRuntime().maxMemory();
        long memoryCacheBytes = Math.min(MAX_MEMORY_CACHE_BYTES,
                Math.max(MIN_MEMORY_CACHE_BYTES, maxMemory / 8));
        mMemoryCache = new LruCache<String, Bitmap>((int) memoryCacheBytes) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    /**
     * Returns the cached bitmap for the url: memory hit first, then the disk file
     * decoded downsampled (longest side <= 2048px) and promoted to the memory level.
     * NOTE: Volley's ImageLoader calls this ON the main thread (its get() throws
     * off-main), so a memory miss that hits disk performs the bounded decode
     * synchronously on the caller's thread - an accepted trade-off for offline
     * hero rendering, keep MAX_DECODED_DIMENSION_PX small.
     *
     * @param url the image url used as cache key
     * @return the cached bitmap, or null on miss
     */
    @Nullable
    @Override
    public Bitmap getBitmap(String url) {
        Bitmap cached = mMemoryCache.get(url);
        if (cached != null) {
            return cached;
        }
        File file = getDiskFile(url);
        if (!file.exists()) {
            return null;
        }
        Bitmap bitmap = decodeDownsampled(file);
        if (bitmap == null) {
            mLogger.d(TAG, "Failed to decode cached image file: " + file.getName());
            return null;
        }
        mMemoryCache.put(url, bitmap);
        return bitmap;
    }

    /**
     * Caches the bitmap in memory synchronously and persists it to disk
     * asynchronously (no-op when already on disk), then evicts old files
     * when the disk capacity is exceeded.
     *
     * @param url    the image url used as cache key
     * @param bitmap the bitmap to cache
     */
    @Override
    public void putBitmap(String url, Bitmap bitmap) {
        mMemoryCache.put(url, bitmap);
        mDiskExecutor.execute(() -> writeToDisk(url, bitmap));
    }

    /**
     * @param url the image url used as cache key
     * @return true if a disk file exists for the url
     */
    public boolean isOnDisk(String url) {
        return getDiskFile(url).exists();
    }

    /**
     * Decodes the full-resolution disk file for the url asynchronously
     * and delivers the result on the main thread.
     *
     * @param url      the image url used as cache key
     * @param listener receives the decoded bitmap or the error
     */
    public void decodeFullResolution(String url, FullResolutionListener listener) {
        mDiskExecutor.execute(() -> {
            try {
                File file = getDiskFile(url);
                if (!file.exists()) {
                    postError(listener, new FileNotFoundException("No cached image file for url: " + url));
                    return;
                }
                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bitmap == null) {
                    postError(listener, new IOException("Failed to decode cached image file: " + file.getName()));
                    return;
                }
                mMainHandler.post(() -> listener.onBitmapReady(bitmap));
            } catch (Exception error) {
                postError(listener, error);
            }
        });
    }

    /**
     * Strips Volley's {@code ImageLoader#getCacheKey} prefix (view bounds + scale
     * type) from a cache key, leaving the bare image url. Keys without the prefix
     * (bare urls passed to {@link #isOnDisk} / {@link #decodeFullResolution}) are
     * returned unchanged. Pure string logic.
     *
     * @param key a cache key as handed to putBitmap/getBitmap, or a bare url
     * @return the bare image url
     */
    static String stripVolleyCacheKeyPrefix(String key) {
        if (key == null) {
            return null;
        }
        return VOLLEY_CACHE_KEY_PREFIX.matcher(key).replaceFirst("");
    }

    /**
     * The single naming rule for disk cache files: the SHA-256 hex of the bare
     * image url, regardless of which Volley view (hero, viewer) wrote
     * or reads the file. Every read AND write path must go through this helper.
     *
     * @param key a cache key as handed to putBitmap/getBitmap, or a bare url
     * @return the 64-character hex file name without extension
     */
    static String hashedDiskFileName(String key) {
        return urlToHashedFileName(stripVolleyCacheKeyPrefix(key));
    }

    /**
     * Maps a url to its cache file name: lowercase-hex SHA-256 of the UTF-8 url bytes.
     * Pure string logic, throws nothing for any non-null url.
     *
     * @param url the image url
     * @return the 64-character hex file name without extension
     */
    static String urlToHashedFileName(String url) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(url.getBytes(StandardCharsets.UTF_8));
            StringBuilder stringBuilder = new StringBuilder(digest.length * 2);
            for (byte digestByte : digest) {
                stringBuilder.append(Character.forDigit((digestByte >> 4) & 0xF, 16));
                stringBuilder.append(Character.forDigit(digestByte & 0xF, 16));
            }
            return stringBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory on every Java platform, this is unreachable in practice
            throw new IllegalStateException("SHA-256 MessageDigest is not available", e);
        }
    }

    /**
     * Picks the files to delete when the cache outgrew its capacity: the oldest files
     * (by lastModified) until the projected total fits the target bytes.
     * Pure file logic, the caller performs the actual deletion.
     *
     * @param files       the cache files
     * @param capBytes    the maximum total size before eviction is needed
     * @param targetBytes the total size to shrink the cache down to
     * @return the files to delete, oldest first, empty when the cache is within the cap
     */
    static List<File> filesToEvictForCap(File[] files, long capBytes, long targetBytes) {
        List<File> filesToEvict = new ArrayList<>();
        if (files == null || files.length == 0) {
            return filesToEvict;
        }
        long totalBytes = 0;
        for (File file : files) {
            totalBytes += file.length();
        }
        if (totalBytes <= capBytes) {
            return filesToEvict;
        }
        File[] oldestFirst = files.clone();
        Arrays.sort(oldestFirst, (file, other) -> Long.compare(file.lastModified(), other.lastModified()));
        for (File file : oldestFirst) {
            if (totalBytes <= targetBytes) {
                break;
            }
            filesToEvict.add(file);
            totalBytes -= file.length();
        }
        return filesToEvict;
    }

    private void writeToDisk(String url, Bitmap bitmap) {
        try {
            if (getDiskFile(url).exists()) {
                return;
            }
            if (!mCacheDir.exists() && !mCacheDir.mkdirs()) {
                mLogger.e(TAG, "Failed to create image cache dir: " + mCacheDir);
                return;
            }
            boolean hasAlpha = bitmap.hasAlpha();
            String extension = hasAlpha ? PNG_EXTENSION : JPEG_EXTENSION;
            Bitmap.CompressFormat compressFormat = hasAlpha ?
                    Bitmap.CompressFormat.PNG : Bitmap.CompressFormat.JPEG;
            File file = new File(mCacheDir, hashedDiskFileName(url) + extension);
            try (FileOutputStream outputStream = new FileOutputStream(file)) {
                bitmap.compress(compressFormat, JPEG_COMPRESSION_QUALITY, outputStream);
            }
            evictIfNeeded();
        } catch (Exception e) {
            mLogger.e(TAG, "Failed to write bitmap to disk cache for url: " + url, e);
        }
    }

    private void evictIfNeeded() {
        File[] files = mCacheDir.listFiles();
        List<File> filesToEvict = filesToEvictForCap(files, DISK_CACHE_CAPACITY_BYTES, DISK_CACHE_EVICTED_BYTES);
        for (File file : filesToEvict) {
            if (!file.delete()) {
                mLogger.w(TAG, "Failed to delete evicted cache file: " + file.getName());
            }
        }
    }

    // the persisted extension depends on the bitmap format, so both candidates are probed;
    // keys arriving from Volley carry its "#W..#H..#S.." prefix which must not end up
    // in the file name, see hashedDiskFileName
    private File getDiskFile(String url) {
        String hashedFileName = hashedDiskFileName(url);
        File pngFile = new File(mCacheDir, hashedFileName + PNG_EXTENSION);
        if (pngFile.exists()) {
            return pngFile;
        }
        return new File(mCacheDir, hashedFileName + JPEG_EXTENSION);
    }

    private Bitmap decodeDownsampled(File file) {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), boundsOptions);
        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth, boundsOptions.outHeight, MAX_DECODED_DIMENSION_PX);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
    }

    private static int calculateInSampleSize(int width, int height, int maxDimension) {
        int longestSide = Math.max(width, height);
        int inSampleSize = 1;
        while (longestSide / inSampleSize > maxDimension) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private void postError(FullResolutionListener listener, Exception error) {
        mMainHandler.post(() -> listener.onError(error));
    }

    /**
     * Receives the result of {@link #decodeFullResolution(String, FullResolutionListener)}
     * on the main thread
     */
    public interface FullResolutionListener {
        void onBitmapReady(Bitmap bitmap);

        void onError(Exception error);
    }
}
