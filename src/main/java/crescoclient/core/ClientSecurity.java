package crescoclient.core;

/** The trust and credentials every connection of one client uses (API socket, dataplanes, log streams). */
public final class ClientSecurity {

    private final TlsTrust trust;
    private final Credentials credentials;

    public ClientSecurity(TlsTrust trust, Credentials credentials) {
        if (trust == null || credentials == null) throw new IllegalArgumentException("trust and credentials are required");
        this.trust = trust;
        this.credentials = credentials;
    }

    /** The service key, trusting per {@link TlsTrust#defaultTrust()}. */
    public static ClientSecurity serviceKey(String key) {
        return new ClientSecurity(TlsTrust.defaultTrust(), Credentials.serviceKey(key));
    }

    public TlsTrust trust() { return trust; }

    public Credentials credentials() { return credentials; }

    @Override
    public String toString() { return credentials + ", " + trust; }
}
