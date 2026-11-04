package crescoclient.core;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * What a client connection authenticates with: a wsapi per-user credential (user name and password, sent as HTTP
 * Basic inside TLS) or the legacy service key ({@code cresco_service_key} header). {@link #toString()} never shows the
 * secret.
 */
public final class Credentials {

    private final String user;
    private final String password;
    private final String serviceKey;

    private Credentials(String user, String password, String serviceKey) {
        this.user = user;
        this.password = password;
        this.serviceKey = serviceKey;
    }

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
        if (user != null) {
            h.add(HttpHeaderNames.AUTHORIZATION, "Basic " + Base64.getEncoder()
                    .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8)));
        } else {
            h.add("cresco_service_key", serviceKey);
        }
        return h;
    }

    @Override
    public String toString() { return user != null ? "user " + user : "service key"; }
}
