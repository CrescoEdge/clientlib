package crescoclient.core;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.transport.HttpClientTransportDynamic;
import org.eclipse.jetty.ee10.websocket.jakarta.client.JakartaWebSocketClientContainerProvider;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Socket;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebSocket transport for the Cresco client, on Jetty 12 + jakarta.websocket (EE10) so it
 * matches the wsapi server. A trust-all Jetty {@link HttpClient} backs a jakarta
 * {@link WebSocketContainer}; identity (region/agent/plugin) is read from the negotiated
 * TLS peer certificate. All lifecycle is stopped in {@link #close()} so the JVM can exit.
 */
public class WSInterface
{
    private static final int MAX_MSG_BYTES = 32 * 1024 * 1024;

    private final AtomicBoolean isActive = new AtomicBoolean(false);
    private final AtomicBoolean sessionLock = new AtomicBoolean();
    private String regionName;
    private String agentName;
    private String pluginName;
    private final AtomicBoolean isReconnect = new AtomicBoolean(true);
    private final AtomicBoolean inConnect = new AtomicBoolean(false);
    private static final Logger LOG = LoggerFactory.getLogger(WSInterface.class);

    private HttpClient http;
    private SslContextFactory.Client ssl;
    private WebSocketContainer container;
    private final ClientEndpointConfig endpointConfig;

    private final Map<Long, Session> sessionMap;
    private final Map<String,String> wsConfig;
    private final WSCallback wsCallback;

    private int connectionTimeout;
    private final long idleTimeout = 30L * 1000L;
    private final String url;

    // Kept so close() can cancel it -- otherwise a non-daemon Timer keeps the JVM alive.
    private Timer sessionCleanupTimer;

    public WSInterface(Map<String,String> wsConfig, WSCallback wsCallback) {
        this.wsConfig = wsConfig;
        this.wsCallback = wsCallback;
        this.sessionMap = Collections.synchronizedMap(new HashMap<>());
        this.url = "wss://" + wsConfig.get("host") + ":" + wsConfig.get("port") + wsConfig.get("api_path");

        // The wsapi upgrade is authenticated by a request header; carry it on every session.
        final String serviceKey = wsConfig.get("service_key");
        this.endpointConfig = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        headers.put("cresco_service_key", Collections.singletonList(serviceKey));
                    }
                })
                .build();

        //clean up sessions
        sessionCleanup();
    }

    public String getRegionName() {
        return regionName;
    }
    public String getAgentName() {
        return agentName;
    }
    public String getPluginName() {
        return pluginName;
    }

    // Identity is carried in three DN attributes (O=region, OU=agent, CN=plugin) so each
    // stays within the X.509 64-char limit. Read from the negotiated TLS peer certificate.
    private void setAgentInfo() {
        try {
            byte[] s = ssl.getSslContext().getClientSessionContext().getIds().nextElement();
            Certificate[] cert = ssl.getSslContext().getClientSessionContext().getSession(s).getPeerCertificates();

            X509Certificate sd = (X509Certificate) cert[0];
            LdapName dn = new LdapName(sd.getSubjectX500Principal().getName());
            for (Rdn rdn : dn.getRdns()) {
                String type = rdn.getType();
                String value = String.valueOf(rdn.getValue());
                if ("O".equalsIgnoreCase(type)) {
                    regionName = value;
                } else if ("OU".equalsIgnoreCase(type)) {
                    agentName = value;
                } else if ("CN".equalsIgnoreCase(type)) {
                    pluginName = value;
                }
            }
        } catch (Exception ex) {
            LOG.warn("setAgentInfo() could not read TLS identity", ex);
        }
    }

    public boolean serverListening(String host, int port)
    {
        Socket s = null;
        try {
            s = new Socket(host, port);
            return true;
        } catch (Exception e) {
            LOG.debug("serverListening({}:{}) probe: {}", host, port, e.getMessage());
            return true;
        } finally {
            if (s != null) {
                try { s.close(); } catch (Exception ignore) { }
            }
        }
    }

    public boolean connect() {

        boolean isConnected = false;

        if (!inConnect.get()) {
            inConnect.set(true);
            if (wsConfig != null) {
                if (wsConfig.containsKey("host") && wsConfig.containsKey("port")
                        && wsConfig.containsKey("service_key") && wsConfig.containsKey("api_path")) {

                    if (serverListening(wsConfig.get("host"), Integer.parseInt(wsConfig.get("port")))) {

                        try {
                            ssl = new SslContextFactory.Client();
                            ssl.setTrustAll(true);
                            ssl.setEndpointIdentificationAlgorithm(null);

                            ClientConnector connector = new ClientConnector();
                            connector.setSslContextFactory(ssl);
                            // Enlarge the TCP socket buffers (SO_RCVBUF/SO_SNDBUF). The small
                            // default throttles bulk dataplane transfer via TCP flow control.
                            connector.setReceiveBufferSize(4 * 1024 * 1024);
                            connector.setSendBufferSize(4 * 1024 * 1024);
                            http = new HttpClient(new HttpClientTransportDynamic(connector));

                            container = JakartaWebSocketClientContainerProvider.getContainer(http);
                            if (container instanceof LifeCycle && !((LifeCycle) container).isStarted()) {
                                ((LifeCycle) container).start();
                            }
                            // Don't request permessage-deflate: binary dataplane payloads don't
                            // compress and per-message deflate is a heavy CPU cost that caps
                            // throughput. (The server also disables it.)
                            try {
                                ((org.eclipse.jetty.ee10.websocket.jakarta.common.JakartaWebSocketContainer) container)
                                        .getWebSocketComponents().getExtensionRegistry().unregister("permessage-deflate");
                            } catch (Exception dex) {
                                LOG.warn("could not disable client permessage-deflate: {}", dex.getMessage());
                            }
                            container.setDefaultMaxTextMessageBufferSize(MAX_MSG_BYTES);
                            container.setDefaultMaxBinaryMessageBufferSize(MAX_MSG_BYTES);
                            container.setDefaultMaxSessionIdleTimeout(0);

                            //get an initial session in order to get the agent info
                            getSession(false);
                            setAgentInfo();
                            isActive.set(true);

                        } catch (Throwable t) {
                            inConnect.set(false);
                            LOG.warn("connect() failed for {}: {}", url, t.getMessage());
                        }
                    } else {
                        inConnect.set(false);
                        LOG.warn("connect(): remote server not listening at host:{} port:{}",
                                wsConfig.get("host"), wsConfig.get("port"));
                    }
                } else {
                    inConnect.set(false);
                    LOG.warn("connect(): wsConfig missing one or more key [host, port, api_path, service_key]");
                }
            } else {
                inConnect.set(false);
                LOG.warn("connect(): wsConfig == null");
            }
        }

        return isConnected;
    }

    public void start(int timeout) {

        this.connectionTimeout = timeout;

        new Thread() {
            public void run() {
                try {
                    //clear out previous
                    clearWS();
                    isReconnect.set(true);
                    while ((isReconnect.get()) && (!isActive.get())) {
                        try {
                            connect();
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                } catch (Exception ex) {
                    LOG.debug("start() reconnect loop ended: {}", ex.getMessage());
                }
            }
        }.start();

    }

    public boolean getIsActive() {
        return isActive.get();
    }

    public boolean getIsReconnect() {
        return isReconnect.get();
    }

    public void setIsReconnect(boolean isReconnect) {
        this.isReconnect.set(isReconnect);
    }

    public boolean connected() {
        return getIsActive()
                && container != null
                && (!(container instanceof LifeCycle) || ((LifeCycle) container).isStarted());
    }

    public boolean SessionConnected() {
        boolean isConnected = false;
        if (getIsActive()) {
            synchronized (sessionLock) {
                if (sessionMap.containsKey(Thread.currentThread().getId())) {
                    isConnected = sessionMap.get(Thread.currentThread().getId()).isOpen();
                    if (!isConnected) {
                        sessionMap.remove(Thread.currentThread().getId());
                    }
                }
            }
        }
        return isConnected;
    }

    public Session createSession(boolean setIdleTimeout) {
        Session session = null;
        try {
            WSocketImp socket = new WSocketImp(new WSPassThroughCallback());
            session = container.connectToServer(socket, endpointConfig, URI.create(url));
            session.setMaxTextMessageBufferSize(MAX_MSG_BYTES);
            session.setMaxBinaryMessageBufferSize(MAX_MSG_BYTES);
            try {
                org.eclipse.jetty.websocket.core.CoreSession core =
                        ((org.eclipse.jetty.ee10.websocket.jakarta.common.JakartaWebSocketSession) session).getCoreSession();
                // WebSocket core I/O buffer size. NOT hardcoded: default 64KB, override via the
                // "cresco.client.io_buffer_bytes" system property. BIG is bad over TLS (wss): a
                // large WS buffer misaligns with the 16KB TLS record boundary and collapses
                // throughput (isolated Jetty drain, 256KB binary: 765 MB/s at 64KB vs 480 at 256KB
                // vs 139 at 1MB). NOTE: requires Jetty >= 12.1 — on 12.0.x setInputBufferSize
                // corrupted binary messages spanning >1 TLS record; fixed in 12.1.
                int ioBuf = Integer.getInteger("cresco.client.io_buffer_bytes", 64 * 1024);
                core.setInputBufferSize(ioBuf);
                core.setOutputBufferSize(ioBuf);
            } catch (Exception bex) {
                LOG.warn("could not tune client websocket buffers: {}", bex.getMessage());
            }
            if (setIdleTimeout) {
                session.setMaxIdleTimeout(idleTimeout);
            } else {
                session.setMaxIdleTimeout(0);
            }
        } catch (Exception ex) {
            LOG.error("createSession() failed for {}", url, ex);
        }
        return session;
    }

    public Session getSession() {
        return getSession(false);
    }

    public Session getSession(boolean isTemp) {
        Session session;
        boolean sessionExists = false;
        synchronized (sessionLock) {
            if (sessionMap.containsKey(Thread.currentThread().getId())) {
                sessionExists = SessionConnected();
            }
        }
        if (sessionExists) {
            session = sessionMap.get(Thread.currentThread().getId());
        } else {
            session = createSession(isTemp);
            synchronized (sessionLock) {
                sessionMap.put(Thread.currentThread().getId(), session);
            }
        }
        return session;
    }

    public void close() {
        isReconnect.set(false);
        if (sessionCleanupTimer != null) {
            sessionCleanupTimer.cancel();
            sessionCleanupTimer = null;
        }
        clearWS();
    }

    private void clearWS() {

        isActive.set(false);

        // Close any open sessions first.
        List<Session> open;
        synchronized (sessionLock) {
            open = new ArrayList<>(sessionMap.values());
            sessionMap.clear();
        }
        for (Session s : open) {
            try {
                if (s != null && s.isOpen()) s.close();
            } catch (Exception e) {
                LOG.debug("session close: {}", e.getMessage());
            }
        }

        // Stopping the jakarta container also stops the HttpClient it manages -- this
        // releases the Jetty selector/scheduler threads so the JVM is free to exit.
        if (container != null) {
            try {
                JakartaWebSocketClientContainerProvider.stop(container);
            } catch (Exception e) {
                LOG.debug("container stop: {}", e.getMessage());
            }
            container = null;
        }
        if (http != null) {
            try {
                http.stop();
            } catch (Exception e) {
                LOG.debug("http stop: {}", e.getMessage());
            }
            http = null;
        }
    }

    private int getSessionCount() {
        synchronized (sessionLock) {
            return sessionMap.size();
        }
    }

    private List<Long> getSessionList() {
        List<Long> sessionList;
        synchronized (sessionLock) {
            sessionList = new ArrayList<>(sessionMap.keySet());
        }
        return sessionList;
    }

    private void sessionCleanup() {

        try {
            // Daemon timer: must never keep the JVM alive after close().
            sessionCleanupTimer = new Timer("cresco-ws-session-cleanup", true);

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        int sessionCount = getSessionCount();
                        if (sessionCount > 0) {
                            List<Long> sessionList = getSessionList();
                            for (Long sessionId : sessionList) {
                                boolean isOpen;
                                synchronized (sessionLock) {
                                    Session s = sessionMap.get(sessionId);
                                    isOpen = s != null && s.isOpen();
                                }
                                if (!isOpen) {
                                    synchronized (sessionLock) {
                                        sessionMap.remove(sessionId);
                                    }
                                }
                            }
                        }
                    } catch (Exception ex) {
                        LOG.debug("session cleanup tick: {}", ex.getMessage());
                    }
                }
            };

            // run every 30s
            sessionCleanupTimer.schedule(task, 0, 30 * 1000);
        } catch (Exception ex) {
            LOG.warn("sessionCleanup() init failed", ex);
        }
    }

    class WSPassThroughCallback implements WSCallback {
        @Override
        public void onConnect(Session sess) {
            isActive.set(true);
            inConnect.set(false);
            wsCallback.onConnect(sess);
        }

        @Override
        public void onError(Throwable cause) {
            wsCallback.onError(cause);
        }

        @Override
        public void onMessage(Session sess, String msg) {
            wsCallback.onMessage(sess, msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            wsCallback.onMessage(b, offset, length);
        }

        @Override
        public void onClose(int statusCode, String reason) {
            wsCallback.onClose(statusCode, reason);
        }
    }
}
