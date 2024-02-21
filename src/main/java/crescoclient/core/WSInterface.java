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

import java.net.Socket;
import java.net.URI;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

@WebSocket
public class WSInterface
{
    //private boolean isActive = false;
    private AtomicBoolean isActive = new AtomicBoolean(false);

    private AtomicBoolean sessionLock = new AtomicBoolean();
    private String regionName;
    private String agentName;
    private String pluginName;
    private AtomicBoolean isReconnect = new AtomicBoolean(true);
    private AtomicBoolean inConnect = new AtomicBoolean(false);
    private final Logger LOG = Log.getLogger(WSInterface.class);
    private HttpClient http;
    private WebSocketClient client;
    //private Session session;
    Map<Long, Session> sessionMap;
    private Map<String,String> wsConfig;

    private WSCallback wsCallback;

    private  int connectionTimeout;

    private final int idleTimeout = 300;

    private String url;

    private ClientUpgradeRequest request;

    public WSInterface(Map<String,String> wsConfig, WSCallback wsCallback) {
        this.wsConfig = wsConfig;
        this.wsCallback = wsCallback;
        this.sessionMap = Collections.synchronizedMap(new HashMap<>());
        this.url = "wss://" + wsConfig.get("host") + ":" + wsConfig.get("port") + wsConfig.get("api_path");

        this.request = new ClientUpgradeRequest();
        this.request.addExtensions("permessage-deflate");
        this.request.setHeader("cresco_service_key", wsConfig.get("service_key"));

    }

    public String getRegionName() {
        return regionName;
    }
    public String getAgentName() {
        return agentName;
    }
    public String getPluginName() {
        return pluginName;
    }
    private void setAgentInfo(HttpClient http) {

        try {
            byte[] s = http.getSslContextFactory().getSslContext().getClientSessionContext().getIds().nextElement();
            Certificate[] cert = http.getSslContextFactory().getSslContext().getClientSessionContext().getSession(s).getPeerCertificates();

            //LOG.info("WHAT: " + cert[0].getType() + ' ' + cert[0]);
            X509Certificate sd = (X509Certificate) cert[0];
            String[] cnName = sd.getIssuerX500Principal().getName().replace("CN=","").split("_");
            regionName = cnName[0];
            agentName = cnName[1];
            pluginName = cnName[2];

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public boolean serverListening(String host, int port)
    {
        Socket s = null;
        try
        {
            s = new Socket(host, port);
            return true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            //return false;
            return true;
        }
        finally
        {
            if(s != null)
                try {s.close();}
                catch(Exception e){}
        }
    }

    public boolean connect() {

        //System.out.println("THIS FIRST");

        boolean isConnected = false;

        if(!inConnect.get()) {
            inConnect.set(true);
            if (wsConfig != null) {

                if (wsConfig.containsKey("host") && wsConfig.containsKey("port") && wsConfig.containsKey("service_key") && wsConfig.containsKey("api_path")) {

                    if (serverListening(wsConfig.get("host"), Integer.parseInt(wsConfig.get("port")))) {

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
                        //no idle timeout
                        client.getPolicy().setIdleTimeout(0);
                        //set buffers
                        client.getPolicy().setMaxTextMessageSize(1024 * 1024 * 32);
                        client.getPolicy().setMaxTextMessageBufferSize(1024 * 1024 * 128);
                        client.getPolicy().setMaxBinaryMessageSize(1024 * 1024 * 32);
                        client.getPolicy().setMaxBinaryMessageBufferSize(1024 * 1024 * 128);

                        try {

                            http.start();
                            client.start();

                            /*
                            WSocketImp socket = new WSocketImp(new WSPassThroughCallback());
                            Future<Session> fut = client.connect(socket, URI.create(url), request);

                            Session session = fut.get();

                            //Set region and agent info
                            setAgentInfo(http);
                            //set connected

                            //isConnected = session.isOpen();

                             */
                            isActive.set(true);


                        } catch (Throwable t) {
                            LOG.warn(t);
                        } finally {
                            //stop(http);
                            //stop(client);
                        }
                        //System.out.println("DID YOU FINISHED");
                    } else {
                        inConnect.set(false);
                        LOG.warn("connect(): Remote server is not listening at host:" + wsConfig.get("host") + " port:" + wsConfig.get("port"));
                    }
                } else {
                    inConnect.set(false);
                    LOG.warn("connect(): wsConfig missing one or more key [host, port, api_path, service_key]");
                }
            } else {
                inConnect.set(false);
                LOG.warn("connect(): wsConfig == null");
            }
        }

        return isConnected;
    }

    public void start(int timeout) {

        this.connectionTimeout = timeout;

        new Thread(){
            public void run() {
                try {

                    //clear out previous
                    clearWS();

                    while ((isReconnect.get()) && (!isActive.get())) {
                        try {
                            connect();
                            Thread.sleep(1000);
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
        return isActive.get();
    }

    public boolean getIsReconnect() {
        return isReconnect.get();
    }

    public void setIsReconnect(boolean isReconnect) {
        this.isReconnect.set(isReconnect);
    }

    public boolean connected() {
        boolean isConnected = false;
        if(getIsActive()) {
            boolean isSession;
            synchronized (sessionLock) {
                isSession = sessionMap.containsKey(Thread.currentThread().getId());
            }
            if (!isSession) {
                getSession();
            }
            synchronized (sessionLock) {
                isConnected = sessionMap.get(Thread.currentThread().getId()).isOpen();
            }
        }
        return isConnected;
    }

    public Session createSession() {
        Session session = null;
        try {
            WSocketImp socket = new WSocketImp(new WSPassThroughCallback());
            //System.out.println("url: " + url);
            //System.out.println("request: " + request);
            //System.out.println(client);
            //System.out.println(URI.create(url));
            Future<Session> fut = client.connect(socket, URI.create(url), request);
            session = fut.get();
            if(regionName == null) {
                setAgentInfo(http);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return session;
    }
    public Session getSession() {
        Session session;

        boolean sessionExists;
        synchronized (sessionLock) {
            sessionExists = sessionMap.containsKey(Thread.currentThread().getId());
        }
        if(sessionExists) {
            session = sessionMap.get(Thread.currentThread().getId());
        } else {
            session = createSession();
            synchronized (sessionLock) {
                sessionMap.put(Thread.currentThread().getId(), session);
            }
        }

        return session;
    }
    
    public void close() {
        isReconnect.set(false);
        clearWS();
    }

    private void clearWS() {

        isActive.set(false);
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
            isActive.set(true);
            inConnect.set(false);
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
        public void onMessage(byte[] b, int offset, int length) {
            wsCallback.onMessage(b, offset, length);
        }

        @Override
        public void onClose(int statusCode, String reason) {
            wsCallback.onClose(statusCode,reason);

            if(isReconnect.get()) {
                if(isActive.get()) {
                    isActive.set(false);
                    start(connectionTimeout);
                }
            }

        }
    }
}
