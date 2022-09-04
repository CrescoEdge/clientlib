package crescoclient;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;

@WebSocket
public class WSInterfaceOld
{
    private final Logger LOG = Log.getLogger(WSInterfaceOld.class);
    private HttpClient http;
    private WebSocketClient client;
    private Session session;
    private SynchronousQueue<String> messageQueue;

    public WSInterfaceOld() {

        messageQueue = new SynchronousQueue<>();

    }


    public boolean connect(String host, int port) {

        //String url = "wss://qa.sockets.stackexchange.com/";
        String url = "ws://" + host + ":" + port + "/api/apisocket";

        //SslContextFactory ssl = new SslContextFactory.Client();
        //ssl.setEndpointIdentificationAlgorithm("HTTPS");
        //HttpClient http = new HttpClient(ssl);
        http = new HttpClient();
        client = new WebSocketClient(http);
        try
        {
            http.start();
            client.start();
            WSInterfaceImpl socket = new WSInterfaceImpl(new WSPassThroughCallback());
            Future<Session> fut = client.connect(socket, URI.create(url));

            session = fut.get();

        }
        catch (Throwable t)
        {
            LOG.warn(t);
        }
        finally
        {
            //stop(http);
            //stop(client);
        }
        return true;
    }

    public boolean connected() {

        return session.isOpen();

    }

    public void send(String message) {

        try {

            session.getRemote().sendString(message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String recv() {
        String responce = null;
        try {

            responce = messageQueue.take();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return responce;
    }

    public void close() {
        stop(http);
        stop(client);
    }

    private void stop(LifeCycle lifeCycle) {
        try
        {
            lifeCycle.stop();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    class WSPassThroughCallback implements WSCallback {
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
}
