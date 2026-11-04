package crescoclient.tls;

import crescoclient.CrescoClient;
import crescoclient.core.TlsTrust;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial review (review/api-20261104): an operator pin given to the JVM (-Dcresco.client.tls.fingerprint, or
 * CRESCO_TLS_FINGERPRINT) is dropped without an error when the code uses the legacy constructor with
 * verify_ssl=false, and the service key goes to whatever server answers. (pycrescolib behaves the same.)
 */
class ReviewApiClientTlsTest {

    @AfterEach
    void clear() {
        System.clearProperty(TlsTrust.PROP_FINGERPRINT);
    }

    @Test
    void anOperatorPinIsNotSilentlyDroppedByVerifySslFalse() throws Exception {
        TestServer.Cert real = TestServer.make("CN=plugin-7,OU=agent-a,O=region-a", "dns:localhost,ip:127.0.0.1");
        TestServer.Cert impostor = TestServer.make("CN=evil,OU=evil,O=evil", "dns:localhost,ip:127.0.0.1");
        System.setProperty(TlsTrust.PROP_FINGERPRINT, TlsTrust.fingerprint(real.cert, "SHA-384"));
        List<Map<String, String>> got;
        try (TestServer srv = new TestServer(impostor, 0)) {
            CrescoClient c;
            try {
                c = new CrescoClient("localhost", srv.port, "sk-review-secret", false);
            } catch (IllegalArgumentException refused) {
                return;                                     // refusing the conflict is a fine answer
            }
            try {
                c.connect(true);
            } finally {
                try { c.close(); } catch (Exception ignore) { }
            }
            got = new ArrayList<>(srv.requests);
        }
        assertTrue(got.stream().noneMatch(h -> "sk-review-secret".equals(h.get("cresco_service_key"))),
                "the JVM is pinned to the real wsapi, yet an impostor received the service key: " + got.size() + " request(s)");
    }
}
