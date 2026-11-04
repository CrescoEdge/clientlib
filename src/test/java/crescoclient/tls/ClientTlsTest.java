package crescoclient.tls;

import crescoclient.CrescoClient;
import crescoclient.core.ClientSecurity;
import crescoclient.core.Credentials;
import crescoclient.core.OnMessageCallback;
import crescoclient.core.TlsTrust;
import crescoclient.core.WSCallback;
import crescoclient.core.WSInterface;
import crescoclient.core.WsConn;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Certificate verification and credentials on every client connection (API socket, dataplane, log stream). The rule:
 * a server whose certificate is not trusted receives no HTTP request, so it never sees the service key or a password.
 */
class ClientTlsTest {

    static TestServer.Cert good, other, wrongName;

    @BeforeAll
    static void certs() throws Exception {
        good = TestServer.make("CN=plugin-7,OU=agent-a,O=region-a", "dns:localhost,ip:127.0.0.1");
        other = TestServer.make("CN=evil,OU=evil,O=evil", "dns:localhost,ip:127.0.0.1");
        wrongName = TestServer.make("CN=p,OU=a,O=r", "dns:elsewhere.example");
    }

    @AfterAll
    static void clearProps() {
        System.clearProperty(TlsTrust.PROP_FINGERPRINT);
        System.clearProperty(TlsTrust.PROP_CA_FILE);
    }

    static final WSCallback NOOP = new WSCallback() {
        @Override public void onConnect(WsConn s) { }
        @Override public void onClose(int c, String r) { }
        @Override public void onError(Throwable t) { }
        @Override public void onMessage(WsConn s, String m) { }
        @Override public void onMessage(byte[] b, int o, int l) { }
    };

    static Map<String, String> cfg(int port, String path) {
        Map<String, String> m = new HashMap<>();
        m.put("host", "localhost");
        m.put("port", String.valueOf(port));
        m.put("api_path", path);
        return m;
    }

    static ClientSecurity sec(TlsTrust t) { return new ClientSecurity(t, Credentials.serviceKey("sk-test")); }

    /** One synchronous connection attempt; returns the transport (closed by the caller). */
    static WSInterface attempt(TestServer srv, ClientSecurity s) {
        WSInterface ws = new WSInterface(cfg(srv.port, "/api/apisocket"), s, NOOP);
        ws.connect();
        return ws;
    }

    // ---- pinned fingerprint --------------------------------------------------------------------------------------

