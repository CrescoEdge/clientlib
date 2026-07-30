package crescoclient;


import crescoclient.core.*;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import crescoclient.msgevent.MsgEventInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CrescoClient {

    private String host;
    private int port;
    private String service_key;
    private boolean verify_ssl;
    private MsgEventInterface msgEventInterface;
    public Messaging messaging;
    public Agents agents;
    public Admin admin;
    public API api;
    public GlobalController globalcontroller;
    public Stunnel stunnel;

    // Named resource registries (mirror the Python client): track streams by identifier so they
    // can be closed/enumerated centrally.
    private final Map<String, DataPlaneInterface> dataplanes = new ConcurrentHashMap<>();
    private final Map<String, LogStreamerInterface> logstreamers = new ConcurrentHashMap<>();

    private int connectionTimeout = 5;

    /**
     * Class object used to connect the client library to a Cresco websocket API endpoint
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     */
    public CrescoClient(String host, int port, String service_key) {
        this(host, port, service_key, false);
    }

    /**
     * Class object used to connect the client library to a Cresco websocket API endpoint
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     * @param verify_ssl whether to verify SSL certificates
     */
    public CrescoClient(String host, int port, String service_key, boolean verify_ssl) {

        // Jetty 12 logs via SLF4J; with the slf4j-jdk14 binding, keep Jetty quiet through JUL.
        java.util.logging.Logger.getLogger("org.eclipse.jetty").setLevel(java.util.logging.Level.WARNING);

        this.host = host;
        this.port = port;
        this.service_key = service_key;
        this.verify_ssl = verify_ssl;
        this.msgEventInterface = new MsgEventInterface(host,port,service_key);
        this.messaging = new Messaging(msgEventInterface);
        this.agents = new Agents(messaging);
        this.admin = new Admin(messaging);
        this.api = new API(msgEventInterface, messaging);
        this.globalcontroller = new GlobalController(messaging);
        this.stunnel = new Stunnel(messaging, globalcontroller);
    }

    public boolean connect() throws InterruptedException {
        return connect(true);
    }

    /**
     * Connect to the wsapi endpoint; block until connected when block_on_connect.
     *
     * @param block_on_connect block until connected (or the connect timeout elapses)
     * @return true if connected
     */
    public boolean connect(boolean block_on_connect) throws InterruptedException {
        int timeout = connectionTimeout;
        msgEventInterface.start(connectionTimeout);
        if(block_on_connect) {
            while((!msgEventInterface.connected()) && (timeout > 0)) {
                Thread.sleep(1000);
                timeout--;
            }
        }
        return msgEventInterface.connected();
    }

    /**
     * Close the client connection and all managed streams.
     *
     * @return true
     */
    public boolean close() {
        for(DataPlaneInterface dp : dataplanes.values()) {
            try { dp.close(); } catch (Exception ignore) { /* best effort */ }
        }
        dataplanes.clear();
        for(LogStreamerInterface ls : logstreamers.values()) {
            try { ls.close(); } catch (Exception ignore) { /* best effort */ }
        }
        logstreamers.clear();
        msgEventInterface.close();
        return true;
    }

    /**
     * Return True if connected to the wsapi endpoint.
     *
     * @return true if connected, false if not
     */
    public boolean connected() {
        return msgEventInterface.connected();
    }

    /**
     * Return the underlying websocket transport interface.
     *
     * @return the transport interface
     */
    public MsgEventInterface connection() {
        return msgEventInterface;
    }

    /**
     * Whether SSL certificate verification is requested for this client.
     *
     * @return true if SSL verification is enabled
     */
    public boolean verify_ssl() {
        return verify_ssl;
    }

    public LogStreamerInterface get_logstreamer() {
        return get_logstreamer(null, null);
    }

    public LogStreamerInterface get_logstreamer(OnMessageCallback onMessageCallback) {
        return get_logstreamer(null, onMessageCallback);
    }

    /**
     * Get (and register) a logstreamer.
     *
     * @param name registry name (auto-generated when null)
     * @param onMessageCallback optional message callback
     * @return the logstreamer
     */
    public LogStreamerInterface get_logstreamer(String name, OnMessageCallback onMessageCallback) {
        if(name == null) {
            name = "logstreamer-" + (logstreamers.size() + 1);
        }
        LogStreamerInterface ls = (onMessageCallback == null)
                ? new LogStreamerInterface(host, port, service_key, connectionTimeout)
                : new LogStreamerInterface(host, port, service_key, onMessageCallback);
        logstreamers.put(name, ls);
        return ls;
    }

    /**
     * Close and deregister the named logstreamer.
     *
     * @param name registry name
     * @return true if a logstreamer was closed
     */
    public boolean close_logstreamer(String name) {
        LogStreamerInterface ls = logstreamers.remove(name);
        if(ls != null) {
            ls.close();
            return true;
        }
        return false;
    }

    /**
     * List names of currently registered logstreamers.
     *
     * @return list of logstreamer names
     */
    public List<String> get_active_logstreamers() {
        return new ArrayList<>(logstreamers.keySet());
    }

    public DataPlaneInterface get_dataplane(String stream_name) {
        return get_dataplane(stream_name, null);
    }

    /**
     * Get (and register) a dataplane for the given stream query.
     *
     * @param stream_name the stream query (also the registry key)
     * @param onMessageCallback optional message callback
     * @return the dataplane
     */
    public DataPlaneInterface get_dataplane(String stream_name, OnMessageCallback onMessageCallback) {
        DataPlaneInterface dp = (onMessageCallback == null)
                ? new DataPlaneInterface(host, port, service_key, stream_name, connectionTimeout)
                : new DataPlaneInterface(host, port, service_key, stream_name, onMessageCallback);
        dataplanes.put(stream_name, dp);
        return dp;
    }

    /**
     * Close and deregister the named dataplane.
     *
     * @param stream_name the stream query used to register the dataplane
     * @return true if a dataplane was closed
     */
    public boolean close_dataplane(String stream_name) {
        DataPlaneInterface dp = dataplanes.remove(stream_name);
        if(dp != null) {
            dp.close();
            return true;
        }
        return false;
    }

    /**
     * List names of currently registered dataplanes.
     *
     * @return list of dataplane stream names
     */
    public List<String> get_active_dataplanes() {
        return new ArrayList<>(dataplanes.keySet());
    }
}
