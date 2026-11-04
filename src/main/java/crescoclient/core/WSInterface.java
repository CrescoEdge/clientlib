package crescoclient.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolConfig;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket transport for the Cresco client on Netty (matches the wsapi Netty server). The server certificate is
 * verified per the client's {@link TlsTrust} (pinned fingerprint, CA file or the JDK store; {@link TlsTrust#insecure()}
 * only on request, with a WARNING per connection), and nothing is sent to a server that fails it. Identity
 * (region/agent/plugin) is read from the verified TLS peer certificate. Per-thread {@link WsConn} pooling for the
 * stateless apisocket ({@link #getSession()}); stream sockets (dataplane, logstreamer) use the one connection that
 * {@link #connect()} opened ({@link #getStreamSession()}). The upgrade carries the client's {@link Credentials}
 * (per-user Basic, or the service key). All resources released in {@link #close()} so the JVM can exit.
 */
public class WSInterface {

    private static final int MAX_MSG_BYTES = 32 * 1024 * 1024;

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicBoolean sessionLock = new AtomicBoolean();
    private String regionName, agentName, pluginName;
    private final AtomicBoolean isReconnect = new AtomicBoolean(true);
    private final AtomicBoolean inConnect = new AtomicBoolean(false);
    // the connection connect() opened: the one a stream's handshake was sent on
    private volatile WsConn streamSession;
    private static final Logger LOG = LoggerFactory.getLogger(WSInterface.class);

    private EventLoopGroup group;
    private SslContext sslContext;

    private final Map<Long, WsConn> sessionMap;
    private final Map<String, String> wsConfig;
    private final WSCallback wsCallback;

    private int connectionTimeout = 30;
    private final String url, host, apiPath;
    private final int port;
    private final ClientSecurity security;
    // why the last connection attempt failed (an untrusted certificate, refused credentials, the network), or null
    private volatile String lastError;

    /** A transport that authenticates with {@code wsConfig.service_key} and trusts per {@link TlsTrust#defaultTrust()}. */
    public WSInterface(Map<String, String> wsConfig, WSCallback wsCallback) {
        this(wsConfig, wsConfig.get("service_key") == null ? null : ClientSecurity.serviceKey(wsConfig.get("service_key")), wsCallback);
    }

    public WSInterface(Map<String, String> wsConfig, ClientSecurity security, WSCallback wsCallback) {
        this.wsConfig = wsConfig;
        this.wsCallback = wsCallback;
        this.sessionMap = Collections.synchronizedMap(new HashMap<>());
        this.host = wsConfig.get("host");
        this.port = Integer.parseInt(wsConfig.get("port"));
        this.apiPath = wsConfig.get("api_path");
        this.security = security;
        this.url = "wss://" + host + ":" + port + apiPath;
    }

    /** Why the last connection attempt failed, or null. */
    public String getLastError() { return lastError; }

    /** Record (and log once per distinct reason) why a connection failed; never includes a secret. */
    void failed(Throwable cause) {
        String why = describe(cause);
        if (why == null) return;
        if (!why.equals(lastError)) LOG.error("{}: {}", url, why);
        else LOG.debug("{}: {}", url, why);
        lastError = why;
    }

    private String describe(Throwable cause) {
        Throwable t = cause;
        java.security.cert.CertificateException cert = null;
        WebSocketClientHandshakeException ws = null;
        while (t != null) {
            if (t instanceof java.security.cert.CertificateException && cert == null) cert = (java.security.cert.CertificateException) t;
            if (t instanceof WebSocketClientHandshakeException) ws = (WebSocketClientHandshakeException) t;
            if (t.getCause() == t) break;
            t = t.getCause();
        }
        if (ws != null && ws.response() != null) {
            int st = ws.response().status().code();
            String what = st == 401 ? "refused the credentials" : st == 403 ? "refused this caller"
                    : st == 429 ? "is refusing attempts from this address after repeated failures" : "refused the upgrade";
            return "wsapi at " + host + ":" + port + " " + what + " (HTTP " + st + ") for " + (security == null ? "?" : security.credentials());
        }
        if (cert != null) {
            String m = String.valueOf(cert.getMessage());
            if (m.startsWith("wsapi certificate mismatch")) return m;
            return "wsapi certificate for " + host + ":" + port + " is not trusted (" + security.trust() + "): " + m
                    + ". Nothing was sent. Pin it (TlsTrust.pinned / -Dcresco.client.tls.fingerprint, the value in ws.fingerprint) or "
                    + "trust ws.crt.pem as a CA file; both are in the wsapi plugin data directory on the agent.";
        }
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        if (root instanceof javax.net.ssl.SSLException) {
            return "TLS with " + host + ":" + port + " failed (" + security.trust() + "): " + root.getMessage() + ". Nothing was sent.";
        }
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    public String getRegionName() { return regionName; }
    public String getAgentName() { return agentName; }
    public String getPluginName() { return pluginName; }

    // Identity is carried in three DN attributes (O=region, OU=agent, CN=plugin). Read from the
    // negotiated TLS peer certificate on the given channel.
    private void setAgentInfo(Channel channel) {
        try {
            SslHandler ssl = channel.pipeline().get(SslHandler.class);
            Certificate[] cert = ssl.engine().getSession().getPeerCertificates();
            X509Certificate sd = (X509Certificate) cert[0];
            LdapName dn = new LdapName(sd.getSubjectX500Principal().getName());
            for (Rdn rdn : dn.getRdns()) {
                String type = rdn.getType();
                String value = String.valueOf(rdn.getValue());
                if ("O".equalsIgnoreCase(type)) regionName = value;
                else if ("OU".equalsIgnoreCase(type)) agentName = value;
                else if ("CN".equalsIgnoreCase(type)) pluginName = value;
            }
        } catch (Exception ex) {
            LOG.warn("setAgentInfo() could not read TLS identity", ex);
        }
    }

    public boolean connect() {
        if (!inConnect.get()) {
            inConnect.set(true);
            if (wsConfig != null && host != null && apiPath != null && security != null) {
                try {
                    if (group == null) {
                        // TLS provider: default JDK (SSLEngine). -Dcresco.client.ssl_provider=OPENSSL
                        // uses native BoringSSL (netty-tcnative) -- the client's JDK crypto is the
                        // slowest TLS path in the fabric. Falls back to JDK if the native lib is absent.
                        SslProvider provider = SslProvider.JDK;
                        if ("OPENSSL".equalsIgnoreCase(System.getProperty("cresco.client.ssl_provider", "JDK"))
                                && OpenSsl.isAvailable()) {
                            provider = SslProvider.OPENSSL;
                            LOG.info("Cresco client TLS provider: OPENSSL (native BoringSSL)");
                        }
                        sslContext = security.trust().sslContext(provider, url);
                        group = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
                    }
                    WsConn first = getSession(false);
                    if (first != null) {
                        streamSession = first;
                        setAgentInfo(first.channel());
                        isActive.set(true);
                    } else {
                        inConnect.set(false);
                    }
                } catch (Throwable t) {
                    inConnect.set(false);
                    failed(t);
                }
            } else {
                inConnect.set(false);
                LOG.warn("connect(): wsConfig missing host/port/api_path, or no credentials");
            }
        }
        return isActive.get();
    }

    public void start(int timeout) {
        this.connectionTimeout = timeout > 0 ? timeout : 30;
        new Thread(() -> {
            try {
                clearWS();
                isReconnect.set(true);
                while (isReconnect.get() && !isActive.get()) {
                    try { connect(); Thread.sleep(1000); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                }
            } catch (Exception ex) { LOG.debug("start() reconnect loop ended: {}", ex.getMessage()); }
        }).start();
    }

    public boolean getIsActive() { return isActive.get(); }
    public boolean getIsReconnect() { return isReconnect.get(); }
    public void setIsReconnect(boolean r) { this.isReconnect.set(r); }

    public boolean connected() { return isActive.get() && group != null && !group.isShutdown(); }

    public boolean SessionConnected() {
        boolean isConnected = false;
        if (isActive.get()) {
            synchronized (sessionLock) {
                WsConn c = sessionMap.get(Thread.currentThread().getId());
                if (c != null) {
                    isConnected = c.isOpen();
                    if (!isConnected) sessionMap.remove(Thread.currentThread().getId());
                }
            }
        }
        return isConnected;
    }

    public WsConn createSession(boolean setIdleTimeout) {
        try {
            final CountDownLatch handshakeLatch = new CountDownLatch(1);
            final int ioBuf = Integer.getInteger("cresco.client.io_buffer_bytes", 64 * 1024);
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_RCVBUF, 4 * 1024 * 1024)
                    .option(ChannelOption.SO_SNDBUF, 4 * 1024 * 1024)
                    .option(ChannelOption.TCP_NODELAY, true)
                    // bound the outbound write buffer so a fast producer paces on writability
                    .option(ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new io.netty.channel.WriteBufferWaterMark(ioBuf, 2 * ioBuf))
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslContext.newHandler(ch.alloc(), host, port));
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            WebSocketClientProtocolConfig cfg = WebSocketClientProtocolConfig.newBuilder()
                                    .webSocketUri(URI.create(url))
                                    .version(WebSocketVersion.V13)
                                    .allowExtensions(false)
                                    .customHeaders(security.credentials().headers())
                                    .maxFramePayloadLength(MAX_MSG_BYTES)
                                    .build();
                            p.addLast(new WebSocketClientProtocolHandler(cfg));
                            p.addLast(new WebSocketFrameAggregator(MAX_MSG_BYTES));
                            p.addLast(new NettyWsClientHandler(new WSPassThroughCallback(), handshakeLatch));
                        }
                    });
            security.trust().warnIfInsecure(url);
            Channel channel = b.connect(host, port).sync().channel();
            if (!handshakeLatch.await(connectionTimeout, TimeUnit.SECONDS) || !channel.isActive()) {
                if (lastError == null) LOG.error("createSession() handshake did not complete for {}", url);
                channel.close();
                return null;
            }
            lastError = null;
            return new WsConn(channel);
        } catch (Exception ex) {
            failed(ex);
            return null;
        }
    }

    public WsConn getSession() { return getSession(false); }

    public WsConn getSession(boolean isTemp) {
        synchronized (sessionLock) {
            WsConn existing = sessionMap.get(Thread.currentThread().getId());
            if (existing != null && existing.isOpen()) return existing;
        }
        WsConn session = createSession(isTemp);
        if (session != null) {
            synchronized (sessionLock) { sessionMap.put(Thread.currentThread().getId(), session); }
        }
        return session;
    }

    /**
     * The connection {@link #connect()} opened, or null when it is not open. Never opens one.
     *
     * <p>A stream socket (dataplane, logstreamer) is stateful: its first frame is the stream
     * handshake, which the server binds to that one connection. {@link #getSession()} pools a
     * connection PER THREAD, so a send from any thread other than the one that connected opened a
     * second socket, whose onConnect re-sent the handshake: the server then held two subscriptions
     * for the stream and every message on it reached the callback twice (2026-10-15). Streams must
     * send on this connection only; when it is down the owner's reconnect monitor re-opens it.
     *
     * @return the stream connection, or null if not connected
     */
    public WsConn getStreamSession() {
        WsConn s = streamSession;
        return (s != null && s.isOpen()) ? s : null;
    }

    /**
     * {@link #getStreamSession()}, waiting up to {@code waitMs} while a connect is in progress
     * (e.g. a send right after {@code connect()}, which connects on a background thread).
     *
     * @param waitMs longest wait in milliseconds
     * @return the stream connection, or null if it did not open in time
     */
    public WsConn getStreamSession(long waitMs) {
        long deadline = System.currentTimeMillis() + Math.max(0L, waitMs);
        WsConn s;
        while ((s = getStreamSession()) == null && isReconnect.get() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        }
        return s;
    }

    public void close() {
        isReconnect.set(false);
        clearWS();
    }

    private void clearWS() {
        isActive.set(false);
        streamSession = null;
        List<WsConn> open;
        synchronized (sessionLock) {
            open = new ArrayList<>(sessionMap.values());
            sessionMap.clear();
        }
        for (WsConn c : open) {
            try { if (c != null && c.isOpen()) c.close(); } catch (Exception e) { LOG.debug("session close: {}", e.getMessage()); }
        }
        if (group != null) {
            try { group.shutdownGracefully(); } catch (Exception e) { LOG.debug("group shutdown: {}", e.getMessage()); }
            group = null;
        }
    }

    class WSPassThroughCallback implements WSCallback {
        @Override public void onConnect(WsConn sess) { isActive.set(true); inConnect.set(false); wsCallback.onConnect(sess); }
        @Override public void onError(Throwable cause) { failed(cause); wsCallback.onError(cause); }
        @Override public void onMessage(WsConn sess, String msg) { wsCallback.onMessage(sess, msg); }
        @Override public void onMessage(byte[] b, int offset, int length) { wsCallback.onMessage(b, offset, length); }
        @Override public void onClose(int statusCode, String reason) {
            // without this, a dropped socket left isActive true, connected() kept reporting
            // healthy, and the owners' reconnect monitors never fired - the client silently
            // dropped everything from then on
            isActive.set(false);
            inConnect.set(false);
            wsCallback.onClose(statusCode, reason);
        }
    }
}
