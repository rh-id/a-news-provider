package m.co.rh.id.a_news_provider.component.network.provider.cache;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import m.co.rh.id.alogger.ILogger;
import m.co.rh.id.aprovider.Provider;
import m.co.rh.id.aprovider.ProviderModule;
import m.co.rh.id.aprovider.ProviderRegistry;

/**
 * Instrumented tests for {@link DiskLruBitmapCache}, moved from the unit-test
 * source set (DiskLruBitmapCacheFileLogicTest) per review follow-up so the suite
 * runs on a real device and exercises the actual two-level (memory + disk)
 * cache, not only the pure statics. Group A ports the pure file/string logic
 * coverage, group B covers real put/get round-trips, cross-instance disk
 * persistence, format-dependent file naming and full-resolution vs downsampled
 * decoding, plus live-path regression pins for the Volley-key/bare-url disk file
 * convergence, the corrupt-file decode failure handling and the write-once disk
 * contract. The logger is a hand-written no-op stub registered on the tiny
 * test-only Provider each test builds in setUp.
 */
@RunWith(AndroidJUnit4.class)
public class DiskLruBitmapCacheTest {

    private static final int TEST_FILE_SIZE_BYTES = 100;
    private static final long DISK_WRITE_TIMEOUT_MS = 10_000L;
    private static final long DISK_WRITE_POLL_MS = 50L;
    private static final long CALLBACK_TIMEOUT_SECONDS = 10L;
    // mirrors DiskLruBitmapCache.IMAGE_DIR_NAME, which is private
    private static final String IMAGE_DIR_NAME = "images";

    // junit's default temp location does not exist on-device, so the rule is
    // rooted at the app cache dir, which is always writable under test
    @Rule
    public TemporaryFolder temporaryFolder =
            new TemporaryFolder(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());

    private Provider mProvider;

    @Before
    public void setUp() {
        mProvider = Provider.createProvider(targetContext(), new ImageCacheTestProviderModule());
    }

    @After
    public void disposeProvider() {
        mProvider.dispose();
        mProvider = null;
    }

    @After
    public void deleteImageCacheDir() {
        deleteRecursively(imagesDir(targetContext()));
    }

    // ------------------------------------------------------------------
    // Group A: pure statics (ported from DiskLruBitmapCacheFileLogicTest)
    // ------------------------------------------------------------------

    @Test
    public void testUrlToHashedFileNameMatchesKnownSha256() {
        // pinned digest of the UTF-8 url bytes, guards against hash/encoding regressions
        assertEquals("5cd76d96bc2f2aecf99356e54cb349c5efb270c1cd0d030b78511575174af695",
                DiskLruBitmapCache.urlToHashedFileName("https://example.com/photo.jpg"));
    }

    @Test
    public void testUrlToHashedFileNameIsStableForSameUrl() {
        String url = "https://example.com/images/news/2026/headline.jpg";
        String first = DiskLruBitmapCache.urlToHashedFileName(url);
        String second = DiskLruBitmapCache.urlToHashedFileName(url);
        assertEquals(first, second);
    }

    @Test
    public void testUrlToHashedFileNameIs64CharLowercaseHex() {
        String hashed = DiskLruBitmapCache.urlToHashedFileName("https://example.com/photo.jpg?w=640");
        assertEquals(64, hashed.length());
        assertTrue(hashed.matches("[0-9a-f]{64}"));
    }

    @Test
    public void testUrlToHashedFileNameIsDistinctForDistinctUrls() {
        String base = DiskLruBitmapCache.urlToHashedFileName("https://example.com/photo.jpg");
        String differentPath = DiskLruBitmapCache.urlToHashedFileName("https://example.com/photo2.jpg");
        String differentQuery = DiskLruBitmapCache.urlToHashedFileName("https://example.com/photo.jpg?size=large");
        String differentScheme = DiskLruBitmapCache.urlToHashedFileName("http://example.com/photo.jpg");
        assertNotEquals(base, differentPath);
        assertNotEquals(base, differentQuery);
        assertNotEquals(base, differentScheme);
    }

