package com.zuoqirun.lyricscompanion;

import android.os.Build;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/** Enables TLS 1.2, which Android 4.4 implements but does not enable by default. */
final class HttpCompat {
    private HttpCompat() { }

    /**
     * 打开一条连接。默认路径完全不变；只有用户打开「网络直连」时（issue #153）才显式传入
     * {@link Proxy#NO_PROXY}，绕开会吞掉词库请求的系统代理。逐条连接指定代理从 API 1 起
     * 就可用，Android 4.4 同样适用。
     */
    static HttpURLConnection open(String address) throws IOException {
        Proxy proxy = NetworkDirectPolicy.proxy();
        URL url = new URL(address);
        HttpURLConnection connection = (HttpURLConnection) (proxy == null
                ? url.openConnection()
                : url.openConnection(proxy));
        if (Build.VERSION.SDK_INT == 19 && connection instanceof HttpsURLConnection) {
            HttpsURLConnection https = (HttpsURLConnection) connection;
            https.setSSLSocketFactory(new Tls12SocketFactory(https.getSSLSocketFactory()));
        }
        return connection;
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;

        Tls12SocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
        }

        @Override public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override public Socket createSocket(Socket socket, String host, int port,
                                             boolean autoClose) throws IOException {
            return enable(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override public Socket createSocket(String host, int port,
                                             InetAddress localHost, int localPort)
                throws IOException {
            return enable(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }

        @Override public Socket createSocket(InetAddress address, int port,
                                             InetAddress localAddress, int localPort)
                throws IOException {
            return enable(delegate.createSocket(address, port, localAddress, localPort));
        }

        private static Socket enable(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[]{"TLSv1.2"});
            }
            return socket;
        }
    }
}
