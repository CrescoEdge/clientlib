package crescoclient.core;

public interface WSCallback {

    void onConnect(WsConn sess);
    void onClose(int statusCode, String reason);

    void onError(Throwable cause);

    void onMessage(WsConn sess, String msg);

    void onMessage(byte[] b, int offset, int length);

}
