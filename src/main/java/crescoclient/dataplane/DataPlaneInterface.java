package crescoclient.dataplane;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.core.OnMessageCallback;
import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class DataPlaneInterface {

    private boolean isActive = false;
    private int messageCount = 0;
    private Map<String,String> wsConfig;
    private final Logger LOG = Log.getLogger(DataPlaneInterface.class);
    private WSInterface wsInterface;

    private OnMessageCallback onMessageCallback;
    private Gson gson;
    private Type type = new TypeToken<Map<String, String>>(){}.getType();

    private int connectionTimeout;

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

    public DataPlaneInterface(String host, int port, String serviceKey, String streamQuery, OnMessageCallback onMessageCallback) {

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/dataplane");
        wsConfig.put("stream_query", streamQuery);

        this.onMessageCallback = onMessageCallback;
        wsInterface = new WSInterface(wsConfig, new WSLogStreamerCallback());

        gson = new Gson();
    }

    public void send(String message) {

        try {
            if(wsInterface.connected()) {
                wsInterface.getSession().getRemote().sendString(message);
            } else {
                System.out.println("WS not connected!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(ByteBuffer byteBuffer) {

        try {
            if(wsInterface.connected()) {
                wsInterface.getSession().getRemote().sendBytes(byteBuffer);
            } else {
                System.out.println("WS not connected!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPartial(ByteBuffer byteBuffer, boolean complete) {

        try {
            if(wsInterface.connected()) {
                System.out.println("sendPartial(ByteBuffer byteBuffer, boolean complete) support not implemented on wsapi");
                wsInterface.getSession().getRemote().sendPartialBytes(byteBuffer, complete);
            } else {
                System.out.println("WS not connected!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void update_config(String dst_region, String dst_agent) {
        send(dst_region + ',' + dst_agent + ",Trace,default");
    }

    public void start() {
        wsInterface.start(connectionTimeout);
        //wsInterface.connect();
    }

    public boolean connected() {
        return wsInterface.connected();
    }

    public void close() {
        wsInterface.close();
    }

    class WSLogStreamerCallback implements WSCallback {
        @Override
        public void onConnect(Session sess) {
            try {
                sess.getRemote().sendString(wsConfig.get("stream_query"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(String msg) {
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
                e.printStackTrace();
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            onMessageCallback.onMessage(b, offset, length);
        }

        @Override
        public void onClose(int statusCode, String reason) {

        }
    }

    class LogPrinter implements OnMessageCallback {
        @Override
        public void onMessage(String msg) {

            System.out.println("DP LogPrinter: " + msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
        }
    }

}
