package crescoclient.core;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * What a client connection authenticates with: a wsapi API token ({@code Authorization: Bearer}), a per-user credential
 * (user name and password, sent as HTTP Basic inside TLS) or the legacy service key ({@code cresco_service_key} header).
 * {@link #toString()} never shows the secret.
 *
 * <p>Tools and automation should use an API token ({@link #token}): it acts as its user, cannot be guessed, and so wsapi
 * never throttles it per user; strangers guessing the user's password never lock a tool out.
 */
public final class Credentials {

    /** The environment variable a tool can hand its API token in. */
    public static final String ENV_API_TOKEN = "CRESCO_API_TOKEN";
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("cwt1_[0-9a-f]{16}\\.[A-Za-z0-9_-]{43}");

    private final String user;
    private final String password;
    private final String serviceKey;
    private final String token;
    private boolean clientCertificate;

    private Credentials(String user, String password, String serviceKey) {
        this(user, password, serviceKey, null);
    }

    private Credentials(String user, String password, String serviceKey, String token) {
        this.user = user;
        this.password = password;
        this.serviceKey = serviceKey;
        this.token = token;
    }

    /** A wsapi API token of a user ({@code cwt1_...}, made with wsapi's {@code CredentialTool token add}). */
    public static Credentials token(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new IllegalArgumentException("an API token looks like cwt1_<16 hex>.<43 base64url characters>");
        }
        return new Credentials(null, null, null, token);
    }

    /** The API token in {@code CRESCO_API_TOKEN}, or null when it is not set. */
    public static Credentials fromEnvironment() {
        String t = System.getenv(ENV_API_TOKEN);
        return t == null || t.isEmpty() ? null : token(t);
    }

    public boolean isToken() { return token != null; }

    /**
     * No HTTP credential: the TLS client certificate is the credential (wsapi's production profile). Use with a
     * {@link ClientCertificate} ({@link ClientSecurity}).
     */
    public static Credentials clientCertificate() {
        Credentials c = new Credentials(null, null, null, null);
        c.clientCertificate = true;
        return c;
    }

    public boolean isClientCertificate() { return clientCertificate; }

    /** The wsapi service key (an admin of the agent's tenant). */
    public static Credentials serviceKey(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("credentials needed: a service key, or a user and password");
        return new Credentials(null, null, key);
    }

    /**
     * A wsapi per-user credential.
     *
     * @throws IllegalArgumentException an empty or malformed user name (1-128 of A-Z a-z 0-9 . _ @ -) or an empty password
     */
    public static Credentials user(String user, String password) {
        if (user == null || !user.matches("[A-Za-z0-9._@-]{1,128}")) {
            throw new IllegalArgumentException("user names are 1-128 characters from A-Z a-z 0-9 . _ @ -");
        }
        if (password == null || password.isEmpty()) throw new IllegalArgumentException("a per-user credential needs a password");
        return new Credentials(user, password, null);
    }

    public boolean isUser() { return user != null; }

    public String user() { return user; }

    /** The upgrade request headers. */
    public HttpHeaders headers() {
        DefaultHttpHeaders h = new DefaultHttpHeaders();
        if (clientCertificate) return h;                  // the TLS client certificate is the credential
        if (token != null) {
            h.add(HttpHeaderNames.AUTHORIZATION, "Bearer " + token);
        } else if (user != null) {
            h.add(HttpHeaderNames.AUTHORIZATION, "Basic " + Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } else {
            h.add("cresco_service_key", serviceKey);
        }
        return h;
    }

    @Override
    public String toString() {
        if (clientCertificate) return "client certificate";
        if (token != null) return "API token " + token.substring(5, 21);
        return user != null ? "user " + user : "service key";
    }
}
