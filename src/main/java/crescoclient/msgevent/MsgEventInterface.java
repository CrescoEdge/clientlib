package crescoclient.msgevent;

import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import crescoclient.core.WsConn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.Channel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MsgEventInterface {

    private Map<String,String> wsConfig;
    private static final Logger LOG = LoggerFactory.getLogger(MsgEventInterface.class);
    private WSInterface wsInterface;

    // The wsapi protocol replies with exactly one response per request and carries no
    // correlation id, so the contract is one-outstanding-RPC-per-CONNECTION. Sessions are
    // per-thread (WSInterface.getSession keys by thread), so replies must be queued per
    // connection as well: one queue shared by all threads let thread A consume thread B's
    // reply (and send()'s stale-reply clear() nuked other threads' in-flight replies).
    private final Map<Channel, LinkedBlockingQueue<String>> replyQueues = new ConcurrentHashMap<>();
    private final ThreadLocal<LinkedBlockingQueue<String>> currentReplyQueue = new ThreadLocal<>();
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
            WsConn session = wsInterface.getSession(true);
            // drop queues for connections that no longer exist so the map cannot grow unbounded
            replyQueues.keySet().removeIf(ch -> !ch.isOpen());
            LinkedBlockingQueue<String> queue =
                    replyQueues.computeIfAbsent(session.channel(), ch -> new LinkedBlockingQueue<>());
            currentReplyQueue.set(queue);
            if (isRPC) {
                // Drop any stale reply from a previously timed-out RPC on THIS connection so
                // recv() returns the response to THIS request.
                queue.clear();
            }
            session.sendText(message);
        } catch (Exception e) {
            LOG.error("send() failed", e);
        }
    }

    public String recv() {
        try {
            LinkedBlockingQueue<String> queue = currentReplyQueue.get();
            if (queue == null) {
                LOG.warn("MsgEvent recv() called with no prior send() on this thread");
                return null;
            }
            String responce = queue.poll(rpcTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);
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
        public void onConnect(WsConn sess) {

        }

        @Override
        public void onClose(int statusCode, String reason) {

        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(WsConn sess, String msg) {
            // One reply per outstanding RPC per connection; route it to that connection's caller.
            LinkedBlockingQueue<String> queue = replyQueues.get(sess.channel());
            if (queue != null) {
                queue.offer(msg);
            } else {
                LOG.warn("MsgEvent reply on a connection with no registered caller - dropped");
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            LOG.debug("MsgEventInterface WSMsgEventCallback onMessage(byte[]) unimplemented");
        }

    }

}
