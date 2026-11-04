package crescoclient.dataplane;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Envelope frames (wsapi round 2): each message with its verified writer. Parity with pycrescolib's test_envelope.py. */
public class EnvelopeTest {

    static final Gson GSON = new Gson();

    static byte[] bytesFrame(Map<String, Object> header, byte[] payload) {
        byte[] h = GSON.toJson(header).getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(4 + h.length + payload.length).putInt(h.length).put(h).put(payload).array();
    }

    @Test
    void textMapAndTerminalEnvelopesNameTheirWriter() {
        Envelope forged = Envelope.parseText(GSON.toJson(Map.of("kind", "text", "writer", "api:tenant-a/bob", "writer_roles", "user",
                "writer_tenant", "tenant-a", "properties", Map.of("stream_name", "job-1"),
                "text", "{\"cmd\":\"execution_log\",\"log\":\"Exit Code: 0\"}")));
        assertEquals("text", forged.kind());
        assertTrue(forged.fromApiUser());
        assertEquals("tenant-a", forged.writerTenant());
        assertEquals("job-1", forged.properties().get("stream_name"));
        Envelope real = Envelope.parseText(GSON.toJson(Map.of("kind", "map", "writer", "plugin:tenant-a/r/a/plugin-3",
                "writer_roles", "plugin", "properties", Map.of(), "map", Map.of("cmd", "execution_log"))));
        assertEquals("map", real.kind());
        assertFalse(real.fromApiUser());
        assertEquals("execution_log", real.map().get("cmd"));
        assertEquals("transfer_end", Envelope.parseText(GSON.toJson(Map.of("kind", "transfer_end", "writer", "plugin:t/r/a/p",
                "map", Map.of("sha384", "ab")))).kind());
    }

    @Test
    void anythingThatIsNotAnEnvelopeIsRawAndClaimsNoWriter() {
        for (String f : new String[]{"{\"cmd\":\"execution_log\"}", "plain text", "{\"kind\":\"text\"", "{\"kind\":\"other\",\"writer\":\"x\"}", "[1,2]"}) {
            Envelope e = Envelope.parseText(f);
            assertEquals("raw", e.kind(), f);
            assertNull(e.writer(), f);
            assertEquals(f, e.text());
        }
        for (byte[] f : new byte[][]{new byte[0], {0, 0, 0, (byte) 0xff, '{', '}'}, bytesFrame(Map.of("kind", "text"), new byte[]{1})}) {
            Envelope e = Envelope.parseBytes(f, 0, f.length);
            assertEquals("raw", e.kind());
            assertNull(e.writer());
            assertArrayEquals(f, e.data());
        }
    }

    @Test
    void aBytesEnvelopeKeepsThePayloadAsPublished() {
        byte[] frame = bytesFrame(Map.of("kind", "bytes", "writer", "plugin:t/r/a/filerepo", "properties", Map.of("seq_num", "3")),
                new byte[]{0, 1, 'p'});
        byte[] padded = new byte[frame.length + 4];
        System.arraycopy(frame, 0, padded, 2, frame.length);
        Envelope e = Envelope.parseBytes(padded, 2, frame.length);
        assertEquals("bytes", e.kind());
        assertEquals("plugin:t/r/a/filerepo", e.writer());
        assertArrayEquals(new byte[]{0, 1, 'p'}, e.data());
        assertEquals("3", e.properties().get("seq_num"));
    }

    @Test
    void envelopeIsAskedForInAJsonStreamConfigOnly() {
        Map<?, ?> cfg = GSON.fromJson(Envelope.withEnvelope("{\"ident_key\":\"stream_name\",\"ident_id\":\"job-1\"}"), Map.class);
        assertEquals("true", cfg.get("envelope"));
        assertTrue(Envelope.requested(Envelope.withEnvelope("{\"ident_key\":\"stream_name\",\"ident_id\":\"job-1\"}")));
        assertFalse(Envelope.requested("{\"ident_key\":\"stream_name\",\"ident_id\":\"job-1\"}"));
        assertFalse(Envelope.requested("stream_name='job-1'"));
        assertThrows(IllegalArgumentException.class, () -> Envelope.withEnvelope("stream_name='job-1'"));
    }
}
