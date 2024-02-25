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
import java.util.*;
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

    private final int idleTimeout = 30 * 1000;

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

        //clean up sessions
        sessionCleanup();
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
                        //ssl.setIncludeProtocols("TLSv1.2", "TLSv1.3");
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

                            //get an initial session in order to get the agent info
                            getSession(true);
                            setAgentInfo(http);
                            isActive.set(true);

                        } catch (Throwable t) {
                            System.out.println("WHAT TYPE ERROR: " + t.getMessage());
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
                    //System.out.println("start()");
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
        boolean isConencted = false;
        if(getIsActive()) {
            if(client.getState().equals("STARTED")) {
                isConencted = true;
            }
        }
        return isConencted;

    }
    public boolean SessionConnected() {
        boolean isConnected = false;
        if(getIsActive()) {
            synchronized (sessionLock) {
                if(sessionMap.containsKey(Thread.currentThread().getId())) {
                    isConnected = sessionMap.get(Thread.currentThread().getId()).isOpen();
                    if(!isConnected) {
                        sessionMap.remove(Thread.currentThread().getId());
                    }
                }
            }
        }
        return isConnected;
    }

    public Session createSession(boolean setIdleTimeout) {
        Session session = null;
        try {
            WSocketImp socket = new WSocketImp(new WSPassThroughCallback());
            //System.out.println("url: " + url);
            //System.out.println("request: " + request);
            //System.out.println(client);
            //System.out.println(URI.create(url));
            Future<Session> fut = client.connect(socket, URI.create(url), request);
            session = fut.get();
            if(setIdleTimeout) {
                session.setIdleTimeout(idleTimeout);
            }
            //if(regionName == null) {
            //    setAgentInfo(http);
            //}
            //System.out.println("CREATE SESSIONS Thread: " + Thread.currentThread().getId());
        } catch (Exception ex) {
            System.out.println("createSession() Error: " + ex.getMessage());
            ex.printStackTrace();
        }
        return session;
    }
    public Session getSession() {
        return getSession(false);
    }
    public Session getSession(boolean isTemp) {
        Session session;
        boolean sessionExists = false;
        synchronized (sessionLock) {
            if(sessionMap.containsKey(Thread.currentThread().getId())) {
                sessionExists = SessionConnected();
            }
        }
        if(sessionExists) {
            //System.out.println("session exists");
            session = sessionMap.get(Thread.currentThread().getId());
        } else {
            //System.out.println("session create");
            session = createSession(isTemp);
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

    private int getSessionCount() {
        synchronized (sessionLock) {
            return sessionMap.size();
        }
    }

    private List<Long> getSessionList() {
        List<Long> sessionList;
        synchronized (sessionLock) {
            sessionList = new ArrayList<>(sessionMap.keySet());
        }
        return sessionList;
    }

    private void sessionCleanup() {

        try {

            //setup performance timer
            Timer timer = new Timer();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        int sessionCount = getSessionCount();
                        if(sessionCount > 0) {

                            //System.out.println("thread: " + Thread.currentThread().getId() + " session count: " + sessionCount);
                            List<Long> sessionList = getSessionList();
                            int count = 1;
                            for (Long sessionId : sessionList) {
                                boolean isOpen;
                                synchronized (sessionLock) {
                                    isOpen = sessionMap.get(sessionId).isOpen();
                                }
                                //System.out.println("thread: " + Thread.currentThread().getId() + " " + count  + " of " + sessionList.size()  +" sessionId: " + sessionId + " is open " + isOpen);
                                count++;
                                if (!isOpen) {
                                    synchronized (sessionLock) {
                                        sessionMap.remove(sessionId);
                                    }
                                }
                            }
                            //System.out.println("-");
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }

                }
            };

            // Schedule the timer task to run every second
            timer.schedule(task, 0, 30 * 1000);
        } catch (Exception ex) {
            ex.printStackTrace();
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
        public void onMessage(Session sess, String msg) {
            wsCallback.onMessage(sess, msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            wsCallback.onMessage(b, offset, length);
        }

        @Override
        public void onClose(int statusCode, String reason) {
            wsCallback.onClose(statusCode,reason);
            /*
            if(isReconnect.get()) {
                if(isActive.get()) {
                    isActive.set(false);
                    System.out.println("1");
                    start(connectionTimeout);
                }
            }
            */

        }
    }
}