    @Test
    public void testUrlToHashedFileNameHandlesUnicodeAndLongUrls() {
        String unicode = DiskLruBitmapCache.urlToHashedFileName("https://example.com/图片/新闻.jpg");
        String longUrl = DiskLruBitmapCache.urlToHashedFileName("https://example.com/" + "a".repeat(500));
        assertEquals(64, unicode.length());
        assertTrue(unicode.matches("[0-9a-f]{64}"));
        assertEquals(64, longUrl.length());
        assertTrue(longUrl.matches("[0-9a-f]{64}"));
        // unicode url is stable too
        assertEquals(unicode, DiskLruBitmapCache.urlToHashedFileName("https://example.com/图片/新闻.jpg"));
    }

    @Test
    public void testStripVolleyCacheKeyPrefixRemovesHeroViewPrefix() {
        // what a NetworkImageView hands over: its measured pixel bounds + FIT_CENTER
        assertEquals("https://example.com/photo.jpg",
                DiskLruBitmapCache.stripVolleyCacheKeyPrefix("#W1080#H400#S3https://example.com/photo.jpg"));
    }

    @Test
    public void testStripVolleyCacheKeyPrefixRemovesViewerPrefix() {
        // what the 2-arg ImageLoader.get hands over: 0/0 bounds + CENTER_INSIDE
        assertEquals("https://example.com/photo.jpg",
                DiskLruBitmapCache.stripVolleyCacheKeyPrefix("#W0#H0#S2https://example.com/photo.jpg"));
    }

    @Test
    public void testStripVolleyCacheKeyPrefixKeepsBareUrlUntouched() {
        assertEquals("https://example.com/photo.jpg",
                DiskLruBitmapCache.stripVolleyCacheKeyPrefix("https://example.com/photo.jpg"));
        assertNull(DiskLruBitmapCache.stripVolleyCacheKeyPrefix(null));
    }

    @Test
    public void testStripVolleyCacheKeyPrefixKeepsUrlFragmentsAfterPrefix() {
        // a "#W..." fragment INSIDE the url must survive, only the leading prefix goes
        assertEquals("https://example.com/?next=#W9#H9#S9x",
                DiskLruBitmapCache.stripVolleyCacheKeyPrefix("#W0#H0#S2https://example.com/?next=#W9#H9#S9x"));
    }

    @Test
    public void testHashedDiskFileNameConvergesForEveryCacheKeyVariant() {
        // regression: the file WRITTEN for a Volley key (hero: measured px bounds,
        // viewer: 0/0) must be the file READ for the bare url - the write
        // path once hashed the raw prefixed key while reads stripped it
        String url = "https://example.com/photo.jpg";
        String bare = DiskLruBitmapCache.hashedDiskFileName(url);
        assertEquals(bare, DiskLruBitmapCache.hashedDiskFileName("#W592#H400#S3" + url));
        assertEquals(bare, DiskLruBitmapCache.hashedDiskFileName("#W0#H0#S2" + url));
        assertEquals(bare, DiskLruBitmapCache.hashedDiskFileName("#W2147483647#H2147483647#S3" + url));
        assertEquals(64, bare.length());
        // the exact hero-style key/url pair observed in the bug report converges too
        String bareImg = DiskLruBitmapCache.hashedDiskFileName("https://x/img.jpg");
        assertEquals(bareImg, DiskLruBitmapCache.hashedDiskFileName("#W592#H400#S3https://x/img.jpg"));
    }

    @Test
    public void testFilesToEvictForCapReturnsEmptyWhenUnderCap() throws IOException {
        File dir = temporaryFolder.newFolder();
        File oldest = newFileWithTimestamp(dir, "oldest.jpg", 1000L);
        File newest = newFileWithTimestamp(dir, "newest.jpg", 2000L);
        long totalBytes = oldest.length() + newest.length();

        assertTrue(DiskLruBitmapCache.filesToEvictForCap(new File[]{oldest, newest},
                totalBytes + 1, totalBytes).isEmpty());
        // boundary: exactly at the cap is still within it
        assertTrue(DiskLruBitmapCache.filesToEvictForCap(new File[]{oldest, newest},
                totalBytes, totalBytes * 80 / 100).isEmpty());
    }

