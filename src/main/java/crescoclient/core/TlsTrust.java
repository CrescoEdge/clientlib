package crescoclient.core;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;
import java.io.InputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * How a client connection decides whether the wsapi server certificate is trusted. Used by every connection the client
 * opens: the API socket, dataplanes and log streams.
 *
 * <ul>
 *   <li>{@link #pinned(String...)}: the certificate's SHA-256 or SHA-384 fingerprint, as wsapi logs it at start and
 *       writes it to {@code ws.fingerprint} in its plugin data directory ({@code sha384:AB:CD:...}; case, colons and
 *       the prefix optional). Several pins allow a certificate rotation. The host name is not checked: the pin names
 *       the certificate itself.</li>
 *   <li>{@link #caFile(Path)}: a PEM file of trusted certificates. wsapi writes its own certificate to
 *       {@code ws.crt.pem}, which works as one. The host name is checked unless {@link #withoutHostnameCheck()}.</li>
 *   <li>{@link #system()}: the JDK trust store, with host name checking.</li>
 *   <li>{@link #insecure()}: no verification. Whoever answers receives the credentials, so every connection made this
 *       way logs a WARNING.</li>
 * </ul>
 * {@link #fromEnvironment()} reads {@code -Dcresco.client.tls.fingerprint} / {@code -Dcresco.client.tls.ca_file}, then
 * {@code CRESCO_TLS_FINGERPRINT} / {@code CRESCO_TLS_CA_FILE}, then falls back to {@link #system()}.
 * {@link #defaultTrust()} is what a client gets when its code names no trust: the same, except that with nothing in the
 * environment it follows {@link #VERIFY_BY_DEFAULT}.
 *
 * <p>A server that does not pass fails its TLS handshake, so nothing (no service key, no password) is sent to it. The
 * failure message says what was expected and what the server presented.
 */
public final class TlsTrust {

    private static final Logger LOG = LoggerFactory.getLogger(TlsTrust.class);

    public static final String PROP_FINGERPRINT = "cresco.client.tls.fingerprint";
    public static final String PROP_CA_FILE = "cresco.client.tls.ca_file";
    public static final String ENV_FINGERPRINT = "CRESCO_TLS_FINGERPRINT";
    public static final String ENV_CA_FILE = "CRESCO_TLS_CA_FILE";

    public enum Mode { SYSTEM, CA_FILE, PINNED, INSECURE }

    /**
     * Whether a client whose code names no trust setting, with nothing in the environment, verifies the certificate
     * (with the JDK store): true. Such a client fails against a self-signed wsapi until it is given a pin or CA file
     * (earlier builds did not verify). An explicit {@link #insecure()} / {@code verify_ssl=false} turns verification off
     * and warns on every connection.
     */
    public static final boolean VERIFY_BY_DEFAULT = true;

    /** A pin: the digest algorithm (SHA-256 or SHA-384) and the lowercase hex fingerprint. */
    static final class Pin {
        final String algorithm;
        final String hex;

        Pin(String algorithm, String hex) {
            this.algorithm = algorithm;
            this.hex = hex;
        }

        @Override
        public String toString() { return algorithm.replace("-", "").toLowerCase(Locale.ROOT) + ":" + hex; }
    }

    private final Mode mode;
    private final Path caFile;
    private final List<Pin> pins;
    private final boolean checkHostname;
    // an unverified default (no trust named, VERIFY_BY_DEFAULT false) does not warn; an explicit insecure() does
    private final boolean silent;

    private TlsTrust(Mode mode, Path caFile, List<Pin> pins, boolean checkHostname) {
        this(mode, caFile, pins, checkHostname, false);
    }

    private TlsTrust(Mode mode, Path caFile, List<Pin> pins, boolean checkHostname, boolean silent) {
        this.mode = mode;
        this.caFile = caFile;
        this.pins = pins;
        this.checkHostname = checkHostname;
        this.silent = silent;
    }

    /** The JDK trust store, host name checked. */
    public static TlsTrust system() { return new TlsTrust(Mode.SYSTEM, null, Collections.emptyList(), true); }

    /**
     * Trust the certificates in a PEM file, host name checked.
     *
     * @throws IllegalArgumentException the file is missing or holds no certificate
     */
    public static TlsTrust caFile(Path pem) {
        if (pem == null || !Files.isRegularFile(pem)) throw new IllegalArgumentException("TLS CA file " + pem + " does not exist");
        try (InputStream in = Files.newInputStream(pem)) {
            if (CertificateFactory.getInstance("X.509").generateCertificates(in).isEmpty()) {
                throw new IllegalArgumentException("TLS CA file " + pem + " holds no certificate");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("TLS CA file " + pem + " is not a PEM certificate file: " + e.getMessage(), e);
        }
        return new TlsTrust(Mode.CA_FILE, pem, Collections.emptyList(), true);
    }

    /**
     * Trust only a certificate with one of these fingerprints.
     *
     * @throws IllegalArgumentException a fingerprint is not SHA-256 (64 hex digits) or SHA-384 (96 hex digits)
     */
    public static TlsTrust pinned(String... fingerprints) {
        List<Pin> out = new ArrayList<>();
        if (fingerprints != null) {
            for (String f : fingerprints) {
                if (f == null) continue;
                for (String one : f.split(",")) if (!one.isBlank()) out.add(parsePin(one));
            }
        }
        if (out.isEmpty()) throw new IllegalArgumentException("no TLS fingerprint given");
        return new TlsTrust(Mode.PINNED, null, Collections.unmodifiableList(out), false);
    }

    /** No verification (logs a WARNING on every connection). */
    public static TlsTrust insecure() { return new TlsTrust(Mode.INSECURE, null, Collections.emptyList(), false); }

    /** {@code -Dcresco.client.tls.*}, then {@code CRESCO_TLS_*}, then {@link #system()}. */
    public static TlsTrust fromEnvironment() {
        String fp = first(System.getProperty(PROP_FINGERPRINT), System.getenv(ENV_FINGERPRINT));
        if (fp != null) return pinned(fp);
        String ca = first(System.getProperty(PROP_CA_FILE), System.getenv(ENV_CA_FILE));
        if (ca != null) return caFile(Paths.get(ca));
        return system();
    }

    /**
     * The trust for a client whose code names none: {@code -Dcresco.client.tls.*} or {@code CRESCO_TLS_*} when set,
     * else the JDK store when {@link #VERIFY_BY_DEFAULT}, else no verification (without a warning).
     */
    public static TlsTrust defaultTrust() {
        String fp = first(System.getProperty(PROP_FINGERPRINT), System.getenv(ENV_FINGERPRINT));
        String ca = first(System.getProperty(PROP_CA_FILE), System.getenv(ENV_CA_FILE));
        if (fp != null || ca != null || VERIFY_BY_DEFAULT) return fromEnvironment();
        return new TlsTrust(Mode.INSECURE, null, Collections.emptyList(), false, true);
    }

    /** The same trust without the host name check (CA file and system modes). */
    public TlsTrust withoutHostnameCheck() { return new TlsTrust(mode, caFile, pins, false, silent); }

    public Mode mode() { return mode; }

    public boolean verifies() { return mode != Mode.INSECURE; }

    @Override
    public String toString() {
        switch (mode) {
            case PINNED: return "pinned " + pins;
            case CA_FILE: return "CA file " + caFile + (checkHostname ? "" : " (no host name check)");
            case INSECURE: return silent ? "not verified (no trust setting given)" : "INSECURE (no verification)";
            default: return "system trust store" + (checkHostname ? "" : " (no host name check)");
        }
    }

    private static String first(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    static Pin parsePin(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        String label = null;
        int c = v.indexOf(':');
        if (c > 0) {
            String head = v.substring(0, c).replace("-", "");
            if (head.equals("sha256") || head.equals("sha384")) {
                label = head;
                v = v.substring(c + 1);
            }
        }
        String hex = v.replace(":", "").replace(" ", "");
        if (!hex.matches("[0-9a-f]+")) throw new IllegalArgumentException("TLS fingerprint is not hexadecimal: " + value);
        String alg = hex.length() == 64 ? "SHA-256" : hex.length() == 96 ? "SHA-384" : null;
        if (alg == null) throw new IllegalArgumentException("TLS fingerprint has " + hex.length() + " hex digits; SHA-256 has 64 and SHA-384 has 96");
        if (label != null && !label.equals(alg.replace("-", "").toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("TLS fingerprint is labelled " + label + " but has the length of " + alg);
        }
        return new Pin(alg, hex);
    }

    /** {@code sha256:AB:CD:...} for display. */
    public static String fingerprint(X509Certificate cert, String algorithm) {
        try {
            byte[] d = MessageDigest.getInstance(algorithm).digest(cert.getEncoded());
            StringBuilder sb = new StringBuilder(algorithm.replace("-", "").toLowerCase(Locale.ROOT)).append(':');
            for (int i = 0; i < d.length; i++) {
                if (i > 0) sb.append(':');
                sb.append(String.format("%02X", d[i] & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(algorithm + " unavailable", e);
        }
    }

    /** The Netty client context for one connection target; logs the insecure WARNING. */
    SslContext sslContext(SslProvider provider, String url) throws Exception {
        SslContextBuilder b = SslContextBuilder.forClient().sslProvider(provider).protocols("TLSv1.3", "TLSv1.2");
        switch (mode) {
            case PINNED:
                b.trustManager(new PinningTrustManager(pins)).endpointIdentificationAlgorithm(null);
                break;
            case CA_FILE:
                b.trustManager(caFile.toFile()).endpointIdentificationAlgorithm(checkHostname ? "HTTPS" : null);
                break;
            case INSECURE:
                b.trustManager(InsecureTrustManagerFactory.INSTANCE).endpointIdentificationAlgorithm(null);
                break;
            default:
                b.trustManager((javax.net.ssl.TrustManagerFactory) null).endpointIdentificationAlgorithm(checkHostname ? "HTTPS" : null);
        }
        return b.build();
    }

    /** Logged for every connection made without verification. */
    void warnIfInsecure(String url) {
        if (mode == Mode.INSECURE && !silent) {
            LOG.warn("TLS certificate verification is DISABLED for {} (verify_ssl=false / TlsTrust.insecure()): the credentials go to "
                    + "whoever answers. Pin the wsapi certificate (ws.fingerprint) or trust ws.crt.pem as a CA file.", url);
        }
    }

    /** Accepts exactly the pinned certificate (and only while it is within its validity dates). */
    static final class PinningTrustManager extends X509ExtendedTrustManager {
        private final List<Pin> pins;

        PinningTrustManager(List<Pin> pins) { this.pins = pins; }

        boolean matches(X509Certificate cert) throws CertificateException {
            boolean ok = false;
            for (Pin p : pins) {
                try {
                    byte[] d = MessageDigest.getInstance(p.algorithm).digest(cert.getEncoded());
                    byte[] want = new byte[d.length];
                    for (int i = 0; i < want.length; i++) want[i] = (byte) Integer.parseInt(p.hex.substring(2 * i, 2 * i + 2), 16);
                    ok |= MessageDigest.isEqual(d, want);
                } catch (java.security.NoSuchAlgorithmException e) {
                    throw new CertificateException(p.algorithm + " unavailable", e);
                }
            }
            return ok;
        }

        private void check(X509Certificate[] chain, String peer) throws CertificateException {
            if (chain == null || chain.length == 0) throw new CertificateException("the wsapi server " + peer + " presented no certificate");
            X509Certificate leaf = chain[0];
            if (!matches(leaf)) {
                throw new CertificateException("wsapi certificate mismatch for " + peer + ": the pinned fingerprint " + pins
                        + " does not match the certificate the server presented (" + fingerprint(leaf, "SHA-256") + ", "
                        + fingerprint(leaf, "SHA-384") + "). Nothing was sent. If the wsapi certificate was regenerated, compare "
                        + "the new value in its ws.fingerprint file before pinning it.");
            }
            leaf.checkValidity();
        }

        private static String peer(SSLEngine e) { return e == null ? "?" : e.getPeerHost() + ":" + e.getPeerPort(); }

        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException { check(chain, peer(engine)); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException { check(chain, String.valueOf(socket == null ? "?" : socket.getRemoteSocketAddress())); }
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException { check(chain, "?"); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException { throw new CertificateException("not a server trust manager"); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException { throw new CertificateException("not a server trust manager"); }
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException { throw new CertificateException("not a server trust manager"); }
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    }
}
