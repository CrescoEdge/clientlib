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
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MsgEventInterface {

    private AtomicBoolean queueLock = new AtomicBoolean();
    private Map<String,String> wsConfig;
    private final Logger LOG = Log.getLogger(MsgEventInterface.class);
    private WSInterface wsInterface;
    //private SynchronousQueue<String> messageQueue;

    private Map<Long, LinkedBlockingQueue<String>> messageQueueMap;

    public MsgEventInterface(String host, int port, String serviceKey) {

        messageQueueMap = Collections.synchronizedMap(new HashMap<>());
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

    public void send(String message) {

        try {

            System.out.println("SENDING 1");
            wsInterface.getSession(true).getRemote().sendString(message);
            System.out.println("SENDING 2");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String recv() {

        String responce = null;
        try {
            synchronized (queueLock) {
                if (!messageQueueMap.containsKey(Thread.currentThread().getId())) {
                    messageQueueMap.put(Thread.currentThread().getId(), new LinkedBlockingQueue<>());
                }
            }

            boolean isEmpty = true;
            while(isEmpty) {
                synchronized (queueLock) {
                    isEmpty = messageQueueMap.get(Thread.currentThread().getId()).isEmpty();
                }
                System.out.println("IS EMPTY? " + isEmpty);
                Thread.sleep(100);
            }
            System.out.println("out of loop");
            synchronized (queueLock) {
                responce = messageQueueMap.get(Thread.currentThread().getId()).take();
            }
            System.out.println("response: " + responce);
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
                System.out.println("HERE IS COMES inMessage " + msg);
                synchronized (queueLock) {
                    if (!messageQueueMap.containsKey(Thread.currentThread().getId())) {
                        messageQueueMap.put(Thread.currentThread().getId(), new LinkedBlockingQueue<>());
                    }
                }
                System.out.println("pushing " + msg);
                synchronized (queueLock) {
                    System.out.println("pushing 1" + msg);
                    messageQueueMap.get(Thread.currentThread().getId()).put(msg);
                    //messageQueueMap.get(Thread.currentThread().getId()).offer(msg);
                    System.out.println("pushing 2" + msg);
                }
                System.out.println("pushed " + msg);

            } catch (Exception e) {
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