    @Test
    public void testFilesToEvictForCapReturnsEmptyForNoFiles() {
        assertTrue(DiskLruBitmapCache.filesToEvictForCap(null, 1000, 800).isEmpty());
        assertTrue(DiskLruBitmapCache.filesToEvictForCap(new File[0], 1000, 800).isEmpty());
    }

    @Test
    public void testFilesToEvictForCapEvictsOldestFirst() throws IOException {
        File dir = temporaryFolder.newFolder();
        File first = newFileWithTimestamp(dir, "first.jpg", 1000L);
        File second = newFileWithTimestamp(dir, "second.jpg", 2000L);
        File third = newFileWithTimestamp(dir, "third.jpg", 3000L);
        long totalBytes = first.length() + second.length() + third.length();
        // cap below the total forces eviction, target low enough that two files must go
        long cap = totalBytes - TEST_FILE_SIZE_BYTES / 2;
        long target = totalBytes - TEST_FILE_SIZE_BYTES * 2;

        List<File> filesToEvict = DiskLruBitmapCache.filesToEvictForCap(
                new File[]{third, first, second}, cap, target);

        assertEquals(2, filesToEvict.size());
        assertEquals(first, filesToEvict.get(0));
        assertEquals(second, filesToEvict.get(1));
    }

    @Test
    public void testFilesToEvictForCapStopsAtTargetNotCap() throws IOException {
        File dir = temporaryFolder.newFolder();
        File first = newFileWithTimestamp(dir, "first.jpg", 1000L);
        File second = newFileWithTimestamp(dir, "second.jpg", 2000L);
        File third = newFileWithTimestamp(dir, "third.jpg", 3000L);
        File fourth = newFileWithTimestamp(dir, "fourth.jpg", 4000L);
        File fifth = newFileWithTimestamp(dir, "fifth.jpg", 5000L);
        long totalBytes = first.length() + second.length() + third.length()
                + fourth.length() + fifth.length();
        // total 500: cap 400, target 300 - deleting the two oldest lands exactly on the
        // target, a third deletion would overshoot towards the cap instead of the target
        long cap = totalBytes - TEST_FILE_SIZE_BYTES;
        long target = totalBytes - TEST_FILE_SIZE_BYTES * 2;

        List<File> filesToEvict = DiskLruBitmapCache.filesToEvictForCap(
                new File[]{first, second, third, fourth, fifth}, cap, target);

        assertEquals(2, filesToEvict.size());
        assertEquals(first, filesToEvict.get(0));
        assertEquals(second, filesToEvict.get(1));
    }

    // ------------------------------------------------------------------
    // Group B: real two-level cache integration on-device
    // ------------------------------------------------------------------

    @Test
    public void testGetBitmapReturnsSameInstanceAfterPut() {
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String url = "https://example.com/l1-hit.png";
        Bitmap bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);

        cache.putBitmap(url, bitmap);

