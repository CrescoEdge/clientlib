package crescoclient.tls;

import crescoclient.CrescoClient;
import crescoclient.core.ClientCertificate;
import crescoclient.core.TlsTrust;
import crescoclient.dataplane.DataPlaneInterface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Client certificates (mutual TLS), which wsapi's production profile requires: a PEM certificate and key (plain or
 * encrypted) or a PKCS12 file, presented on every connection; refusals name the reason. The PKI is made with openssl.
 */
class ClientCertificateTest {

    static Path dir;
    static TestServer.Cert server;
    static Path ca, clientPem, chainPem, clientKey, encKey, p12, strangerPem, strangerKey;

    static void openssl(String... args) throws Exception {
        List<String> c = new ArrayList<>(List.of("openssl"));
        c.addAll(List.of(args));
        Process p = new ProcessBuilder(c).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, String.join(" ", c) + ": " + out);
    }

    static boolean haveOpenssl() {
        try {
            Process p = new ProcessBuilder("openssl", "version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    static void ca(String name) throws Exception {
        openssl("req", "-x509", "-newkey", "ec", "-pkeyopt", "ec_paramgen_curve:P-384", "-nodes", "-keyout", name + ".key",
                "-out", name + ".pem", "-subj", "/CN=" + name, "-days", "2",
                "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "keyUsage=critical,keyCertSign,cRLSign");
    }

    static void client(String name, String caName) throws Exception {
        openssl("genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-384", "-out", name + ".key");
        openssl("req", "-new", "-key", name + ".key", "-subj", "/CN=" + name, "-out", name + ".csr");
        Files.writeString(dir.resolve(name + ".ext"), "subjectAltName=URI:cresco:api:default/" + name + ",URI:cresco:role:user\n");
        openssl("x509", "-req", "-in", name + ".csr", "-CA", caName + ".pem", "-CAkey", caName + ".key", "-CAcreateserial",
                "-out", name + ".pem", "-days", "2", "-extfile", name + ".ext");
    }

    @BeforeAll
    static void pki() throws Exception {
        assumeTrue(haveOpenssl(), "openssl not installed");
        dir = Files.createTempDirectory("clientlib-client-cert");
        server = TestServer.make("CN=plugin-7,OU=agent-a,O=region-a", "dns:localhost,ip:127.0.0.1");
        ca("client-ca");
        ca("other-ca");
        client("carol", "client-ca");
        client("stranger", "other-ca");
        ca = dir.resolve("client-ca.pem");
        clientPem = dir.resolve("carol.pem");
        clientKey = dir.resolve("carol.key");
        chainPem = Files.writeString(dir.resolve("carol-chain.pem"), Files.readString(clientPem) + Files.readString(ca));
        openssl("pkcs8", "-topk8", "-in", "carol.key", "-out", "carol-enc.key", "-passout", "pass:s3cret-pass");
        encKey = dir.resolve("carol-enc.key");
        openssl("pkcs12", "-export", "-inkey", "carol.key", "-in", "carol.pem", "-certfile", "client-ca.pem", "-out", "carol.p12",
                "-passout", "pass:p12-pass");
        p12 = dir.resolve("carol.p12");
        strangerPem = dir.resolve("stranger.pem");
        strangerKey = dir.resolve("stranger.key");
    }

    @AfterEach
    void clearProps() {
        System.clearProperty(ClientCertificate.PROP_CERT);
        System.clearProperty(ClientCertificate.PROP_KEY);
        System.clearProperty(ClientCertificate.PROP_KEY_PASSWORD);
    }

    static X509Certificate carol() throws Exception {
        try (var in = Files.newInputStream(clientPem)) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
    }

    static String pin() { return TlsTrust.fingerprint(server.cert, "SHA-384"); }

    @Test
    void aPemClientCertificateIsPresentedAndIsTheOnlyCredential() throws Exception {
        try (TestServer srv = new TestServer(server, 0, ca)) {
            CrescoClient c = CrescoClient.builder("localhost", srv.port).clientCertificate(chainPem, clientKey, null)
                    .pinnedFingerprint(pin()).build();
            try {
                assertTrue(c.connect(), c.last_error());
            } finally {
                c.close();
            }
            assertEquals(carol(), srv.clientCerts.get(0));
            assertNull(srv.requests.get(0).get("authorization"));
            assertNull(srv.requests.get(0).get("cresco_service_key"));
        }
    }

    @Test
    void anEncryptedKeyAndAPkcs12FileWorkAndAWrongPasswordFailsEarly() throws Exception {
        try (TestServer srv = new TestServer(server, 0, ca)) {
            for (ClientCertificate cc : List.of(ClientCertificate.pem(clientPem, encKey, "s3cret-pass".toCharArray()),
                    ClientCertificate.pkcs12(p12, "p12-pass".toCharArray()))) {
                CrescoClient c = CrescoClient.builder("localhost", srv.port).clientCertificate(cc).pinnedFingerprint(pin()).build();
                try {
                    assertTrue(c.connect(), cc + ": " + c.last_error());
                } finally {
                    c.close();
                }
            }
            assertEquals(2, srv.clientCerts.size());
        }
        assertThrows(IllegalArgumentException.class, () -> ClientCertificate.pem(clientPem, encKey, "wrong".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> ClientCertificate.pkcs12(p12, "wrong".toCharArray()));
        assertThrows(IllegalArgumentException.class, () -> ClientCertificate.pem(dir.resolve("missing.pem"), clientKey, null));
        String shown = ClientCertificate.pkcs12(p12, "p12-pass".toCharArray()).toString();
        assertTrue(shown.contains("CN=carol") && shown.contains("sha256:") && !shown.contains("p12-pass"), shown);
    }

    @Test
    void withoutACertificateOrWithAnotherCasTheRefusalSaysWhy() throws Exception {
        try (TestServer srv = new TestServer(server, 0, ca)) {
            CrescoClient none = CrescoClient.builder("localhost", srv.port).serviceKey("sk-test").pinnedFingerprint(pin()).build();
            try {
                assertFalse(none.connect());
                assertTrue(none.last_error().contains("requires a client certificate"), none.last_error());
            } finally {
                none.close();
            }
            CrescoClient other = CrescoClient.builder("localhost", srv.port).clientCertificate(strangerPem, strangerKey, null)
                    .pinnedFingerprint(pin()).build();
            try {
                assertFalse(other.connect());
                assertTrue(other.last_error().contains("client certificate"), other.last_error());
            } finally {
                other.close();
            }
            assertTrue(srv.requests.isEmpty(), "nothing reached HTTP");
        }
    }

    @Test
    void theEnvironmentNamesItAndStreamsPresentItToo() throws Exception {
        System.setProperty(ClientCertificate.PROP_CERT, p12.toString());
        System.setProperty(ClientCertificate.PROP_KEY_PASSWORD, "p12-pass");
        try (TestServer srv = new TestServer(server, 0, ca)) {
            CrescoClient c = CrescoClient.builder("localhost", srv.port).pinnedFingerprint(pin()).build();
            try {
                assertTrue(c.connect(), c.last_error());
                DataPlaneInterface dp = c.get_dataplane("u:carol:x", new crescoclient.core.OnMessageCallback() {
                    public void onMessage(String msg) { }
                    public void onMessage(byte[] b, int offset, int length) { }
                });
                dp.connect();
                assertTrue(dp.awaitActive(10_000), "the dataplane presented the certificate and activated");
                dp.close();
            } finally {
                c.close();
            }
            assertTrue(srv.clientCerts.size() >= 2 && srv.clientCerts.stream().allMatch(x -> {
                try {
                    return x.equals(carol());
                } catch (Exception e) {
                    return false;
                }
            }));
        }
    }
}
