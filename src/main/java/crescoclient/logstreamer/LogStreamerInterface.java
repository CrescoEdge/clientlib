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

public class LogStreamerInterface {

    private boolean isActive = false;
    private int messageCount = 0;
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

    public LogStreamerInterface(String host, int port, String serviceKey, OnMessageCallback onMessageCallback) {

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/logstreamer");

        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());
        gson = new Gson();
    }

    public void send(String message) {

        try {
            wsInterface.getSession().sendText(message);
        } catch (Exception e) {
            LOG.error("send() failed", e);
        }
    }

    public void update_config(String dst_region, String dst_agent) {
        send(dst_region + ',' + dst_agent + ",Trace,default");
    }

    public void update_config_class(String dst_region, String dst_agent, String loglevel, String baseclass) {
        send(dst_region + ',' + dst_agent + ',' + loglevel + ',' + baseclass);
    }

    public void start() {
        closing = false;
        wsInterface.start(connectionTimeout);
        startReconnectMonitor();
    }

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

    public boolean connected() {
        return wsInterface.connected();
    }

    public boolean is_active() {
        return wsInterface.connected();
    }

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

        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(WsConn sess, String msg) {
            try {
                if(messageCount == 0) {
                    Map<String, String> statusMap = gson.fromJson(msg, type);
                    if(statusMap.get("status_code").equals("10")) {
                        isActive = true;
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
