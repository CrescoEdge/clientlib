package crescoclient;


import crescoclient.core.*;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import crescoclient.msgevent.MsgEventInterface;

public class CrescoClient {

    private String host;
    private int port;
    private String service_key;
    private MsgEventInterface msgEventInterface;
    public Messaging messaging;
    public Agents agents;
    public Admin admin;
    public API api;
    public GlobalController globalcontroller;

    private int connectionTimeout = 5;

    /**
     * Class object used to connect the client library to a Cresco websocket API endpoint
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     */
    public CrescoClient(String host, int port, String service_key) {

        // Jetty 12 logs via SLF4J; with the slf4j-jdk14 binding, keep Jetty quiet through JUL.
        java.util.logging.Logger.getLogger("org.eclipse.jetty").setLevel(java.util.logging.Level.WARNING);

        this.host = host;
        this.port = port;
        this.service_key = service_key;
        this.msgEventInterface = new MsgEventInterface(host,port,service_key);
        this.messaging = new Messaging(msgEventInterface);
        this.agents = new Agents(messaging);
        this.admin = new Admin(messaging);
        this.api = new API(msgEventInterface, messaging);
        this.globalcontroller = new GlobalController(messaging);
    }

    public boolean connect() throws InterruptedException {
        return connect(true);
    }

    /**
     * Method used to connect to the wsapi plugin interface
     *
     * @return true
     */
    public boolean connect(boolean blockOnConnect) throws InterruptedException {
        int timeout = connectionTimeout;
        msgEventInterface.start(connectionTimeout);
        if(blockOnConnect) {
            while((!msgEventInterface.connected()) && (timeout > 0)) {
                Thread.sleep(1000);
                timeout--;
            }
        }
        return msgEventInterface.connected();
    }

    /**
     * Method to close the wsapi plugin interface
     *
     * @return true
     */
    public boolean close() {
        msgEventInterface.close();
        return true;
    }

    /**
     * Method to determine if client is connected to a wsapi plugin interface
     *
     * @return true if connected, false if not
     */
    public boolean connected() {
        return msgEventInterface.connected();
    }

    public LogStreamerInterface getLogStreamer() {
        return new LogStreamerInterface(host, port, service_key, connectionTimeout);
    }

    public LogStreamerInterface getLogStreamer(OnMessageCallback onMessageCallback) {
        return new LogStreamerInterface(host, port, service_key, onMessageCallback);
    }

    public DataPlaneInterface getDataPlane(String streamQuery) {
        return new DataPlaneInterface(host, port, service_key, streamQuery, connectionTimeout);
    }

    public DataPlaneInterface getDataPlane(String streamQuery, OnMessageCallback onMessageCallback) {
        return new DataPlaneInterface(host, port, service_key, streamQuery, onMessageCallback);
    }
}
