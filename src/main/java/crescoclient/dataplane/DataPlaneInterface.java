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
        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());

        gson = new Gson();
    }

    /**
     * Publish a text message on this stream (tagged with the stream's ident and input id).
     *
     * @param message the text
     */
    public void send(String message) {

        try {
            if(wsInterface.connected()) {
                wsInterface.getSession().sendText(message);
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
                wsInterface.getSession().sendBinary(byteBuffer);
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
                wsInterface.getSession().sendBinary(byteBuffer, complete);
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
     * Whether the stream's socket is connected (same as {@link #connected()}).
     *
     * @return true if connected
     */
    public boolean is_active() {
        return wsInterface.connected();
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
                    Map<String, String> statusMap = gson.fromJson(msg, type);
                    if(statusMap.get("status_code").equals("10")) {
                        isActive = true;
                    } else {
                        LOG.error("dataplane listener activation failed: " + msg);
                    }
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
