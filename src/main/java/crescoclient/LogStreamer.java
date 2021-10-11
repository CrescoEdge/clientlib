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
public class LogStreamer
{
    private final Logger LOG = Log.getLogger(LogStreamer.class);
    private HttpClient http;
    private WebSocketClient client;
    private Session session;
    private SynchronousQueue<String> messageQueue;

    public LogStreamer() {

        messageQueue = new SynchronousQueue<>();

    }

    public boolean connect(String host, int port) {

        //String url = "wss://qa.sockets.stackexchange.com/";
        //String url = "ws://localhost:8282/api/apisocket";
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
            WSInterfaceImpl socket = new WSInterfaceImpl(messageQueue);
            Future<Session> fut = client.connect(socket, URI.create(url));

            //Session session = fut.get();
            session = fut.get();
            //String payload = "{\"message_info\": {\"message_type\": \"global_agent_msgevent\", \"message_event_type\": \"EXEC\", \"dst_region\": \"global-region\", \"dst_agent\": \"global-controller\", \"is_rpc\": true}, \"message_payload\": {\"action\": \"getcontrollerstatus\"}}";
            //session.getRemote().sendString(payload);
            //Future<Void> f = session.getRemote().sendStringByFuture(payload);
            //f.get();

            //session.getRemote().sendString("Hello");
            //session.getRemote().sendString("155-questions-active");


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


}
