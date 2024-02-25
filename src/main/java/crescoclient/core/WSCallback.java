package crescoclient.core;

import org.eclipse.jetty.websocket.api.Session;


public interface WSCallback {

    void onConnect(Session sess);
    void onClose(int statusCode, String reason);

    void onError(Throwable cause);

    void onMessage(Session sess, String msg);

    void onMessage(byte[] b, int offset, int length);

}
