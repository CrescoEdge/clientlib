package crescoclient.logstreamer;

import com.google.common.primitives.Bytes;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.core.OnMessageCallback;
import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import crescoclient.core.WsConn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * A log stream: a WebSocket to wsapi's {@code /api/logstreamer} that delivers log lines from agents in the mesh.
 * Get one with {@link crescoclient.CrescoClient#get_logstreamer(String, crescoclient.core.OnMessageCallback)},
 * {@link #connect()} it, then choose what to stream with {@link #update_config} / {@link #update_config_class}.
 */
public class LogStreamerInterface {

    private boolean isActive = false;
    private int messageCount = 0;
    // see DataPlaneInterface.handshakePending: first frame of EVERY (re)connect is the status
    // response; a messageCount==0 gate is one-shot and breaks after any reconnect
    private volatile boolean handshakePending = true;
    private Map<String,String> wsConfig;
    private static final Logger LOG = LoggerFactory.getLogger(LogStreamerInterface.class);
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
     * Create a log streamer that prints what it receives. Prefer {@link crescoclient.CrescoClient#get_logstreamer}.
     *
     * @param host              wsapi host
     * @param port              wsapi port
     * @param serviceKey        wsapi service key
     * @param connectionTimeout connect timeout in seconds
     */
    public LogStreamerInterface(String host, int port, String serviceKey, int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/logstreamer");

        onMessageCallback = new LogPrinter();
        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());
        gson = new Gson();
    }

    /**
     * Create a log streamer with a message callback. Prefer {@link crescoclient.CrescoClient#get_logstreamer}.
     *
     * @param host       wsapi host
     * @param port       wsapi port
     * @param serviceKey wsapi service key
     * @param onMessageCallback receives text messages ({@code onMessage(String)}) and binary messages
     *                           ({@code onMessage(byte[], int, int)})
     */
    public LogStreamerInterface(String host, int port, String serviceKey, OnMessageCallback onMessageCallback) {

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/logstreamer");

        // was never assigned: every log line hit a NullPointerException instead of the caller's callback
        this.onMessageCallback = onMessageCallback;
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
     * Send a raw control line ({@code region,agent,level,baseclass}) on the log stream.
     *
     * @param message the line
     */
    public void send(String message) {

        try {
            streamSession().sendText(message);
        } catch (Exception e) {
            LOG.error("send() failed", e);
        }
    }

    /**
     * Stream an agent's logs at Trace level for all classes ({@code region,agent,Trace,default}).
     *
     * @param dst_region region of the agent
     * @param dst_agent  agent name
     */
    public void update_config(String dst_region, String dst_agent) {
        send(dst_region + ',' + dst_agent + ",Trace,default");
    }

    /**
     * Set the level streamed for one logger base class on an agent ({@code region,agent,level,baseclass}).
     *
     * @param dst_region region of the agent
     * @param dst_agent  agent name
     * @param loglevel   Trace, Debug, Info, Warn or Error
     * @param baseclass  logger base class (package prefix), e.g. {@code io.cresco.agent}
     */
    public void update_config_class(String dst_region, String dst_agent, String loglevel, String baseclass) {
        send(dst_region + ',' + dst_agent + ',' + loglevel + ',' + baseclass);
    }

    /**
     * Open the log stream and start the reconnect monitor.
     */
    public void start() {
        closing = false;
        wsInterface.start(connectionTimeout);
        startReconnectMonitor();
    }

    /**
     * Open the log stream; same as {@link #start()}.
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
                            LOG.warn("Log streamer connection lost, attempting to reconnect...");
                            wsInterface.start(connectionTimeout);
                        }
                    } else {
                        reconnecting = false;
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.debug("logstreamer reconnect monitor ended: {}", e.getMessage());
            }
        }, "logstreamer-reconnect-monitor");
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
            handshakePending = true;   // re-arm for this session's status frame
            isActive = false;
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
                        LOG.error("logstreamer listener activation failed: " + msg);
                    }
                } else {
                    onMessageCallback.onMessage(msg);
                }
                messageCount += 1;

            } catch (Exception e) {
                LOG.error("logstreamer onMessage failed", e);
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            LOG.debug("WSLogStreamerCallback onMessage(byte[]) unimplemented");
        }

        @Override
        public void onClose(int statusCode, String reason) {

        }
    }

    class LogPrinter implements OnMessageCallback {
        @Override
        public void onMessage(String msg) {
            // Default sink for streamed log lines: this is user-facing output, not diagnostics.
            System.out.println(msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            LOG.debug("LogStreamerInterface LogPrinter onMessage(byte[]) unimplemented");
        }
    }

}
