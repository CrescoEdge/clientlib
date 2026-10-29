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

    @Test
    void aRefusedReceiverAsksAgainEverySixtySecondsWhileItsSocketStaysOpen() throws Exception {
        java.util.concurrent.atomic.AtomicInteger reopened = new java.util.concurrent.atomic.AtomicInteger();
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "stream", new OnMessageCallback() {
            @Override public void onMessage(String msg) { }
            @Override public void onMessage(byte[] b, int offset, int length) { }
        });
        dp.socketConnected = () -> true;                         // wsapi keeps the socket open after status 40
        dp.reopenStream = reopened::incrementAndGet;
        dp.onActivationReply(REFUSAL);
        long t0 = System.currentTimeMillis();
        assertFalse(dp.retryRefusedIfDue(t0 + 5_000), "asked again after 5 s");
        assertTrue(dp.retryRefusedIfDue(t0 + DataPlaneInterface.REFUSED_RETRY_MS + 1), "a refused receiver never asked again");
        assertEquals(1, reopened.get());
        assertFalse(dp.retryRefusedIfDue(t0 + DataPlaneInterface.REFUSED_RETRY_MS + 10_000), "asked again before another 60 s");
        assertTrue(dp.retryRefusedIfDue(t0 + 2 * DataPlaneInterface.REFUSED_RETRY_MS + 2));
        dp.onActivationReply(Map.of("status_code", "10"));       // the policy changed: subscribed
        assertFalse(dp.retryRefusedIfDue(t0 + 10 * DataPlaneInterface.REFUSED_RETRY_MS), "an active receiver was reopened");
        assertTrue(dp.awaitActive(100));
        // a sender is never reopened for a refusal
        DataPlaneInterface sender = new DataPlaneInterface("localhost", 8282, "key", "stream", 5000);
        sender.socketConnected = () -> true;
        sender.reopenStream = reopened::incrementAndGet;
        sender.onActivationReply(REFUSAL);
        assertFalse(sender.retryRefusedIfDue(t0 + 10 * DataPlaneInterface.REFUSED_RETRY_MS));
        assertEquals(2, reopened.get());
    }

    @Test
    void aRefusalOnReconnectIsReportedAndTheReceiverIsInactive() throws Exception {
        java.util.List<String> told = new java.util.ArrayList<>();
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "stream", new OnMessageCallback() {
            @Override public void onMessage(String msg) { }
            @Override public void onMessage(byte[] b, int offset, int length) { }
            @Override public void onSubscriptionRefused(String reason) { told.add(reason); }
        });
        dp.onActivationReply(Map.of("status_code", "10"));
        assertTrue(dp.awaitActive(100));
        dp.onActivationReply(REFUSAL);                         // a reconnect, refused
        assertThrows(SubscriptionRefusedException.class, () -> dp.awaitActive(100));
        assertEquals(1, told.size());
        assertFalse(dp.isReceiving(), "a receiver whose re-subscription was refused still reports receiving");
    }
}
