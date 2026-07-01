package crescoclient.msgevent;

import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

public class MsgEventInterface {

    private Map<String,String> wsConfig;
    private static final Logger LOG = LoggerFactory.getLogger(MsgEventInterface.class);
    private WSInterface wsInterface;

    // The wsapi protocol replies with exactly one response per request and carries no
    // correlation id, so the contract is one-outstanding-RPC-per-connection. A single
    // reply queue (drained one-per-call) is the correct, race-free model. The previous
    // thread/requestId map used the constant Sec-WebSocket-Key and dropped replies via an
    // NPE under rapid sequential calls, which deadlocked recv().
    private final LinkedBlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private int rpcTimeoutSeconds = 30;

    public MsgEventInterface(String host, int port, String serviceKey) {

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

    public void send(boolean isRPC, String message) {

        try {
            Session session = wsInterface.getSession(true);
            if (isRPC) {
                // Drop any stale reply from a previously timed-out RPC so recv() returns
                // the response to THIS request.
                messageQueue.clear();
            }
            session.getBasicRemote().sendText(message);
        } catch (Exception e) {
            LOG.error("send() failed", e);
        }
    }

    public String recv() {
        try {
            String responce = messageQueue.poll(rpcTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
            if (responce == null) {
                LOG.warn("MsgEvent recv() timed out after " + rpcTimeoutSeconds + "s");
            }
            return responce;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
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
        public void onClose(int statusCode, String reason) {

        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(Session sess, String msg) {
            // One reply per outstanding RPC on this connection; hand it to recv().
            messageQueue.offer(msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            LOG.debug("MsgEventInterface WSMsgEventCallback onMessage(byte[]) unimplemented");
        }

    }

}
