package crescoclient.tls;

import crescoclient.core.ClientSecurity;
import crescoclient.core.Credentials;
import crescoclient.core.TlsTrust;
import crescoclient.msgevent.MsgEventInterface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * When wsapi closes a connection (1008 for a revoked or changed credential) the Java client's RPC fails at once, naming
 * the server's reason; it used to wait out its 30 s timeout and return nothing, and a send with no connection threw a
 * NullPointerException inside the client.
 */
class ServerCloseTest {

    static TestServer.Cert good;

    @BeforeAll
    static void certs() throws Exception {
        good = TestServer.make("CN=plugin-7,OU=agent-a,O=region-a", "dns:localhost,ip:127.0.0.1");
    }

    static MsgEventInterface client(TestServer srv) throws Exception {
        MsgEventInterface m = new MsgEventInterface("localhost", srv.port,
                new ClientSecurity(TlsTrust.pinned(TlsTrust.fingerprint(good.cert, "SHA-384")), Credentials.token("cwt1_0123456789abcdef."
                        + "A".repeat(43))));
        m.start(10);                                 // connects in the background
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end && !m.connected() && m.getLastError() == null) Thread.sleep(20);
        return m;
    }

    @Test
    void anRpcWhoseConnectionTheServerClosesFailsAtOnceWithTheServersReason() throws Exception {
        try (TestServer srv = new TestServer(good, 0)) {
            MsgEventInterface m = client(srv);
            try {
                assertTrue(m.connected(), m.getLastError());
                m.send(true, "{\"message_payload\":{\"action\":\"ping\"}}");
                assertTrue(m.recv().contains("ping"), "control: the echo server answers");
                long t0 = System.nanoTime();
                m.send(true, "{\"message_payload\":{\"action\":\"close_with_1008\"}}");
                String r = m.recv();
                double secs = (System.nanoTime() - t0) / 1e9;
                assertTrue(secs < 3, "it waited " + secs + " s");
                assertNotNull(r);
                assertTrue(r.contains("\"status\":\"499\"") && r.contains("1008 credentials revoked or changed: authenticate again"), r);
                assertTrue(m.getLastError().contains("wsapi closed the connection (1008 credentials revoked or changed"), m.getLastError());
            } finally {
                m.close();
            }
        }
    }

    @Test
    void anRpcWithNoConnectionFailsAtOnceSayingWhy() throws Exception {
        try (TestServer srv = new TestServer(good, 401)) {
            MsgEventInterface m = client(srv);
            try {
                assertNotNull(m.getLastError(), "control: refused");
                long t0 = System.nanoTime();
                m.send(true, "{\"message_payload\":{\"action\":\"ping\"}}");
                String r = m.recv();
                assertTrue((System.nanoTime() - t0) / 1e9 < 15, "no 30 s wait");
                assertNotNull(r);
                assertTrue(r.contains("\"status\":\"503\"") && r.contains("no connection to wsapi") && r.contains("401"), r);
            } finally {
                m.close();
            }
        }
    }
}
