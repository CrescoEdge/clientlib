package crescoclient;

import org.eclipse.jetty.websocket.api.Session;

class WSTestCallback implements WSCallback {
    @Override
    public void onConnect(Session sess) {
        System.out.println("onConnect");
    }

    @Override
    public void onError(Throwable cause) {
        System.out.println("onError");
    }
    @Override
    public void onMessage(String msg) {
        System.out.println("onMessage");
    }
    @Override
    public void onClose(int statusCode, String reason) {
        System.out.println("onClose");
    }
}