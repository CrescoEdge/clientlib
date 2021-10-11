package crescoclient;

public class CrescoClient {

    private String host;
    private int port;
    private String service_key;
    private WSInterface ws_interface;
    public Messaging messaging;
    public Agents agents;
    public Admin admin;
    public GlobalController globalcontroller;

    /**
     * Class object used to connect the client library to a Cresco websocket API endpoint
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     */
    public CrescoClient(String host, int port, String service_key) {

        this.host = host;
        this.port = port;
        this.service_key = service_key;
        this.ws_interface = new WSInterface();
        this.messaging = new Messaging(ws_interface);
        this.agents = new Agents(messaging);
        this.admin = new Admin(messaging);
        this.globalcontroller = new GlobalController(messaging);
    }

    /**
     * Method used to connect to the wsapi plugin interface
     *
     * @return true
     */
    public boolean connect() {
        ws_interface.connect();
        return true;
    }

    /**
     * Method to close the wsapi plugin interface
     *
     * @return true
     */
    public boolean close() {
        ws_interface.close();
        return true;
    }

    /**
     * Method to determine if client is connected to a wsapi plugin interface
     *
     * @return true if connected, false if not
     */
    public boolean connected() {
        return ws_interface.connected();
    }

}
