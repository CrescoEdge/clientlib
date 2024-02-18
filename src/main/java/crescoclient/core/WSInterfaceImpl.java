package crescoclient.core;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.InputStream;

@WebSocket
public class WSInterfaceImpl
{

    private final Logger LOG = Log.getLogger(WSInterfaceImpl.class);
    private WSCallback wSStatusCallback;
    public WSInterfaceImpl(WSCallback wSStatusCallback) {
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
        LOG.warn(cause);
        wSStatusCallback.onError(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {

        LOG.debug("onMessage({})", msg);
        wSStatusCallback.onMessage(msg);

    }

    @OnWebSocketMessage
    public void onMessage(byte[] b, int offset, int length) {
        LOG.debug("onMessage() Bytes");
        wSStatusCallback.onMessage(b, offset, length);
    }



}
