package crescoclient.core;

import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

/**
 * The client certificate every connection of a client presents (mutual TLS: the API socket, dataplanes and log streams).
 * wsapi's production profile ({@code security_profile=production}) accepts nothing else: the certificate, issued by the
 * operator's client CA, names the caller ({@code cresco:api:<tenant>/<user>} and {@code cresco:role:<admin|user>} subject
 * alternative names).
 *
 * <p>Either a PEM certificate file (the certificate, then its chain) with its PEM PKCS#8 key (encrypted or not), or a
 * PKCS12 file. The files are read and checked when the object is made (a wrong password, or a key that is not the
 * certificate's, fails here); {@link #toString()} names the certificate, never the key or the password. From the
 * environment: {@code CRESCO_TLS_CLIENT_CERT}, {@code CRESCO_TLS_CLIENT_KEY} and {@code CRESCO_TLS_CLIENT_KEY_PASSWORD}
 * (or the system properties {@code cresco.client.tls.client_cert}, {@code ..._key}, {@code ..._key_password}).
 */
public final class ClientCertificate {

    public static final String ENV_CERT = "CRESCO_TLS_CLIENT_CERT";
    public static final String ENV_KEY = "CRESCO_TLS_CLIENT_KEY";
    public static final String ENV_KEY_PASSWORD = "CRESCO_TLS_CLIENT_KEY_PASSWORD";
    public static final String PROP_CERT = "cresco.client.tls.client_cert";
    public static final String PROP_KEY = "cresco.client.tls.client_key";
    public static final String PROP_KEY_PASSWORD = "cresco.client.tls.client_key_password";

    private final Path certFile, keyFile;
    private final String keyPassword;            // PEM key password (Netty takes a String), or null
    private final KeyManagerFactory pkcs12;      // or the PKCS12 key managers
    private final X509Certificate leaf;

    private ClientCertificate(Path certFile, Path keyFile, String keyPassword, KeyManagerFactory pkcs12, X509Certificate leaf) {
        this.certFile = certFile;
        this.keyFile = keyFile;
        this.keyPassword = keyPassword;
        this.pkcs12 = pkcs12;
        this.leaf = leaf;
        try {
            apply(SslContextBuilder.forClient()).build();      // fails now, not at the first connection
        } catch (Exception e) {
            throw new IllegalArgumentException("client certificate " + certFile + " cannot be used: " + e.getMessage(), e);
        }
    }

    /**
     * A PEM certificate (then its chain) and its PEM PKCS#8 private key.
     *
     * @param password the key's password, or null when it is not encrypted
     * @throws IllegalArgumentException a file is missing or unreadable, the password is wrong, or the key is not the certificate's
     */
    public static ClientCertificate pem(Path certChain, Path key, char[] password) {
        if (certChain == null || !Files.isRegularFile(certChain)) throw new IllegalArgumentException("client certificate " + certChain + " does not exist");
        if (key == null || !Files.isRegularFile(key)) throw new IllegalArgumentException("client key " + key + " does not exist");
        X509Certificate leaf;
        try (InputStream in = Files.newInputStream(certChain)) {
            leaf = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        } catch (Exception e) {
            throw new IllegalArgumentException("client certificate " + certChain + " is not a PEM certificate: " + e.getMessage(), e);
        }
        return new ClientCertificate(certChain, key, password == null ? null : new String(password), null, leaf);
    }

    /**
     * A PKCS12 file holding the key, the certificate and its chain.
     *
     * @throws IllegalArgumentException the file is missing or unreadable, or the password is wrong
     */
    public static ClientCertificate pkcs12(Path p12, char[] password) {
        if (p12 == null || !Files.isRegularFile(p12)) throw new IllegalArgumentException("client certificate " + p12 + " does not exist");
        try (InputStream in = Files.newInputStream(p12)) {
            char[] pw = password == null ? new char[0] : password;
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, pw);
            X509Certificate leaf = null;
            for (Enumeration<String> a = ks.aliases(); a.hasMoreElements(); ) {
                String alias = a.nextElement();
                if (ks.isKeyEntry(alias) && ks.getCertificate(alias) instanceof X509Certificate) {
                    leaf = (X509Certificate) ks.getCertificate(alias);
                    break;
                }
            }
            if (leaf == null) throw new IllegalArgumentException("PKCS12 file " + p12 + " holds no key and certificate");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pw);
            return new ClientCertificate(p12, null, null, kmf, leaf);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("client certificate " + p12 + " cannot be read (a wrong password?): " + e.getMessage(), e);
        }
    }

    /** A PKCS12 file ({@code .p12}/{@code .pfx}, or no key given) or a PEM certificate with its key. */
    public static ClientCertificate of(Path cert, Path key, char[] password) {
        String n = cert == null ? "" : cert.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return key == null || n.endsWith(".p12") || n.endsWith(".pfx") ? pkcs12(cert, password) : pem(cert, key, password);
    }

    /** The client certificate the system properties or the environment name, or null when none does. */
    public static ClientCertificate fromEnvironment() {
        String cert = setting(PROP_CERT, ENV_CERT);
        if (cert == null) return null;
        String key = setting(PROP_KEY, ENV_KEY), pw = setting(PROP_KEY_PASSWORD, ENV_KEY_PASSWORD);
        return of(Paths.get(cert), key == null ? null : Paths.get(key), pw == null ? null : pw.toCharArray());
    }

    private static String setting(String prop, String env) {
        String v = System.getProperty(prop);
        if (v == null || v.isEmpty()) v = System.getenv(env);
        return v == null || v.isEmpty() ? null : v;
    }

    /** Present this certificate on the connections of {@code b}. */
    SslContextBuilder apply(SslContextBuilder b) {
        return pkcs12 != null ? b.keyManager(pkcs12) : b.keyManager(certFile.toFile(), keyFile.toFile(), keyPassword);
    }

    /** {@code sha256:<hex>} of the certificate (what wsapi's deny list names). */
    public String fingerprint() {
        try {
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded())) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public X509Certificate certificate() { return leaf; }

    @Override
    public String toString() {
        return "client certificate " + leaf.getSubjectX500Principal().getName() + " (" + fingerprint().substring(0, 23) + "...)";
    }
}
