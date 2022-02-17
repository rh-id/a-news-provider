package m.co.rh.id.a_news_provider.component.network.volley;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * {@link SSLSocketFactory} which ensures TLSv1.1 and 1.2 are enabled where supported.
 *
 * <p>These protocols are supported on API 16+ devices but only enabled by default on API 20+
 * devices.
 */
public class TlsEnabledSSLSocketFactory extends InterceptingSSLSocketFactory {

    static final String TLS_1_1 = "TLSv1.1";
    static final String TLS_1_2 = "TLSv1.2";

    public TlsEnabledSSLSocketFactory(SSLSocketFactory delegate) {
        super(delegate);
    }

    @Override
    protected void onSocketCreated(Socket socket) {
        if (!(socket instanceof SSLSocket)) {
            return;
        }
        SSLSocket sslSocket = (SSLSocket) socket;
        boolean shouldEnableTls11 = shouldEnableProtocol(sslSocket, TLS_1_1);
        boolean shouldEnableTls12 = shouldEnableProtocol(sslSocket, TLS_1_2);
        if (!shouldEnableTls11 && !shouldEnableTls12) {
            // Already enabled - unexpected, but no action is needed.
            return;
        }
        // Add missing protocols to the list of enabled protocols.
        List<String> augmentedProtocols =
                new ArrayList<>(Arrays.asList(sslSocket.getEnabledProtocols()));
        if (shouldEnableTls11) {
            augmentedProtocols.add(TLS_1_1);
        }
        if (shouldEnableTls12) {
            augmentedProtocols.add(TLS_1_2);
        }
        sslSocket.setEnabledProtocols(
                augmentedProtocols.toArray(new String[0]));
    }

    private static boolean shouldEnableProtocol(SSLSocket sslSocket, String protocol) {
        return !linearSearch(sslSocket.getEnabledProtocols(), protocol)
                && linearSearch(sslSocket.getSupportedProtocols(), protocol);
    }

    private static <T> boolean linearSearch(T[] array, T item) {
        for (T arrayItem : array) {
            if (item.equals(arrayItem)) {
                return true;
            }
        }
        return false;
    }
}
