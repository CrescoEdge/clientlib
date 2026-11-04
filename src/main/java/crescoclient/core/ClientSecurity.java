package crescoclient.core;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslProvider;

/**
 * The trust, credentials and (optionally) client certificate every connection of one client uses (API socket,
 * dataplanes, log streams).
 */
public final class ClientSecurity {

    private final TlsTrust trust;
    private final Credentials credentials;
    private final ClientCertificate clientCertificate;

    public ClientSecurity(TlsTrust trust, Credentials credentials) {
        this(trust, credentials, null);
    }

    /**
     * @param clientCertificate presented on every connection (mutual TLS), or null; wsapi's production profile requires one
     */
    public ClientSecurity(TlsTrust trust, Credentials credentials, ClientCertificate clientCertificate) {
        if (trust == null || credentials == null) throw new IllegalArgumentException("trust and credentials are required");
        if (credentials.isClientCertificate() && clientCertificate == null) {
            throw new IllegalArgumentException("Credentials.clientCertificate() needs a client certificate");
        }
        this.trust = trust;
        this.credentials = credentials;
        this.clientCertificate = clientCertificate;
    }

    /** The service key, trusting per {@link TlsTrust#defaultTrust()}. */
    public static ClientSecurity serviceKey(String key) {
        return new ClientSecurity(TlsTrust.defaultTrust(), Credentials.serviceKey(key));
    }

    public TlsTrust trust() { return trust; }

    public Credentials credentials() { return credentials; }

    /** The client certificate presented on every connection, or null. */
    public ClientCertificate clientCertificate() { return clientCertificate; }

    /** The Netty client context for one connection target: the server trust, and the client certificate if any. */
    SslContext sslContext(SslProvider provider, String url) throws Exception {
        io.netty.handler.ssl.SslContextBuilder b = trust.builder(provider);
        if (clientCertificate != null) clientCertificate.apply(b);
        return b.build();
    }

    @Override
    public String toString() { return credentials + ", " + trust + (clientCertificate == null ? "" : ", " + clientCertificate); }
}