    @Test
    void aPinnedCertificateConnectsWithEitherDigest() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            for (String alg : new String[]{"SHA-384", "SHA-256"}) {
                WSInterface ws = attempt(srv, sec(TlsTrust.pinned(TlsTrust.fingerprint(good.cert, alg))));
                try {
                    assertTrue(ws.getIsActive(), ws.getLastError());
                    assertEquals("region-a", ws.getRegionName());
                    assertEquals("agent-a", ws.getAgentName());
                    assertEquals("plugin-7", ws.getPluginName());
                } finally {
                    ws.close();
                }
            }
            assertEquals("sk-test", srv.requests.get(0).get("cresco_service_key"));
        }
    }

    @Test
    void aPinMismatchFailsBeforeAnyCredentialIsSent() throws Exception {
        try (TestServer srv = new TestServer(other, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.pinned(TlsTrust.fingerprint(good.cert, "SHA-384"))));
            try {
                assertFalse(ws.getIsActive());
                String e = ws.getLastError();
                assertNotNull(e);
                assertTrue(e.startsWith("wsapi certificate mismatch"), e);
                assertTrue(e.contains(TlsTrust.fingerprint(other.cert, "SHA-256")) && e.contains("Nothing was sent"), e);
            } finally {
                ws.close();
            }
            assertEquals(List.of(), srv.requests, "the impostor received an HTTP request (and the service key)");
        }
    }

    @Test
    void severalPinsAllowARotation() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.pinned(TlsTrust.fingerprint(other.cert, "SHA-256") + "," + TlsTrust.fingerprint(good.cert, "SHA-384"))));
            try { assertTrue(ws.getIsActive(), ws.getLastError()); } finally { ws.close(); }
        }
    }

    // ---- CA file / system store / host name ----------------------------------------------------------------------

    @Test
    void theServerCertificateAsACaFileConnectsAndAnotherDoesNot() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.caFile(good.pem)));
            try { assertTrue(ws.getIsActive(), ws.getLastError()); } finally { ws.close(); }
        }
        try (TestServer srv = new TestServer(other, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.caFile(good.pem)));
            try {
                assertFalse(ws.getIsActive());
                assertTrue(ws.getLastError().contains("not trusted") && ws.getLastError().contains("ws.fingerprint"), ws.getLastError());
            } finally { ws.close(); }
            assertEquals(List.of(), srv.requests);
        }
    }

    @Test
    void theHostNameIsCheckedWithACaFileUnlessTurnedOff() throws Exception {
        try (TestServer srv = new TestServer(wrongName, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.caFile(wrongName.pem)));
            try { assertFalse(ws.getIsActive()); } finally { ws.close(); }
            assertEquals(List.of(), srv.requests);
            ws = attempt(srv, sec(TlsTrust.caFile(wrongName.pem).withoutHostnameCheck()));
            try { assertTrue(ws.getIsActive(), ws.getLastError()); } finally { ws.close(); }
        }
    }

    @Test
    void theJdkStoreDoesNotTrustASelfSignedWsapi() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            WSInterface ws = attempt(srv, sec(TlsTrust.system()));
            try { assertFalse(ws.getIsActive()); } finally { ws.close(); }
            assertEquals(List.of(), srv.requests);
        }
    }

    @Test
    void trustComesFromSystemPropertiesOrTheEnvironment() {
        System.setProperty(TlsTrust.PROP_FINGERPRINT, TlsTrust.fingerprint(good.cert, "SHA-384"));
        try {
            assertEquals(TlsTrust.Mode.PINNED, TlsTrust.fromEnvironment().mode());
        } finally {
            System.clearProperty(TlsTrust.PROP_FINGERPRINT);
        }
        System.setProperty(TlsTrust.PROP_CA_FILE, good.pem.toString());
        try {
            assertEquals(TlsTrust.Mode.CA_FILE, TlsTrust.fromEnvironment().mode());
        } finally {
            System.clearProperty(TlsTrust.PROP_CA_FILE);
        }
        if (System.getenv(TlsTrust.ENV_FINGERPRINT) == null && System.getenv(TlsTrust.ENV_CA_FILE) == null) {
            assertEquals(TlsTrust.Mode.SYSTEM, TlsTrust.fromEnvironment().mode(), "verified by default");
        }
    }

    // ---- insecure ----------------------------------------------------------------------------------------------

    @Test
    void insecureConnectsAndWarnsOnEveryConnection() throws Exception {
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        Logger jul = Logger.getLogger("crescoclient.core.TlsTrust");
        Handler h = new Handler() {
            @Override public void publish(LogRecord r) { if (r.getLevel().intValue() >= java.util.logging.Level.WARNING.intValue()) warnings.add(r.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        jul.addHandler(h);
        try (TestServer srv = new TestServer(good, 0)) {
            for (int i = 0; i < 2; i++) {
                WSInterface ws = attempt(srv, sec(TlsTrust.insecure()));
                try { assertTrue(ws.getIsActive()); } finally { ws.close(); }
            }
        } finally {
            jul.removeHandler(h);
        }
        assertEquals(2, warnings.stream().filter(w -> w.contains("DISABLED")).count(), warnings.toString());
    }

    // ---- credentials -------------------------------------------------------------------------------------------

    @Test
    void perUserCredentialsGoAsBasicAndNoServiceKey() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            WSInterface ws = attempt(srv, new ClientSecurity(TlsTrust.pinned(TlsTrust.fingerprint(good.cert, "SHA-384")),
                    Credentials.user("alice", "pw-1")));
            try { assertTrue(ws.getIsActive()); } finally { ws.close(); }
            Map<String, String> h = srv.requests.get(0);
            assertNull(h.get("cresco_service_key"));
            assertEquals("Basic " + Base64.getEncoder().encodeToString("alice:pw-1".getBytes(StandardCharsets.UTF_8)), h.get("authorization"));
        }
    }

    @Test
    void aRefusedCredentialSaysSo() throws Exception {
        for (int status : new int[]{401, 403, 429}) {
            try (TestServer srv = new TestServer(good, status)) {
                WSInterface ws = attempt(srv, new ClientSecurity(TlsTrust.pinned(TlsTrust.fingerprint(good.cert, "SHA-384")),
                        Credentials.user("alice", "secret-pw")));
                try {
                    assertFalse(ws.getIsActive());
                    String e = ws.getLastError();
                    assertNotNull(e);
                    assertTrue(e.contains("HTTP " + status) && e.contains("alice") && !e.contains("secret-pw"), e);
                } finally { ws.close(); }
            }
        }
    }

    @Test
    void credentialAndPinValidation() {
        assertThrows(IllegalArgumentException.class, () -> Credentials.user("a:b", "pw"));
        assertThrows(IllegalArgumentException.class, () -> Credentials.user("alice", ""));
        assertThrows(IllegalArgumentException.class, () -> Credentials.serviceKey(""));
        assertFalse(Credentials.user("alice", "pw-x").toString().contains("pw-x"));
        assertFalse(Credentials.serviceKey("sk-x").toString().contains("sk-x"));
        for (String bad : new String[]{"", "zz", "sha256:abcd", "sha384:" + "a".repeat(64), "a".repeat(63)}) {
            assertThrows(IllegalArgumentException.class, () -> TlsTrust.pinned(bad), bad);
        }
        assertEquals(TlsTrust.Mode.PINNED, TlsTrust.pinned("SHA-256:" + "AB".repeat(32)).mode());
        assertEquals(TlsTrust.Mode.PINNED, TlsTrust.pinned("ab".repeat(48)).mode());
        assertThrows(IllegalArgumentException.class, () -> TlsTrust.caFile(java.nio.file.Path.of("/nonexistent/ca.pem")));
    }

    // ---- the client, its dataplanes and log streams -----------------------------------------------------------

    static final OnMessageCallback SINK = new OnMessageCallback() {
        @Override public void onMessage(String msg) { }
        @Override public void onMessage(byte[] b, int offset, int length) { }
    };

    static boolean waitFor(java.util.function.BooleanSupplier ok, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!ok.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(25);
        return ok.getAsBoolean();
    }

    @Test
    void theClientItsDataplaneAndLogStreamAllVerifyAndRefuseAMismatchedServer() throws Exception {
        String pin = TlsTrust.fingerprint(good.cert, "SHA-384");
        try (TestServer srv = new TestServer(good, 0)) {
            CrescoClient c = CrescoClient.builder("localhost", srv.port).credentials("alice", "pw-1").pinnedFingerprint(pin).build();
            try {
                assertTrue(c.connect(), c.last_error());
                assertTrue(c.verify_ssl());
                DataPlaneInterface dp = c.get_dataplane("{\"ident_key\":\"stream_name\",\"ident_id\":\"s1\"}", SINK);
                dp.connect();
                assertTrue(waitFor(dp::connected, 10_000), dp.last_error());
                LogStreamerInterface ls = c.get_logstreamer("logs", SINK);
                ls.connect();
                assertTrue(waitFor(ls::connected, 10_000), ls.last_error());
                assertTrue(srv.requests.stream().allMatch(h -> h.get("authorization") != null));
                assertTrue(srv.requests.stream().anyMatch(h -> "/api/dataplane".equals(h.get(":path"))));
                assertTrue(srv.requests.stream().anyMatch(h -> "/api/logstreamer".equals(h.get(":path"))));
            } finally {
                c.close();
            }
        }
        try (TestServer srv = new TestServer(other, 0)) {
            ClientSecurity s = sec(TlsTrust.pinned(pin));
            DataPlaneInterface dp = new DataPlaneInterface("localhost", srv.port, s, "{\"ident_key\":\"stream_name\",\"ident_id\":\"s1\"}", SINK);
            LogStreamerInterface ls = new LogStreamerInterface("localhost", srv.port, s, SINK);
            try {
                dp.connect();
                ls.connect();
                assertTrue(waitFor(() -> dp.last_error() != null && ls.last_error() != null, 10_000));
                assertTrue(dp.last_error().contains("mismatch") && ls.last_error().contains("mismatch"));
                assertFalse(dp.connected() || ls.connected());
            } finally {
                dp.close();
                ls.close();
            }
            assertEquals(List.of(), srv.requests, "a dataplane or log stream sent its credentials to the impostor");
        }
    }

    @Test
    void theOldConstructorsVerifyByDefaultAndVerifySslFalseIsTheExplicitOptOut() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            if (System.getenv(TlsTrust.ENV_FINGERPRINT) == null && System.getenv(TlsTrust.ENV_CA_FILE) == null) {
                CrescoClient strict = new CrescoClient("localhost", srv.port, "sk-test");
                try {
                    assertTrue(strict.verify_ssl());
                    strict.connect(false);
                    assertTrue(waitFor(() -> strict.last_error() != null, 10_000), "a connection was attempted");
                    assertFalse(strict.connected());
                    assertTrue(strict.last_error().contains("not trusted"), strict.last_error());
                } finally {
                    strict.close();
                }
                assertEquals(List.of(), srv.requests);
            }
            CrescoClient loose = new CrescoClient("localhost", srv.port, "sk-test", false);
            try {
                assertFalse(loose.verify_ssl());
                assertTrue(loose.connect());
            } finally {
                loose.close();
            }
        }
        assertThrows(IllegalStateException.class, () -> CrescoClient.builder("h", 1).build());
    }
}
