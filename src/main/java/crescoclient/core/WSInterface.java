package crescoclient.core;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import javax.net.ssl.HttpsURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.Future;

@WebSocket
public class WSInterface
{
    private boolean isActive = false;

    private boolean isReconnect = true;
    private final Logger LOG = Log.getLogger(WSInterface.class);
    private HttpClient http;
    private WebSocketClient client;
    private Session session;

    private Map<String,String> wsConfig;

    private WSCallback wsCallback;

    public WSInterface(Map<String,String> wsConfig, WSCallback wsCallback) {
        this.wsConfig = wsConfig;
        this.wsCallback = wsCallback;
    }

    public boolean connect() {

        boolean isConnected = false;





        if(wsConfig != null) {
            if(wsConfig.containsKey("host") && wsConfig.containsKey("port") && wsConfig.containsKey("service_key") && wsConfig.containsKey("api_path")) {

                String url = "wss://" + wsConfig.get("host") + ":" + wsConfig.get("port") + wsConfig.get("api_path");

                SslContextFactory ssl = new SslContextFactory.Client();
                ssl.setTrustAll(true);
                ssl.setValidateCerts(false);
                ssl.setValidatePeerCerts(false);
                ssl.setEndpointIdentificationAlgorithm(null);
                ssl.setIncludeProtocols("TLSv1.2", "TLSv1.3");
                //ssl.setEndpointIdentificationAlgorithm("HTTPS");
                http = new HttpClient(ssl);
                //http = new HttpClient();
                client = new WebSocketClient(http);
                ClientUpgradeRequest request = new ClientUpgradeRequest();
                request.setHeader("cresco_service_key",wsConfig.get("service_key"));

                try
                {
                    http.start();
                    client.start();
                    WSInterfaceImpl socket = new WSInterfaceImpl(new WSPassThroughCallback());
                    Future<Session> fut = client.connect(socket, URI.create(url), request);

                    session = fut.get();
                    isConnected = session.isOpen();

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

            } else {
                LOG.warn("connect(): wsConfig missing one or more key [host, port, api_path, service_key]");
            }
        } else {
            LOG.warn("connect(): wsConfig == null");
        }

        return isConnected;
    }

    public void start() {

        //create new thread try and start continuously
        new Thread(){
            public void run() {
                try {

                    //clear out previous
                    clearWS();


                    while ((isReconnect) && (!isActive)) {
                        try {
                            connect();
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                } catch (Exception ex) {
                    //do nothing
                }
            }

        }.start();

    }

    public boolean getIsActive() {
        return isActive;
    }

    public boolean getIsReconnect() {
        return isReconnect;
    }

    public void setIsReconnect(boolean isReconnect) {
        this.isReconnect = isReconnect;
    }

    public boolean connected() {
        if(session != null) {
            return session.isOpen();
        } else {
            return false;
        }
    }

    public Session getSession() {
        return session;
    }


    public void close() {
        isReconnect = false;
        clearWS();
    }

    private void clearWS() {

        isActive = false;
        if(client != null) {
            stopLC(client);
        }
        if(http != null) {
            stopLC(http);
        }
    }

    private void stopLC(LifeCycle lifeCycle) {
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
            isActive = true;
            wsCallback.onConnect(sess);
        }

        @Override
        public void onError(Throwable cause) {
            wsCallback.onError(cause);
        }

        @Override
        public void onMessage(String msg) {
            wsCallback.onMessage(msg);
        }

        @Override
        public void onClose(int statusCode, String reason) {
            wsCallback.onClose(statusCode,reason);

            if(isReconnect) {
                if(isActive) {
                    start();
                }
            }
        }
    }
}
