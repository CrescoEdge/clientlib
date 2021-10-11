package crescoclient;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.util.concurrent.SynchronousQueue;

@WebSocket
public class WSInterfaceImpl
{
    private final Logger LOG = Log.getLogger(WSInterfaceImpl.class);
    public SynchronousQueue<String> messageQueue;

    public WSInterfaceImpl(SynchronousQueue<String> messageQueue) {

        this.messageQueue = messageQueue;

    }

    @OnWebSocketConnect
    public void onConnect(Session sess)
    {
        LOG.info("onConnect({})", sess);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        LOG.info("onClose({}, {})", statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause)
    {
        LOG.warn(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        try {

            messageQueue.put(msg);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
