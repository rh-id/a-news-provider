package m.co.rh.id.a_news_provider.component.network.volley;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

/**
 * {@link SSLSocketFactory} which defers to the given delegate and provides an intercept to inspect
 * and alter created sockets.
 */
abstract class InterceptingSSLSocketFactory extends SSLSocketFactory {

    private final SSLSocketFactory mDelegate;

    InterceptingSSLSocketFactory(SSLSocketFactory delegate) {
        mDelegate = delegate;
    }

    @Override
    public final String[] getDefaultCipherSuites() {
        return mDelegate.getDefaultCipherSuites();
    }

    @Override
    public final String[] getSupportedCipherSuites() {
        return mDelegate.getSupportedCipherSuites();
    }

    @Override
    public final Socket createSocket(Socket s, String host, int port, boolean autoClose)
            throws IOException {
        Socket socket = mDelegate.createSocket(s, host, port, autoClose);
        onSocketCreated(socket);
        return socket;
    }

    @Override
    public final Socket createSocket(String host, int port) throws IOException {
        Socket socket = mDelegate.createSocket(host, port);
        onSocketCreated(socket);
        return socket;
    }

    @Override
    public final Socket createSocket(InetAddress host, int port) throws IOException {
        Socket socket = mDelegate.createSocket(host, port);
        onSocketCreated(socket);
        return socket;
    }

    @Override
    public final Socket createSocket(
            InetAddress address, int port, InetAddress localAddress, int localPort)
            throws IOException {
        Socket socket = mDelegate.createSocket(address, port, localAddress, localPort);
        onSocketCreated(socket);
        return socket;
    }

    @Override
    public final Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        Socket socket = mDelegate.createSocket(host, port, localHost, localPort);
        onSocketCreated(socket);
        return socket;
    }

    /**
     * Called whenever a new socket is created.
     */
    protected abstract void onSocketCreated(Socket socket);
}
