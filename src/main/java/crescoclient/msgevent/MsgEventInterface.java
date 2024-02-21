package crescoclient.msgevent;

import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.SynchronousQueue;

public class MsgEventInterface {

    private Map<String,String> wsConfig;
    private final Logger LOG = Log.getLogger(MsgEventInterface.class);
    private WSInterface wsInterface;
    private SynchronousQueue<String> messageQueue;
    public MsgEventInterface(String host, int port, String serviceKey) {

        messageQueue = new SynchronousQueue<>();

        wsConfig = new HashMap<>();
        wsConfig.put("host",host);
        wsConfig.put("port", String.valueOf(port));
        wsConfig.put("service_key", serviceKey);
        wsConfig.put("api_path","/api/apisocket");

        wsInterface = new WSInterface(wsConfig, new WSMsgEventCallback());

    }

    public String getRegionName() {
        return wsInterface.getRegionName();
    }
    public String getAgentName() {
        return wsInterface.getAgentName();
    }
    public String getPluginName() {
        return wsInterface.getPluginName();
    }

    public void send(String message) {

        try {

            wsInterface.getSession().getRemote().sendString(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String recv() {
        String responce = null;
        try {

            responce = messageQueue.take();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return responce;
    }

    public void start(int timeout) {
        wsInterface.start(timeout);
        //wsInterface.connect();
    }

    public boolean connected() {
        return wsInterface.connected();
    }

    public void close() {
        wsInterface.close();
    }

    class WSMsgEventCallback implements WSCallback {
        @Override
        public void onConnect(Session sess) {

        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(String msg) {
            try {
                messageQueue.put(msg);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            System.out.println("MsgEventInterface WSMsgEventCallback onMessage(Bytes[] b) unimplemented");
        }

        @Override
        public void onClose(int statusCode, String reason) {

        }
    }

}
