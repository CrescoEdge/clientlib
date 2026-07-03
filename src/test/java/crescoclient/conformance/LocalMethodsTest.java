package crescoclient.conformance;

import crescoclient.dataplane.DataPlaneInterface;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Local (non-wire) method tests (mirror pycrescolib tests/test_local_methods.py).
 *
 * Some public methods return client-side state rather than emitting a wire message, so they are not
 * in the conformance corpus; they are covered here.
 */
public class LocalMethodsTest {

    private static final Set<String> METRIC_KEYS = Set.of(
            "stream_name", "messages_received", "messages_sent", "bytes_received", "bytes_sent", "active");

    @Test
    void dataplane_get_metrics_keys() {
        DataPlaneInterface dp = new DataPlaneInterface("localhost", 8282, "key", "my-stream", 5);
        Map<String, Object> m = dp.get_metrics();
        assertEquals(METRIC_KEYS, m.keySet());
        assertEquals("my-stream", m.get("stream_name"));
        assertEquals(0L, m.get("messages_sent"));
        assertEquals(0L, m.get("bytes_sent"));
        assertEquals(0, m.get("messages_received"));
        assertEquals(0L, m.get("bytes_received"));
        assertEquals(false, m.get("active"));
    }
}
