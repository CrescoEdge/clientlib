package crescoclient.conformance;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crescoclient.core.Executor;
import crescoclient.core.GlobalController;
import crescoclient.core.Messaging;
import crescoclient.dataplane.DataPlaneInterface;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Executor submodule tests (parity with pycrescolib's tests/test_executor.py): lock the outgoing wire messages against
 * the executor plugin's contract (executor e5b91a6: config_process with stdin, run/status/end_process, write_stdin with
 * data_b64 / close_stdin and its 10 / 9 / 8 replies), plugin-id resolution, and the dataplane refusing to write stdin.
 */
public class ExecutorTest {

    private static final Gson GSON = new Gson();
    private final StunnelTest.ScriptedMsgEvent ws = new StunnelTest.ScriptedMsgEvent();
    private final Messaging messaging = new Messaging(ws);
    private final Executor executor = new Executor(messaging, new GlobalController(messaging));

    private JsonObject sent(int i) {
        return new JsonParser().parse(ws.sent.get(i)).getAsJsonObject();
    }

    private static String p(JsonObject m, String k) {
        JsonObject pl = m.getAsJsonObject("message_payload");
        return pl.has(k) ? pl.get(k).getAsString() : null;
    }

    private static byte[] written(JsonObject m) {
        String b = p(m, "data_b64");
        return b == null ? new byte[0] : Base64.getDecoder().decode(b);
    }

    private void reply(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        ws.replies.add(GSON.toJson(m));
    }

    private void assertRoute(JsonObject m, String type, String action, String plugin) {
        JsonObject info = m.getAsJsonObject("message_info");
        assertEquals("global_plugin_msgevent", info.get("message_type").getAsString());
        assertEquals(type, info.get("message_event_type").getAsString());
        assertEquals("R", info.get("dst_region").getAsString());
        assertEquals("A", info.get("dst_agent").getAsString());
        assertEquals(plugin, info.get("dst_plugin").getAsString());
        assertEquals("true", info.get("is_rpc").getAsString());
        assertEquals(action, p(m, "action"));
    }

    @Test
    void configProcessAsksForStdinAndReportsIt() {
        reply("config_status", "true", "stdin", "true");
        Map<String, Object> out = executor.config_process("R", "A", "job-1", "cat", true, false, "pe");
        assertEquals(Boolean.TRUE, out.get("config_status"));
        assertEquals(Boolean.TRUE, out.get("stdin"));
        JsonObject m = sent(0);
        assertRoute(m, "CONFIG", "config_process", "pe");
        assertEquals("job-1", p(m, "stream_name"));
        assertEquals("cat", p(m, "command"));
        assertEquals("true", p(m, "stdin"));
        assertEquals("false", p(m, "metrics"));
        reply("config_status", "false", "error_msg", "Runner already exist stream_name: job-1");
        out = executor.config_process("R", "A", "job-1", "cat", false, false, "pe");
        assertEquals(Boolean.FALSE, out.get("config_status"));
        assertTrue(((String) out.get("error_msg")).contains("already exist"));
    }

    @Test
    void runStatusAndEnd() {
        reply("status", "true");
        reply("run_status", "true");
        reply("end_status", "true");
        assertTrue(executor.run_process("R", "A", "job-1", "pe"));
        assertTrue(executor.status_process("R", "A", "job-1", "pe"));
        assertTrue(executor.end_process("R", "A", "job-1", "pe"));
        assertRoute(sent(0), "EXEC", "run_process", "pe");
        assertRoute(sent(1), "CONFIG", "status_process", "pe");
        assertRoute(sent(2), "CONFIG", "end_process", "pe");
        assertFalse(executor.run_process("R", "A", "job-1", "pe"), "no reply is not a start");
    }

    @Test
    void textGetsANewlineAndBytesAreWrittenAsTheyAre() {
        for (int i = 0; i < 3; i++) reply("status", "10", "status_desc", "written");
        assertEquals(6, executor.write_stdin("R", "A", "job-1", "ls -l", true, false, "pe"));
        JsonObject m = sent(0);
        assertRoute(m, "EXEC", "write_stdin", "pe");
        assertEquals("job-1", p(m, "stream_name"));
        assertArrayEquals("ls -l\n".getBytes(StandardCharsets.UTF_8), written(m));
        assertNull(p(m, "close_stdin"));
        assertNull(p(m, "data"), "never the plain text param: bytes as base64");
        byte[] e = "no newline é".getBytes(StandardCharsets.UTF_8);
        assertEquals(e.length, executor.write_stdin("R", "A", "job-1", "no newline é", false, false, "pe"));
        assertArrayEquals(e, written(sent(1)));
        assertEquals(3, executor.write_stdin("R", "A", "job-1", new byte[]{0, (byte) 0xff, '\n'}, true, "pe"));
        assertArrayEquals(new byte[]{0, (byte) 0xff, '\n'}, written(sent(2)));
        assertEquals("true", p(sent(2), "close_stdin"));
    }

