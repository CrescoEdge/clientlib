package crescoclient;


import crescoclient.core.*;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import crescoclient.msgevent.MsgEventInterface;
import org.eclipse.jetty.util.log.Log;

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

    /**
     * Class object used to connect the client library to a Cresco websocket API endpoint
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     */
    public CrescoClient(String host, int port, String service_key) {

        //This is needed to suppress Jetty logging
        Log.getProperties().setProperty("org.eclipse.jetty.util.log.announce", "false");
        System.setProperty("org.eclipse.jetty.util.log.class", "org.eclipse.jetty.util.log.StdErrLog");
        System.setProperty("org.eclipse.jetty.LEVEL", "OFF");
        //System.setProperty("oejusS.config.LEVEL", "OFF");

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

    /**
     * Method used to connect to the wsapi plugin interface
     *
     * @return true
     */
    public boolean connect() {
        msgEventInterface.start();
        return true;
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
        return new LogStreamerInterface(host, port, service_key);
    }

    public LogStreamerInterface getLogStreamer(OnMessageCallback onMessageCallback) {
        return new LogStreamerInterface(host, port, service_key, onMessageCallback);
    }

    public DataPlaneInterface getDataPlane(String streamQuery) {
        return new DataPlaneInterface(host, port, service_key, streamQuery);
    }

    public DataPlaneInterface getDataPlane(String streamQuery, OnMessageCallback onMessageCallback) {
        return new DataPlaneInterface(host, port, service_key, streamQuery, onMessageCallback);
    }
}
