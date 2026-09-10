package m.co.rh.id.a_news_provider.component.network.ssl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLSocketFactory;

import m.co.rh.id.a_news_provider.component.network.R;

/**
 * Instrumented test for ExtendedTrustManager, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExtendedTrustManagerTest {

    /**
     * Official published SHA-256 fingerprint of the ISRG Root X1 certificate,
     * guards against corruption of the bundled raw resource.
     */
    private static final String ISRG_ROOT_X1_SHA256 =
            "96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6";

    @Test
    public void trustManagerInitializesWithBundledIssuers() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        ExtendedTrustManager trustManager = new ExtendedTrustManager(appContext);
        assertTrue(trustManager.getAcceptedIssuers().length >= 2);
    }

    @Test
    public void bundledRootX1MatchesOfficialFingerprint() throws Exception {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] bytes = readRawResource(appContext.getResources(), R.raw.isrg_root_x1);
        byte[] digest = sha256(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        assertEquals(ISRG_ROOT_X1_SHA256, hex.toString());
    }

    @Test
    public void createSslSocketFactoryReturnsFactory() {
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SSLSocketFactory socketFactory = ExtendedTrustManager.createSslSocketFactory(appContext);
        assertNotNull(socketFactory);
    }

    private static byte[] sha256(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes);
        return messageDigest.digest();
    }

    private static byte[] readRawResource(Resources resources, int resourceId) throws IOException {
        try (InputStream inputStream = resources.openRawResource(resourceId);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toByteArray();
        }
    }
}
