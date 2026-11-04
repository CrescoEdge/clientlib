package crescoclient;


import crescoclient.core.*;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import crescoclient.msgevent.MsgEventInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java client for a Cresco mesh, connected through the wsapi plugin of any agent.
 *
 * <p>One client holds one authenticated control connection ({@code wss://host:port/api/apisocket}) and exposes the
 * submodules as public fields. Every connection it opens (control, dataplanes, log streams) verifies the wsapi
 * certificate ({@link TlsTrust}: pinned fingerprint, CA file, or the JDK store) before it sends anything, and
 * authenticates with the client's {@link Credentials} (per-user, or the service key):
 * <ul>
 *   <li>{@link #api} - identity of the wsapi endpoint and of the global controller</li>
 *   <li>{@link #globalcontroller} - pipelines, region/agent inventory, plugin lookup and repository, inventories</li>
 *   <li>{@link #agents} - per-agent controller status, plugin lifecycle, info, logs, CEP</li>
 *   <li>{@link #admin} - stop/restart controller or framework, kill the JVM</li>
 *   <li>{@link #messaging} - raw MsgEvents to any controller, agent or plugin</li>
 *   <li>{@link #stunnel} - TCP tunnels through the io.cresco.stunnel plugin</li>
 *   <li>{@link #filerepo} - verified file downloads from a filerepo plugin ({@code streamfile})</li>
 *   <li>{@link #executor} - OS processes through the io.cresco.executor plugin, and their stdin ({@code write_stdin})</li>
 * </ul>
 * Data-plane and log streams are separate WebSockets created with {@link #get_dataplane} and
 * {@link #get_logstreamer}; the client tracks them by name and {@link #close()} closes them all.
 *
 * <pre>{@code
 * CrescoClient client = new CrescoClient("localhost", 8282, serviceKey);
 * if (client.connect()) {
 *     System.out.println(client.globalcontroller.get_agent_list(null));
 *     client.close();
 * }
 * }</pre>
 *
 * <p>Method names are snake_case and match the Python client (pycrescolib) one to one.
 */
public class CrescoClient {

    private String host;
    private int port;
    private final ClientSecurity security;
    private boolean verify_ssl;
    private MsgEventInterface msgEventInterface;
    public Messaging messaging;
    public Agents agents;
    public Admin admin;
    public API api;
    public GlobalController globalcontroller;
    public Stunnel stunnel;
    public FileRepo filerepo;
    public Executor executor;

    // Named resource registries (mirror the Python client): track streams by identifier so they
    // can be closed/enumerated centrally.
    private final Map<String, DataPlaneInterface> dataplanes = new ConcurrentHashMap<>();
    private final Map<String, LogStreamerInterface> logstreamers = new ConcurrentHashMap<>();

    private int connectionTimeout = 5;

    /**
     * A client that authenticates with the service key and trusts per {@link TlsTrust#defaultTrust()}: the wsapi
     * certificate is verified when {@code -Dcresco.client.tls.fingerprint} / {@code -Dcresco.client.tls.ca_file} or
     * {@code CRESCO_TLS_FINGERPRINT} / {@code CRESCO_TLS_CA_FILE} names a trust, otherwise per
     * {@link TlsTrust#VERIFY_BY_DEFAULT}. Use {@link #builder} to pin a fingerprint, name a CA file or use a per-user
     * credential in code.
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     */
    public CrescoClient(String host, int port, String service_key) {
        this(host, port, ClientSecurity.serviceKey(service_key));
    }

    /**
     * A client that authenticates with the service key.
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @param service_key the security key used to allow communication with the wsapi
     * @param verify_ssl true: verify the certificate ({@link TlsTrust#fromEnvironment()}: the pin or CA file named in
     *                   the environment, else the JDK store); false: do not verify it (the service key then goes to
     *                   whoever answers; every connection logs a WARNING), unless an operator pin or CA file is set in
     *                   the environment or system properties, which always wins
     */
    public CrescoClient(String host, int port, String service_key, boolean verify_ssl) {
        this(host, port, new ClientSecurity(verify_ssl ? TlsTrust.fromEnvironment() : TlsTrust.insecure(), Credentials.serviceKey(service_key)));
    }

    /**
     * A client with explicit trust and credentials (see {@link #builder}).
     *
     * @param host     the hostname or ip of the agent running a wsapi plugin
     * @param port     the port of the listening wsapi plugin
     * @param security how every connection verifies the server certificate and what it authenticates with
     */
    public CrescoClient(String host, int port, ClientSecurity security) {

        // Jetty 12 logs via SLF4J; with the slf4j-jdk14 binding, keep Jetty quiet through JUL.
        java.util.logging.Logger.getLogger("org.eclipse.jetty").setLevel(java.util.logging.Level.WARNING);

        this.host = host;
        this.port = port;
        this.security = security;
        this.verify_ssl = security.trust().verifies();
        this.msgEventInterface = new MsgEventInterface(host, port, security);
        this.messaging = new Messaging(msgEventInterface);
        this.agents = new Agents(messaging);
        this.admin = new Admin(messaging);
        this.api = new API(msgEventInterface, messaging);
        this.globalcontroller = new GlobalController(messaging);
        this.stunnel = new Stunnel(messaging, globalcontroller);
        this.filerepo = new FileRepo(this);
        this.executor = new Executor(messaging, globalcontroller);
    }

    /**
     * Start building a client for {@code host:port}.
     *
     * <pre>{@code
     * CrescoClient c = CrescoClient.builder("agent1", 8282)
     *         .credentials("alice", password)                 // or .serviceKey(key)
     *         .pinnedFingerprint("sha384:AB:CD:...")          // or .caFile(Path.of("ws.crt.pem")); default: defaultTrust()
     *         .build();
     * }</pre>
     *
     * @param host the hostname or ip of the agent running a wsapi plugin
     * @param port the port of the listening wsapi plugin
     * @return a builder
     */
    public static Builder builder(String host, int port) {
        return new Builder(host, port);
    }

    /** Builds a {@link CrescoClient}: credentials (required) and trust (default {@link TlsTrust#defaultTrust()}). */
    public static final class Builder {
        private final String host;
        private final int port;
        private Credentials credentials;
        private TlsTrust trust;
        private boolean noHostnameCheck;

        private Builder(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /** Authenticate with the wsapi service key. */
        public Builder serviceKey(String key) { this.credentials = Credentials.serviceKey(key); return this; }

        /** Authenticate with a wsapi per-user credential. */
        public Builder credentials(String user, String password) { this.credentials = Credentials.user(user, password); return this; }

        /** Authenticate with a wsapi API token (what tools and automation should use; never throttled per user). */
        public Builder token(String token) { this.credentials = Credentials.token(token); return this; }

        /** Trust only a certificate with one of these SHA-256 / SHA-384 fingerprints (from ws.fingerprint). */
        public Builder pinnedFingerprint(String... fingerprints) { this.trust = TlsTrust.pinned(fingerprints); return this; }

        /** Trust the certificates in a PEM file (wsapi's ws.crt.pem, or a CA bundle); host name checked. */
        public Builder caFile(java.nio.file.Path pem) { this.trust = TlsTrust.caFile(pem); return this; }

        /** Use this trust. */
        public Builder trust(TlsTrust t) { this.trust = t; return this; }

        /** With a CA file or the JDK store, do not check the host name against the certificate. */
        public Builder checkHostname(boolean check) { this.noHostnameCheck = !check; return this; }

        /** Do not verify the certificate (credentials go to whoever answers; every connection logs a WARNING). */
        public Builder insecure() { this.trust = TlsTrust.insecure(); return this; }

        public CrescoClient build() {
            if (credentials == null) credentials = Credentials.fromEnvironment();      // a tool's CRESCO_API_TOKEN
            if (credentials == null) throw new IllegalStateException("credentials needed: token(...), serviceKey(...) or credentials(user, password), or CRESCO_API_TOKEN");
            TlsTrust t = trust != null ? trust : TlsTrust.defaultTrust();
            if (noHostnameCheck) t = t.withoutHostnameCheck();
            return new CrescoClient(host, port, new ClientSecurity(t, credentials));
        }
    }

    /**
     * Connect to the wsapi endpoint, blocking until connected or the 5 s connect timeout elapses.
     *
     * @return true if connected
     */
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
     * Whether this client verifies the wsapi certificate.
     *
     * @return true unless built with {@link TlsTrust#insecure()} / {@code verify_ssl=false}
     */
    public boolean verify_ssl() {
        return verify_ssl;
    }

    /**
     * Why the last connection attempt failed (an untrusted certificate, refused credentials, the network).
     *
     * @return the reason, or null
     */
    public String last_error() {
        return msgEventInterface.getLastError();
    }

    /**
     * The trust and credentials every connection of this client uses.
     *
     * @return the client's security settings
     */
    public ClientSecurity security() {
        return security;
    }

    /**
     * Get (and register under an auto-generated name) a logstreamer that prints what it receives.
     *
     * @return the logstreamer; call {@code connect()} on it
     */
    public LogStreamerInterface get_logstreamer() {
        return get_logstreamer(null, null);
    }

    /**
     * Get (and register under an auto-generated name) a logstreamer with a message callback.
     *
     * @param onMessageCallback receives the log lines
     * @return the logstreamer; call {@code connect()} on it
     */
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
                ? new LogStreamerInterface(host, port, security, connectionTimeout)
                : new LogStreamerInterface(host, port, security, onMessageCallback);
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

    /**
     * Get (and register) a dataplane for the given stream handshake that prints what it receives.
     *
     * @param stream_name the stream handshake (JSON stream config or selector; see {@link DataPlaneInterface}), also the registry key
     * @return the dataplane; call {@code connect()} on it
     */
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
    /**
     * Get (and register) a dataplane; with {@code envelope} (a JSON stream config) wsapi sends envelope frames and the
     * callback's {@link OnMessageCallback#onEnvelope} receives each message with its verified writer
     * ({@link crescoclient.dataplane.Envelope}).
     */
    public DataPlaneInterface get_dataplane(String stream_name, OnMessageCallback onMessageCallback, boolean envelope) {
        return get_dataplane(envelope ? crescoclient.dataplane.Envelope.withEnvelope(stream_name) : stream_name, onMessageCallback);
    }

    public DataPlaneInterface get_dataplane(String stream_name, OnMessageCallback onMessageCallback) {
        DataPlaneInterface dp = (onMessageCallback == null)
                ? new DataPlaneInterface(host, port, security, stream_name, connectionTimeout)
                : new DataPlaneInterface(host, port, security, stream_name, onMessageCallback);
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
