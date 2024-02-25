package crescoclient.msgevent;

import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MsgEventInterface {

    private AtomicBoolean threadMapLock = new AtomicBoolean();
    private AtomicBoolean queueLock = new AtomicBoolean();
    private Map<String,String> wsConfig;
    private final Logger LOG = Log.getLogger(MsgEventInterface.class);
    private WSInterface wsInterface;

    private Map<String, Long> threadMap;
    private Map<Long, LinkedBlockingQueue<String>> messageQueueMap;

    public MsgEventInterface(String host, int port, String serviceKey) {

        messageQueueMap = Collections.synchronizedMap(new HashMap<>());
        threadMap = Collections.synchronizedMap(new HashMap<>());

        //messageQueue = new SynchronousQueue<>();

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

    private long getRequestThreadId(String requestId) {
        long threadId = -1;
        synchronized (threadMapLock) {
            threadId = threadMap.get(requestId);
        }
        return threadId;
    }

    private void setRequestThreadId(String requestId, long threadId) {
        synchronized (threadMapLock) {
            threadMap.put(requestId, threadId);
        }
    }

    private void removeRequestThreadId(String requestId) {
        synchronized (threadMapLock) {
            threadMap.remove(requestId);
        }
    }

    public void send(boolean isRPC, String message) {

        try {

            Session session = wsInterface.getSession(true);
            if(isRPC) {
                String requestId = session.getUpgradeRequest().getHeader("Sec-WebSocket-Key");
                setRequestThreadId(requestId, Thread.currentThread().getId());
                synchronized (queueLock) {
                    if(!messageQueueMap.containsKey(Thread.currentThread().getId())) {
                        messageQueueMap.put(Thread.currentThread().getId(), new LinkedBlockingQueue<>());
                    }
                }
            }
            session.getRemote().sendString(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String recv() {

        String responce = null;
        try {

            boolean isEmpty = true;
            while(isEmpty) {
                synchronized (queueLock) {
                    isEmpty = messageQueueMap.get(Thread.currentThread().getId()).isEmpty();
                }
                Thread.sleep(100);
            }

            synchronized (queueLock) {
                responce = messageQueueMap.get(Thread.currentThread().getId()).take();
            }

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
        public void onClose(int statusCode, String reason) {

        }

        @Override
        public void onError(Throwable cause) {

        }

        @Override
        public void onMessage(Session sess, String msg) {
            try {

                long threadId;

                String requestId = sess.getUpgradeRequest().getHeader("Sec-WebSocket-Key");
                threadId = getRequestThreadId(requestId);
                removeRequestThreadId(requestId);

                if(threadId == -1) {
                    System.out.println("NO THREAD ID FOR INCOMING MESSAGE!!!");
                } else {
                    synchronized (queueLock) {
                        messageQueueMap.get(threadId).put(msg);
                    }
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            System.out.println("MsgEventInterface WSMsgEventCallback onMessage(Bytes[] b) unimplemented");
        }

    }

}
