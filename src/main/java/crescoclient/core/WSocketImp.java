package crescoclient.core;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

@WebSocket
public class WSocketImp
{

    private final Logger LOG = Log.getLogger(WSocketImp.class);
    private WSCallback wSStatusCallback;
    public WSocketImp(WSCallback wSStatusCallback) {
        this.wSStatusCallback = wSStatusCallback;
    }

    @OnWebSocketConnect
    public void onConnect(Session sess)
    {
        LOG.debug("onConnect({})", sess);
        wSStatusCallback.onConnect(sess);
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        LOG.debug("onClose({}, {})", statusCode, reason);
        wSStatusCallback.onClose(statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause)
    {
        if(cause instanceof org.eclipse.jetty.websocket.api.CloseException) {
            LOG.debug(cause);
        } else {
            LOG.warn(cause);
            wSStatusCallback.onError(cause);
        }
    }

    @OnWebSocketMessage
    public void onMessage(Session sess, String msg) {

        LOG.debug("onMessage({})", msg);
        wSStatusCallback.onMessage(sess, msg);

    }

    @OnWebSocketMessage
    public void onMessage(byte[] b, int offset, int length) {
        LOG.debug("onMessage() Bytes");
        wSStatusCallback.onMessage(b, offset, length);
    }



}