        // the put is synchronous on the memory level, so this is an L1 hit
        assertSame("memory-level hit must return the very instance that was put",
                bitmap, cache.getBitmap(url));
    }

    @Test
    public void testGetBitmapPersistsAcrossInstancesOnSameContext() throws Exception {
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String url = "https://example.com/l2-persist.png";
        Bitmap original = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
        original.eraseColor(Color.RED);

        cache.putBitmap(url, original);
        awaitOnDisk(cache, url);

        // a fresh instance has an empty memory cache, forcing the L2 disk path
        DiskLruBitmapCache freshCache = new DiskLruBitmapCache(mProvider);
        Bitmap loaded = freshCache.getBitmap(url);
        assertNotNull("disk path must return the persisted bitmap", loaded);
        assertEquals(original.getWidth(), loaded.getWidth());
        assertEquals(original.getHeight(), loaded.getHeight());
    }

    @Test
    public void testIsOnDiskTracksPersistenceWithFormatDependentExtension() throws Exception {
        Context context = targetContext();
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String alphaUrl = "https://example.com/alpha.png";
        String opaqueUrl = "https://example.com/opaque.jpg";
        Bitmap alphaBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        // a fully transparent pixel guarantees hasAlpha reports true
        alphaBitmap.setPixel(0, 0, Color.TRANSPARENT);
        assertTrue(alphaBitmap.hasAlpha());
        Bitmap opaqueBitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.RGB_565);
        assertFalse(opaqueBitmap.hasAlpha());

        assertFalse("nothing put yet, so nothing on disk", cache.isOnDisk(alphaUrl));
        assertFalse(cache.isOnDisk(opaqueUrl));

        cache.putBitmap(alphaUrl, alphaBitmap);
        cache.putBitmap(opaqueUrl, opaqueBitmap);
        awaitOnDisk(cache, alphaUrl);
        awaitOnDisk(cache, opaqueUrl);

        assertTrue(cache.isOnDisk(alphaUrl));
        assertTrue(cache.isOnDisk(opaqueUrl));
        // alpha bitmaps persist as png, opaque ones as jpg
        assertTrue(new File(imagesDir(context),
                DiskLruBitmapCache.hashedDiskFileName(alphaUrl) + ".png").exists());
        assertTrue(new File(imagesDir(context),
                DiskLruBitmapCache.hashedDiskFileName(opaqueUrl) + ".jpg").exists());
    }

    @Test
    public void testDecodeFullResolutionReturnsFullWidthWhileGetBitmapDownsamples() throws Exception {
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String url = "https://example.com/very-wide.png";
        Bitmap original = Bitmap.createBitmap(2100, 100, Bitmap.Config.ARGB_8888);
        original.eraseColor(Color.BLUE);

        cache.putBitmap(url, original);
        awaitOnDisk(cache, url);

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Bitmap> fullBitmapRef = new AtomicReference<>();
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        AtomicReference<Boolean> callbackOnMainThreadRef = new AtomicReference<>();
        cache.decodeFullResolution(url, new DiskLruBitmapCache.FullResolutionListener() {
            @Override
            public void onBitmapReady(Bitmap bitmap) {
                callbackOnMainThreadRef.set(Looper.myLooper() == Looper.getMainLooper());
                fullBitmapRef.set(bitmap);
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                callbackOnMainThreadRef.set(Looper.myLooper() == Looper.getMainLooper());
                errorRef.set(error);
                latch.countDown();
            }
        });
        assertTrue("decodeFullResolution never called back within "
                        + CALLBACK_TIMEOUT_SECONDS + "s",
                latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("decodeFullResolution failed", errorRef.get());
        }
        assertEquals("callback must fire on the main looper", Boolean.TRUE, callbackOnMainThreadRef.get());
        Bitmap fullBitmap = fullBitmapRef.get();
        assertNotNull(fullBitmap);
        assertEquals("full resolution must keep the original width", 2100, fullBitmap.getWidth());

        // the decoded disk file must be downsampled for the regular get path
        DiskLruBitmapCache freshCache = new DiskLruBitmapCache(mProvider);
        Bitmap downsampled = freshCache.getBitmap(url);
        assertNotNull(downsampled);
        int longestSide = Math.max(downsampled.getWidth(), downsampled.getHeight());
        assertTrue("longest side must be capped at 2048px, was " + longestSide,
                longestSide <= 2048);
        assertEquals("inSampleSize 2 must halve the 2100px width", 1050, downsampled.getWidth());
    }

    @Test
    public void testDecodeFullResolutionForNeverPutUrlSignalsError() throws Exception {
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String url = "https://example.com/never-put.png";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        cache.decodeFullResolution(url, new DiskLruBitmapCache.FullResolutionListener() {
            @Override
            public void onBitmapReady(Bitmap bitmap) {
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                errorRef.set(error);
                latch.countDown();
            }
        });
        assertTrue("decodeFullResolution never called back within "
                        + CALLBACK_TIMEOUT_SECONDS + "s",
                latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Exception error = errorRef.get();
        assertNotNull("onError must fire for a url that was never put", error);
        assertTrue("expected FileNotFoundException, got: " + error,
                error instanceof FileNotFoundException);
    }

    @Test
    public void testVolleyKeyedPutAndBareUrlReadConvergeOnSameDiskFile() throws Exception {
        // live-path regression pin for the review bug: writeToDisk once hashed the
        // raw Volley-prefixed key while the read paths stripped it, so a file written
        // by the hero was invisible to isOnDisk/decodeFullResolution (which get the
        // bare url) and to other Volley key variants. Every key form must land on
        // and read from ONE file per url.
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String bareUrl = "https://example.com/cat.jpg";
        Bitmap putBitmap = solidBitmap(8, Color.RED);

        // what the detail hero hands over: measured pixel bounds + FIT_CENTER
        cache.putBitmap("#W592#H400#S3" + bareUrl, putBitmap);
        // production calls isOnDisk with the bare url (the image viewer)
        awaitOnDisk(cache, bareUrl);
        assertTrue("isOnDisk(bare url) must see the file written under the hero key",
                cache.isOnDisk(bareUrl));

        // a fresh instance has an empty memory cache, forcing the L2 disk path via
        // the viewer-style key (0/0 bounds + CENTER_INSIDE)
        DiskLruBitmapCache freshCache = new DiskLruBitmapCache(mProvider);
        Bitmap loaded = freshCache.getBitmap("#W0#H0#S2" + bareUrl);
        assertNotNull("viewer-style key must hit the hero-written disk file", loaded);
        assertEquals(putBitmap.getWidth(), loaded.getWidth());
        assertEquals(putBitmap.getHeight(), loaded.getHeight());
    }

    @Test
    public void testGetBitmapReturnsNullForCorruptDiskFile() throws Exception {
        // pins the decode-failure path for a cache file that exists but is not
        // decodable (e.g. truncated/overwritten bytes): getBitmap must return null
        // (logging via the provider ILogger, stubbed here) and decodeFullResolution
        // must signal onError instead of throwing or delivering a bitmap
        Context context = targetContext();
        String bareUrl = "https://example.com/corrupt.jpg";
        File corruptFile = new File(imagesDir(context),
                DiskLruBitmapCache.hashedDiskFileName(bareUrl) + ".jpg");
        writeBytes(corruptFile, "definitely not a bitmap".getBytes());

        DiskLruBitmapCache freshCache = new DiskLruBitmapCache(mProvider);
        assertNull("undecodable disk file must yield a null getBitmap",
                freshCache.getBitmap(bareUrl));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        freshCache.decodeFullResolution(bareUrl, new DiskLruBitmapCache.FullResolutionListener() {
            @Override
            public void onBitmapReady(Bitmap bitmap) {
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                errorRef.set(error);
                latch.countDown();
            }
        });
        assertTrue("decodeFullResolution never called back within "
                        + CALLBACK_TIMEOUT_SECONDS + "s",
                latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotNull("onError must fire when the disk file cannot be decoded",
                errorRef.get());
    }

    @Test
    public void testPutBitmapDoesNotRewriteExistingDiskFile() throws Exception {
        // pins the write-once disk contract: writeToDisk is a no-op when the file
        // for the url already exists, so re-putting a different bitmap under the
        // same url must not rewrite (re-compress/touch) the persisted bytes and a
        // fresh instance must keep decoding the original content
        Context context = targetContext();
        DiskLruBitmapCache cache = new DiskLruBitmapCache(mProvider);
        String url = "https://example.com/write-once.jpg";
        Bitmap bitmapA = solidBitmap(8, Color.RED);

        cache.putBitmap(url, bitmapA);
        awaitOnDisk(cache, url);
        File diskFile = existingDiskFile(context, url);
        byte[] bytesAfterFirstPut = readAllBytes(diskFile);

        // same url, different content: the disk level must keep A's bytes
        cache.putBitmap(url, solidBitmap(8, Color.BLUE));

        // the disk executor is single-threaded FIFO, so once this decode task has
        // run, the re-put's writeToDisk task completed too (and was skipped)
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> errorRef = new AtomicReference<>();
        cache.decodeFullResolution(url, new DiskLruBitmapCache.FullResolutionListener() {
            @Override
            public void onBitmapReady(Bitmap bitmap) {
                latch.countDown();
            }

            @Override
            public void onError(Exception error) {
                errorRef.set(error);
                latch.countDown();
            }
        });
        assertTrue("decodeFullResolution never called back within "
                        + CALLBACK_TIMEOUT_SECONDS + "s",
                latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("decodeFullResolution failed", errorRef.get());
        }

        assertArrayEquals("re-put under the same url must not rewrite the disk file",
                bytesAfterFirstPut, readAllBytes(diskFile));

        // a fresh instance decodes whatever is really on disk: still A's content
        DiskLruBitmapCache freshCache = new DiskLruBitmapCache(mProvider);
        Bitmap loaded = freshCache.getBitmap(url);
        assertNotNull("disk content must survive the re-put", loaded);
        assertEquals(bitmapA.getWidth(), loaded.getWidth());
        assertEquals(bitmapA.getHeight(), loaded.getHeight());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Context targetContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    // the disk level persists under <cacheDir>/images; the async write must be
    // awaited before any on-disk assertion can run deterministically
    private static File imagesDir(Context context) {
        return new File(context.getCacheDir(), IMAGE_DIR_NAME);
    }

    private static void awaitOnDisk(DiskLruBitmapCache cache, String url) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DISK_WRITE_TIMEOUT_MS;
        while (!cache.isOnDisk(url) && System.currentTimeMillis() < deadline) {
            Thread.sleep(DISK_WRITE_POLL_MS);
        }
        assertTrue("disk write did not complete within " + DISK_WRITE_TIMEOUT_MS + " ms",
                cache.isOnDisk(url));
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

    private File newFileWithTimestamp(File dir, String name, long lastModifiedMillis) throws IOException {
        File file = new File(dir, name);
        // plain java.io on purpose: File.toPath() is an instance method added in API 26
        // and core-library desugaring cannot inject it into the platform File class,
        // so the NIO variant crashes with NoSuchMethodError on API 24 devices
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(new byte[TEST_FILE_SIZE_BYTES]);
        }
        if (!file.setLastModified(lastModifiedMillis)) {
            throw new IOException("Failed to set lastModified for " + file);
        }
        return file;
    }

    private static Bitmap solidBitmap(int size, int color) {
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                bitmap.setPixel(x, y, color);
            }
        }
        return bitmap;
    }

    // mirrors DiskLruBitmapCache#getDiskFile's probe order (alpha bitmaps persist
    // as png, opaque ones as jpg) so tests locate the persisted file without
    // assuming the format
    private static File existingDiskFile(Context context, String url) {
        String hashedName = DiskLruBitmapCache.hashedDiskFileName(url);
        File pngFile = new File(imagesDir(context), hashedName + ".png");
        if (pngFile.exists()) {
            return pngFile;
        }
        return new File(imagesDir(context), hashedName + ".jpg");
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        File parentDir = file.getParentFile();
        // plain java.io on purpose: File.toPath() crashes with NoSuchMethodError on
        // API 24 devices, see newFileWithTimestamp
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create dir: " + parentDir);
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        // plain java.io on purpose: java.nio.file is unavailable on API 24 devices,
        // see newFileWithTimestamp
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream inputStream = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = inputStream.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new IOException("Unexpected end of stream: " + file);
                }
                offset += read;
            }
        }
        return bytes;
    }

    /**
     * Hand-rolled no-op {@link ILogger} double for this suite: Mockito is
     * unreliable on ART, so fakes are implemented directly (project convention).
     */
    private static class StubLogger implements ILogger {

        @Override
        public void setLogLevel(int level) {
            // no-op
        }

        @Override
        public void v(String tag, String message) {
            // no-op
        }

        @Override
        public void v(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void d(String tag, String message) {
            // no-op
        }

        @Override
        public void d(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void i(String tag, String message) {
            // no-op
        }

        @Override
        public void i(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void w(String tag, String message) {
            // no-op
        }

        @Override
        public void w(String tag, String message, Throwable throwable) {
            // no-op
        }

        @Override
        public void e(String tag, String message) {
            // no-op
        }

        @Override
        public void e(String tag, String message, Throwable throwable) {
            // no-op
        }
    }

    /**
     * Tiny provider module for this suite: registers exactly the {@link ILogger}
     * the {@link DiskLruBitmapCache} constructor resolves from the Provider.
     */
    private static class ImageCacheTestProviderModule implements ProviderModule {

        @Override
        public void provides(ProviderRegistry providerRegistry, Provider provider) {
            providerRegistry.registerLazy(ILogger.class, StubLogger::new);
        }

        @Override
        public void dispose(Provider provider) {
            // nothing to dispose
        }
    }
}
