package m.co.rh.id.a_news_provider.component.network.ssl;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import m.co.rh.id.a_news_provider.component.network.R;

/**
 * X509TrustManager that combines the default Android system trust store with
 * additional well-known public root CA certificates bundled in res/raw.
 * <p>
 * Old Android versions (API 21-24) ship a frozen system CA store without the
 * ISRG Root X1/X2 roots, so certificates issued by Let's Encrypt fail with
 * "Trust anchor not found". Bundling the public self-signed ISRG roots as
 * additional trust anchors completes those certificate chains.
 * <p>
 * Security note: certificate validation is never disabled or bypassed - a
 * certificate is only accepted if it is trusted by the system trust store OR
 * by the bundled well-known public roots.
 * <p>
 * Bundled certificates (public Let's Encrypt (ISRG) root CAs, used as
 * ADDITIONAL trust anchors):
 * <ul>
 * <li>res/raw/isrg_root_x1.crt -
 * SHA-256 96BCEC06264976F37460779ACF28C5A7CFE8A3C0AAE11A8FFCEE05C0BDDF08C6,
 * expires 2035-06-04</li>
 * <li>res/raw/isrg_root_x2.crt -
 * SHA-256 69729B8E15A86EFC177A57AFB7171DFC64ADD28C2FCA8C8F1507E34453CCB1470,
 * expires 2040-09-17</li>
 * </ul>
 * <p>
 * Maintenance note: the bundled roots are only a FALLBACK for devices whose
 * system CA store lacks these roots (frozen Android &lt;= 7.0 images). System
 * store validation is always attempted first. When the roots eventually
 * expire, either replace the raw .crt files with the current ones from
 * https://letsencrypt.org/certificates/ or remove the bundle entirely once old
 * devices are extinct - failing to update causes no regression for up-to-date
 * devices.
 */
public class ExtendedTrustManager implements X509TrustManager {

    private static final String TAG = ExtendedTrustManager.class.getName();

    private static final String ALIAS_ISRG_ROOT_X1 = "isrg_root_x1";
    private static final String ALIAS_ISRG_ROOT_X2 = "isrg_root_x2";

    private final X509TrustManager mDefaultTrustManager;
    private final X509TrustManager mAdditionalTrustManager;

    /**
     * Build the trust manager from the system trust store plus the bundled ISRG roots.
     *
     * @param context the application context used to read the bundled raw certificates
     * @throws GeneralSecurityException if the trust managers cannot be initialized
     * @throws IOException              if the bundled raw certificates cannot be read
     */
    public ExtendedTrustManager(Context context) throws GeneralSecurityException, IOException {
        TrustManagerFactory defaultFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        defaultFactory.init((KeyStore) null);
        mDefaultTrustManager = pickX509TrustManager(defaultFactory);
        mAdditionalTrustManager = buildAdditionalTrustManager(context);
    }

    /**
     * Create an SSLSocketFactory that trusts the system store plus the bundled ISRG roots.
     *
     * @param context the application context used to read the bundled raw certificates
     * @return the configured SSL socket factory
     */
    public static SSLSocketFactory createSslSocketFactory(Context context) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new ExtendedTrustManager(context)}, null);
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to create the extended SSL socket factory", e);
        }
    }

    private X509TrustManager buildAdditionalTrustManager(Context context)
            throws GeneralSecurityException, IOException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.isrg_root_x1)) {
            keyStore.setCertificateEntry(ALIAS_ISRG_ROOT_X1,
                    certificateFactory.generateCertificate(inputStream));
        }
        try (InputStream inputStream = context.getResources().openRawResource(R.raw.isrg_root_x2)) {
            keyStore.setCertificateEntry(ALIAS_ISRG_ROOT_X2,
                    certificateFactory.generateCertificate(inputStream));
        }
        TrustManagerFactory additionalFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        additionalFactory.init(keyStore);
        return pickX509TrustManager(additionalFactory);
    }

    private X509TrustManager pickX509TrustManager(TrustManagerFactory trustManagerFactory) {
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        for (TrustManager trustManager : trustManagers) {
            if (trustManager instanceof X509TrustManager) {
                return (X509TrustManager) trustManager;
            }
        }
        throw new IllegalStateException("No X509TrustManager found in "
                + trustManagerFactory.getClass().getName());
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        mDefaultTrustManager.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType)
            throws CertificateException {
        try {
            mDefaultTrustManager.checkServerTrusted(chain, authType);
        } catch (CertificateException e) {
            Log.w(TAG, "Server certificate not trusted by the system store, trying bundled trust anchors");
            try {
                mAdditionalTrustManager.checkServerTrusted(chain, authType);
            } catch (CertificateException e2) {
                // Neither store trusts the chain, rethrow the original system store failure
                throw e;
            }
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        X509Certificate[] defaultIssuers = mDefaultTrustManager.getAcceptedIssuers();
        X509Certificate[] additionalIssuers = mAdditionalTrustManager.getAcceptedIssuers();
        X509Certificate[] issuers =
                new X509Certificate[defaultIssuers.length + additionalIssuers.length];
        System.arraycopy(defaultIssuers, 0, issuers, 0, defaultIssuers.length);
        System.arraycopy(additionalIssuers, 0, issuers, defaultIssuers.length, additionalIssuers.length);
        return issuers;
    }
}