    @Test
    void largeInputGoesInOrderedWritesWithinTheExecutorsBound() {
        byte[] data = new byte[2 * Executor.MAX_STDIN_WRITE + 1000];
        for (int i = 0; i < data.length; i++) data[i] = (byte) i;
        for (int i = 0; i < 3; i++) reply("status", "10");
        assertEquals(data.length, executor.write_stdin("R", "A", "job-1", data, true, "pe"));
        assertEquals(3, ws.sent.size());
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        List<Boolean> closes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            byte[] w = written(sent(i));
            assertTrue(w.length <= Executor.MAX_STDIN_WRITE);
            all.writeBytes(w);
            closes.add(p(sent(i), "close_stdin") != null);
        }
        assertArrayEquals(data, all.toByteArray());
        assertEquals(List.of(false, false, true), closes, "stdin closes after the last write only");
    }

    @Test
    void closeStdinSendsNoData() {
        reply("status", "10");
        executor.close_stdin("R", "A", "job-1", "pe");
        assertRoute(sent(0), "EXEC", "write_stdin", "pe");
        assertEquals("true", p(sent(0), "close_stdin"));
        assertNull(p(sent(0), "data_b64"));
        assertEquals(0, executor.write_stdin("R", "A", "job-1", (String) null, true, false, "pe"));
        assertEquals(1, ws.sent.size(), "nothing to write and nothing to close: no message");
    }

    @Test
    void aRefusalThrowsWithTheExecutorsReasonAndWhatWasWritten() {
        for (String refusal : new String[]{"not-owner", "no-stdin", "not-running"}) {
            reply("status", "9", "refusal", refusal, "status_desc", "refused");
            Executor.StdinWriteException e = assertThrows(Executor.StdinWriteException.class,
                    () -> executor.write_stdin("R", "A", "job-1", "x", true, false, "pe"));
            assertEquals(refusal, e.getRefusal());
            assertEquals("9", e.getStatus());
            assertEquals(0, e.getWritten());
            assertTrue(e.getMessage().contains(refusal), e.getMessage());
        }
        ws.sent.clear();
        reply("status", "10");
        reply("status", "9", "refusal", "not-running", "status_desc", "closed");
        Executor.StdinWriteException e = assertThrows(Executor.StdinWriteException.class,
                () -> executor.write_stdin("R", "A", "job-1", new byte[Executor.MAX_STDIN_WRITE + 10], false, "pe"));
        assertEquals(Executor.MAX_STDIN_WRITE, e.getWritten());
        // no reply: an error, not a silent loss
        e = assertThrows(Executor.StdinWriteException.class, () -> executor.write_stdin("R", "A", "job-1", "x", true, false, "pe"));
        assertNull(e.getStatus());
        assertTrue(e.getMessage().contains("no reply"), e.getMessage());
    }

    @Test
    void thePluginIsFoundByName() {
        Map<String, Object> list = new HashMap<>();
        list.put("plugins", List.of(Map.of("pluginname", "io.cresco.example", "name", "plugin-other"),
                Map.of("pluginname", Executor.EXECUTOR_PLUGIN_NAME, "name", "plugin-exec")));
        ws.replies.add(GSON.toJson(Map.of("pluginslist", messaging.setCompressedParam(GSON.toJson(list)))));
        reply("status", "10");
        executor.write_stdin("R", "A", "job-1", "x");
        assertEquals("listplugins", p(sent(0), "action"));
        assertRoute(sent(1), "EXEC", "write_stdin", "plugin-exec");
        list.put("plugins", List.of(Map.of("pluginname", "io.cresco.example", "name", "plugin-other")));
        ws.replies.add(GSON.toJson(Map.of("pluginslist", messaging.setCompressedParam(GSON.toJson(list)))));
        assertThrows(NoSuchElementException.class, () -> executor.write_stdin("R", "A", "job-1", "x"));
    }

    @Test
    void theDataplaneNeverWritesStdin() {
        for (String t : new String[]{"input", "input-0123456789abcdef"}) {
            String cfg = GSON.toJson(Map.of("ident_key", "stream_name", "ident_id", "job-1", "io_type_key", "type", "input_id", t));
            assertTrue(DataPlaneInterface.stdinStreamRefusal(cfg).contains("write_stdin"));
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new DataPlaneInterface("localhost", 1, "sk-test-0123456789abcdef", cfg, (crescoclient.core.OnMessageCallback) null));
            assertTrue(e.getMessage().contains("write_stdin"), e.getMessage());
        }
        for (String ok : new String[]{
                GSON.toJson(Map.of("ident_key", "stream_name", "ident_id", "job-1", "io_type_key", "type", "input_id", "output")),
                GSON.toJson(Map.of("ident_key", "stream_name", "ident_id", "job-1", "io_type_key", "kind", "input_id", "input")),
                "stream_name='job-1'", "job-1"}) {
            assertNull(DataPlaneInterface.stdinStreamRefusal(ok), ok);
            assertNotNull(new DataPlaneInterface("localhost", 1, "sk-test-0123456789abcdef", ok, (crescoclient.core.OnMessageCallback) null));
        }
    }
}
