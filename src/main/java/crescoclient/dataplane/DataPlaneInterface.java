package crescoclient.dataplane;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.core.OnMessageCallback;
import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import crescoclient.core.WsConn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A dataplane stream: a WebSocket to wsapi's {@code /api/dataplane} for publishing and receiving messages on
 * the Cresco data plane (the broker topic plugins use for bulk data). Get one with
 * {@link crescoclient.CrescoClient#get_dataplane(String, crescoclient.core.OnMessageCallback)}.
 *
 * <p>The first frame is the stream handshake, either
 * <ul>
 *   <li>a JSON stream config {@code {"ident_key":"stream_name","ident_id":"demo","io_type_key":"type","input_id":"in"}}:
 *       the stream receives messages whose {@code ident_key} property equals {@code ident_id}, and messages it sends
 *       are tagged with those properties (plus {@code io_type_key=input_id}); or</li>
 *   <li>a selector such as {@code stream_name='demo'} ({@code ident = 'value'} terms joined by AND, at least one
 *       equality): receive-only.</li>
 * </ul>
 * wsapi refuses selectors that could match other streams (OR, IS NULL, ...) and closes the socket.
 *
 * <pre>{@code
 * String stream = "{\"ident_key\":\"stream_name\",\"ident_id\":\"demo\",\"io_type_key\":\"type\",\"input_id\":\"in\"}";
 * DataPlaneInterface dp = client.get_dataplane(stream, callback);
 * dp.connect();
 * dp.send("hello");                                  // every stream subscribed to stream_name='demo' receives it
 * client.close_dataplane(stream);
 * }</pre>
 */
public class DataPlaneInterface {

    private boolean isActive = false;
    private int messageCount = 0;
    // true from each (re)connect until the server's status frame arrives: the FIRST frame of
    // EVERY session is the handshake response, not stream data. Gating on messageCount==0 was a
    // one-shot: after a reconnect the new session's status frame leaked to the consumer as data
    // and the stream could never re-activate.
    private volatile boolean handshakePending = true;
    // the broker's reason when it refused this stream's subscription (wsapi status 40), else null
    private volatile String subscriptionRefused;
    // true when the caller passed a callback (it wants to receive); a dataplane without one is a sender
    private final boolean receiver;
    private final String streamQuery;
    // client-side counters exposed via get_metrics() (parity with the Python dataplane)
    private long messagesSent = 0;
    private long bytesReceived = 0;
    private long bytesSent = 0;
    private Map<String,String> wsConfig;
    private static final Logger LOG = LoggerFactory.getLogger(DataPlaneInterface.class);
    private WSInterface wsInterface;

    private OnMessageCallback onMessageCallback;
    private Gson gson;
    private Type type = new TypeToken<Map<String, String>>(){}.getType();

    private int connectionTimeout;

    // Auto-reconnect monitor: re-establishes the stream if it drops after becoming active
    // (WSInterface.start() only retries the initial connect). Mirrors the Python _reconnect_monitor.
    private volatile boolean closing = false;
    private volatile boolean reconnecting = false;
    private Thread reconnectMonitor;
    private static final long RECONNECT_CHECK_MS = 5000;
    /** How often a receiver whose subscription was refused asks again (a policy can change); the socket stays open meanwhile. */
    static final long REFUSED_RETRY_MS = 60_000L;
    long refusedRetryMs = REFUSED_RETRY_MS;
    private volatile long refusedAt;
    // seams for tests: is the stream's socket up, and how to reopen it (a new handshake = a new subscription attempt)
    java.util.function.BooleanSupplier socketConnected = () -> wsInterface.connected();
    Runnable reopenStream = () -> { wsInterface.close(); wsInterface.start(connectionTimeout); };

    /**
     * Create a dataplane stream that prints what it receives. Prefer {@link crescoclient.CrescoClient#get_dataplane}.
     *
     * @param host              wsapi host
     * @param port              wsapi port
     * @param serviceKey        wsapi service key
     * @param streamQuery       the stream handshake (see the class description)
     * @param connectionTimeout connect timeout in seconds
     */
    public DataPlaneInterface(String host, int port, String serviceKey, String streamQuery, int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/dataplane");
        wsConfig.put("stream_query", streamQuery);

        onMessageCallback = new LogPrinter();
        receiver = false;
        this.streamQuery = streamQuery;
        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());
        gson = new Gson();
    }

    /**
     * Create a dataplane stream with a message callback. Prefer {@link crescoclient.CrescoClient#get_dataplane}.
     *
     * @param host        wsapi host
     * @param port        wsapi port
     * @param serviceKey  wsapi service key
     * @param streamQuery the stream handshake (see the class description)
     * @param onMessageCallback receives text messages ({@code onMessage(String)}) and binary messages
     *                           ({@code onMessage(byte[], int, int)})
     */
    public DataPlaneInterface(String host, int port, String serviceKey, String streamQuery, OnMessageCallback onMessageCallback) {

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/dataplane");
        wsConfig.put("stream_query", streamQuery);

        LOG.debug("DataPlaneInterface query: {}", streamQuery);

        this.onMessageCallback = onMessageCallback;
        this.receiver = onMessageCallback != null;
        this.streamQuery = streamQuery;
        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());

        gson = new Gson();
    }

    // The connection this stream's handshake went on. Sends from any thread must use it: the
    // per-thread getSession() opened a second socket that re-sent the handshake (duplicate delivery).
    private static final long STREAM_WAIT_MS = 10_000L;

    private crescoclient.core.WsConn streamSession() throws java.io.IOException {
        crescoclient.core.WsConn s = wsInterface.getStreamSession(STREAM_WAIT_MS);
        if (s == null) throw new java.io.IOException("stream not connected");
        return s;
    }

    /**
     * Publish a text message on this stream (tagged with the stream's ident and input id).
     *
     * @param message the text
     */
    public void send(String message) {

        try {
            if(wsInterface.connected()) {
                streamSession().sendText(message);
                messagesSent++;
                bytesSent += message.length();
            } else {
                LOG.warn("send(text): WS not connected");
            }
        } catch (Exception e) {
            LOG.error("send(text) failed", e);
        }
    }

    /**
     * Publish a binary message on this stream.
     *
     * @param byteBuffer the bytes (position to limit)
     */
    public void send(ByteBuffer byteBuffer) {

        try {
            if(wsInterface.connected()) {
                int n = byteBuffer.remaining();
                // Blocking send paces the sender to the consumer's drain rate (same throughput as
                // async, but bounded latency instead of an unbounded outgoing queue). The send
                // speed comes from the enlarged client OUTPUT buffer (see WSInterface), not from
                // firing async.
                streamSession().sendBinary(byteBuffer);
                messagesSent++;
                bytesSent += n;
            } else {
                LOG.warn("send(bytes): WS not connected");
            }
        } catch (Exception e) {
            LOG.error("send(bytes) failed", e);
        }
    }

    /**
     * Publish a binary message on this stream; same as {@link #send(ByteBuffer)}.
     *
     * @param byteBuffer the bytes
     */
    public void send_binary(ByteBuffer byteBuffer) {
        send(byteBuffer);
    }

    /**
     * Read a file and publish its contents as one binary message.
     *
     * @param file_path path of the file
     */
    public void send_binary_file(String file_path) {
        try {
            byte[] data = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file_path));
            send(ByteBuffer.wrap(data));
            LOG.info("Sent binary file {} ({} bytes)", file_path, data.length);
        } catch (Exception e) {
            LOG.error("send_binary_file failed: {}", file_path, e);
        }
    }

    /**
     * Send one fragment of a binary message; the receiver gets a single message once a fragment is sent with
     * {@code complete} true.
     *
     * @param byteBuffer the fragment
     * @param complete   true for the last fragment
     */
    public void send_partial(ByteBuffer byteBuffer, boolean complete) {

        try {
            if(wsInterface.connected()) {
                int n = byteBuffer.remaining();
                streamSession().sendBinary(byteBuffer, complete);
                bytesSent += n;
                if (complete) messagesSent++;
            } else {
                LOG.warn("send_partial(bytes): WS not connected");
            }
        } catch (Exception e) {
            LOG.error("send_partial(bytes) failed", e);
        }
    }

    /**
     * Send a {@code region,agent,Trace,default} control line on the stream (kept for parity with the
     * logstreamer and the Python client).
     *
     * @param dst_region region name
     * @param dst_agent  agent name
     */
    public void update_config(String dst_region, String dst_agent) {
        send(dst_region + ',' + dst_agent + ",Trace,default");
    }

    /**
     * Open the stream (send the handshake) and start the reconnect monitor, which re-opens the socket if it drops.
     */
    public void start() {
        closing = false;
        wsInterface.start(connectionTimeout);
        startReconnectMonitor();
    }

    /**
     * Open the stream; same as {@link #start()}.
     */
    public void connect() {
        start();
    }

    private synchronized void startReconnectMonitor() {
        if (reconnectMonitor != null && reconnectMonitor.isAlive()) {
            return;
        }
        reconnectMonitor = new Thread(() -> {
            try {
                Thread.sleep(2000); // initial delay before monitoring starts
                while (!closing) {
                    Thread.sleep(RECONNECT_CHECK_MS);
                    if (closing) break;
                    // refused by policy: wsapi keeps the socket open (for sending), so a refused receiver would never
                    // subscribe again; ask again every REFUSED_RETRY_MS (retrying every few seconds would not help)
                    if (retryRefusedIfDue(System.currentTimeMillis())) continue;
                    if (!wsInterface.connected()) {
                        if (!reconnecting) {
                            reconnecting = true;
                            LOG.warn("Dataplane connection lost, attempting to reconnect...");
                            wsInterface.start(connectionTimeout);
                        }
                    } else {
                        reconnecting = false;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.debug("dataplane reconnect monitor ended: {}", e.getMessage());
            }
        }, "dataplane-reconnect-monitor");
        reconnectMonitor.setDaemon(true);
        reconnectMonitor.start();
    }

    /**
     * Whether the stream's socket is connected.
     *
     * @return true if connected
     */
    public boolean connected() {
        return wsInterface.connected();
    }

    /**
     * When this is a receiver whose subscription was refused at least {@link #REFUSED_RETRY_MS} ago and its socket is still
     * open, reopen the stream: the new handshake is a new subscription attempt (status 10 if the policy now allows it, 40
     * again otherwise). Called by the reconnect monitor.
     *
     * @return true when it reopened the stream
     */
    boolean retryRefusedIfDue(long now) {
        if (!receiver || subscriptionRefused == null || closing || !socketConnected.getAsBoolean()) return false;
        if (now - refusedAt < refusedRetryMs) return false;
        refusedAt = now;
        LOG.info("dataplane " + streamQuery + ": asking again for the refused subscription (" + subscriptionRefused + ")");
        reopenStream.run();
        return true;
    }

    /** wsapi activation status: listener active. */
    public static final String STATUS_ACTIVE = "10";
    /** wsapi activation status: the broker refused the subscription; the stream stays open for sending. */
    public static final String STATUS_SUBSCRIPTION_REFUSED = "40";

    /** Apply a session's activation reply (the first frame wsapi sends). */
    void onActivationReply(Map<String, String> statusMap) {
        String code = statusMap == null ? null : statusMap.get("status_code");
        if (STATUS_ACTIVE.equals(code)) {
            subscriptionRefused = null;
            isActive = true;
        } else if (STATUS_SUBSCRIPTION_REFUSED.equals(code)) {
            subscriptionRefused = statusMap.getOrDefault("status_desc", "subscription refused");
            refusedAt = System.currentTimeMillis();
            if (receiver) {
                // a receiver that cannot receive is not active, on the first session and on every reconnect alike
                isActive = false;
                LOG.warn("dataplane " + streamQuery + ": " + subscriptionRefused + " (this receiver gets nothing)");
                try { onMessageCallback.onSubscriptionRefused(subscriptionRefused); } catch (Exception e) { LOG.error("onSubscriptionRefused failed", e); }
            } else {
                isActive = true;                                  // a sender can still send
                LOG.warn("dataplane " + streamQuery + ": " + subscriptionRefused + " (open for sending only)");
            }
        } else {
            LOG.error("dataplane listener activation failed: " + statusMap);
        }
    }

    /** The broker's reason when it refused this stream's subscription, else null. */
    public String getSubscriptionRefused() {
        return subscriptionRefused;
    }

    /**
     * Wait for the stream's activation reply.
     *
     * @return true when active (for a sender, also when the subscription was refused), false on timeout
     * @throws SubscriptionRefusedException when the broker refused the subscription of a stream that was given a callback
     */
    public boolean awaitActive(long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (!isActive && subscriptionRefused == null && System.currentTimeMillis() < end) Thread.sleep(50);
        if (subscriptionRefused != null && receiver) throw new SubscriptionRefusedException(streamQuery, subscriptionRefused);
        return isActive;
    }

    /**
     * Whether the stream's socket is connected (same as {@link #connected()}).
     *
     * @return true if connected
     */
    public boolean is_active() {
        return wsInterface.connected();
    }

    /**
     * Whether this stream is activated and its subscription was not refused: for a receiver, whether it is receiving.
     * False after a refusal, on the first session or any reconnect.
     *
     * @return true if activated and not refused
     */
    public boolean isReceiving() {
        return isActive && subscriptionRefused == null && wsInterface.connected();
    }

    /**
     * Client-side dataplane counters for this stream connection (parity with the Python client's
     * dataplane.get_metrics): stream_name, messages/bytes received and sent, and active.
     *
     * @return a map of this connection's counters
     */
    public Map<String,Object> get_metrics() {
        Map<String,Object> m = new HashMap<>();
        m.put("stream_name", wsConfig.get("stream_query"));
        m.put("messages_received", messageCount);
        m.put("messages_sent", messagesSent);
        m.put("bytes_received", bytesReceived);
        m.put("bytes_sent", bytesSent);
        m.put("active", isActive);
        return m;
    }

    /**
     * Stop the reconnect monitor and close the stream.
     */
    public void close() {
        closing = true;
        if (reconnectMonitor != null) {
            reconnectMonitor.interrupt();
        }
        wsInterface.close();
    }

    class WSLogStreamerCallback implements WSCallback {
        @Override
        public void onConnect(WsConn sess) {
            LOG.debug("WSLogStreamerCallback query: {}", wsConfig.get("stream_query"));
            handshakePending = true;   // re-arm for this session's status frame
            isActive = false;
            sess.sendText(wsConfig.get("stream_query"));
        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(WsConn sess, String msg) {
            try {
                if(handshakePending) {
                    handshakePending = false;
                    onActivationReply(gson.fromJson(msg, type));
                } else {
                    onMessageCallback.onMessage(msg);
                }
                messageCount += 1;

            } catch (Exception e) {
                LOG.error("dataplane onMessage failed", e);
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            bytesReceived += length;
            onMessageCallback.onMessage(b, offset, length);
        }

        @Override
        public void onClose(int statusCode, String reason) {

        }
    }

    class LogPrinter implements OnMessageCallback {
        @Override
        public void onMessage(String msg) {

            //System.out.println("DP LogPrinter: " + msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
        }
    }

}
