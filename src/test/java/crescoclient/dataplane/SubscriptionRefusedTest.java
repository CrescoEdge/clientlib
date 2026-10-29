package crescoclient.dataplane;

import crescoclient.core.OnMessageCallback;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** A refused dataplane subscription (wsapi status 40) is a typed error for a receiver; a sender carries on. */
class SubscriptionRefusedTest {

    private static final Map<String, String> REFUSAL = Map.of("status_code", "40",
            "status_desc", "Subscription refused: selector asks for the stream of recipient rgA_a1", "subscription", "refused");

    @Test
    void aReceiverGetsATypedErrorAndItsCallbackIsTold() throws Exception {
        AtomicReference<String> told = new AtomicReference<>();
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "{\"ident_key\":\"cresco_to\",\"ident_id\":\"rgA_a1\"}",
                new OnMessageCallback() {
                    @Override public void onMessage(String msg) { }
                    @Override public void onMessage(byte[] b, int offset, int length) { }
                    @Override public void onSubscriptionRefused(String reason) { told.set(reason); }
                });
        dp.onActivationReply(REFUSAL);
        SubscriptionRefusedException e = assertThrows(SubscriptionRefusedException.class, () -> dp.awaitActive(100));
        assertEquals(REFUSAL.get("status_desc"), e.getReason());
        assertEquals(REFUSAL.get("status_desc"), told.get());
        assertEquals(REFUSAL.get("status_desc"), dp.getSubscriptionRefused());
    }

    @Test
    void aSenderStaysOpen() throws Exception {
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "stream", 5000);
        dp.onActivationReply(REFUSAL);
        assertTrue(dp.awaitActive(100));
        assertNotNull(dp.getSubscriptionRefused());
    }

    @Test
    void anActiveListenerIsNotRefused() throws Exception {
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "stream", new OnMessageCallback() {
            @Override public void onMessage(String msg) { }
            @Override public void onMessage(byte[] b, int offset, int length) { }
        });
        dp.onActivationReply(Map.of("status_code", "10"));
        assertTrue(dp.awaitActive(100));
        assertNull(dp.getSubscriptionRefused());
    }
}
