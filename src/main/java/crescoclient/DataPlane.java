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

@WebSocket
public class DataPlane
{
    private final Logger LOG = Log.getLogger(DataPlane.class);
    private HttpClient http;
    private WebSocketClient client;
    private Session session;

    private String host;
    private int port;

    private DataPlaneCallback dataPlaneCallback;
    private DataPlaneImpl socket;
    private String streamName;

    public DataPlane(String host, int port, DataPlaneCallback dataPlaneCallback, String streamName) {
        this.dataPlaneCallback = dataPlaneCallback;
        this.host = host;
        this.port = port;
        this.streamName = streamName;
    }

    public DataPlane(String host, int port, String streamName) {
        this.dataPlaneCallback = new DPPrinter();
        this.host = host;
        this.port = port;
        this.streamName = streamName;
    }

    public boolean connect() {

        //String url = "wss://qa.sockets.stackexchange.com/";
        //String url = "ws://localhost:8282/api/apisocket";
        String url = "ws://" + host + ":" + port + "/api/dataplane";

        //SslContextFactory ssl = new SslContextFactory.Client();
        //ssl.setEndpointIdentificationAlgorithm("HTTPS");
        //HttpClient http = new HttpClient(ssl);
        http = new HttpClient();
        client = new WebSocketClient(http);
        try
        {
            http.start();
            client.start();
            socket = new DataPlaneImpl(dataPlaneCallback, streamName);
            Future<Session> fut = client.connect(socket, URI.create(url));

            //Session session = fut.get();
            session = fut.get();

            int connectionTimeout = 10;
            for(int i=0; i<connectionTimeout; i++) {
                try {
                    if(socket.isActive) {
                        i = 10;
                    } else {
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            if(!socket.isActive) {
                LOG.warn("Logger not active!");
                close();
            }

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
            if(socket.isActive) {
                session.getRemote().sendString(message);
            } else {
                LOG.warn("Can't send socket not active");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        stop(client);
        stop(http);
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

    class DPPrinter implements DataPlaneCallback {
        @Override
        public void onMessage(String msg) {
            System.out.println(msg);
        }
    }


}
