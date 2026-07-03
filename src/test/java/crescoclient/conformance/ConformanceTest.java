package crescoclient.conformance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import crescoclient.core.Admin;
import crescoclient.core.API;
import crescoclient.core.Agents;
import crescoclient.core.GlobalController;
import crescoclient.core.Messaging;
import crescoclient.msgevent.MsgEventInterface;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Cross-language conformance / parity test.
 *
 * Each invocation is run against a capturing mock transport; the normalized outgoing message must
 * equal the shared golden corpus (src/test/resources/conformance/golden_messages.json), which is
 * generated from the Python client and copied here verbatim. A green run on both suites means the
 * Java and Python clients emit identical wire messages for identical calls.
 *
 * Normalization is content-based: gzip+base64 payload fields are decompressed and re-parsed (the
 * two languages' gzip bytes differ), and JsonObject.equals is order-independent.
 */
public class ConformanceTest {

    /** Fake transport: records the last outgoing JSON message, returns a canned reply. */
    static class CaptureMsgEvent extends MsgEventInterface {
        String sent;
        final String reply;
        CaptureMsgEvent(String reply) { super("host", 0, "key"); this.reply = reply; }
        @Override public void send(boolean isRPC, String message) { this.sent = message; }
        @Override public String recv() { return reply; }
        @Override public boolean connected() { return true; }
        @Override public String getRegionName() { return "test-region"; }
        @Override public String getAgentName() { return "test-agent"; }
        @Override public String getPluginName() { return "test-plugin"; }
    }

    /** The submodule handles wired to one capturing transport. */
    static class Clients {
        final CaptureMsgEvent ws = new CaptureMsgEvent("{}");
        final Messaging messaging = new Messaging(ws);
        final Admin admin = new Admin(messaging);
        final API api = new API(ws, messaging);
        final Agents agents = new Agents(messaging);
        final GlobalController gc = new GlobalController(messaging);
    }

    // ---- helpers ----------------------------------------------------------

    /** Absolute path to the shared fixture jar (byte-identical to the Python repo's copy). */
    static final String JAR_PATH;
    static {
        try {
            JAR_PATH = new java.io.File(
                    ConformanceTest.class.getResource("/conformance/example-plugin.jar").toURI()).getAbsolutePath();
        } catch (Exception e) {
            throw new RuntimeException("fixture jar /conformance/example-plugin.jar not on test classpath", e);
        }
    }

    private static Map<String, Object> payload(String action) {
        Map<String, Object> m = new HashMap<>();
        m.put("action", action);
        return m;
    }

    private static Map<String, String> smap(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    /** Decompress a gzip+base64 field and re-parse; return null if it is not a compressed value. */
    private static JsonElement tryDecompress(Messaging messaging, String s) {
        try {
            String decompressed = messaging.getCompressedParam(s);
            if (decompressed == null) return null;
            return new JsonParser().parse(decompressed);
        } catch (Exception e) {
            return null;
        }
    }

    /** Parse a captured outgoing message and canonicalize its compressed payload fields. */
    private static JsonObject normalize(String rawJson, Messaging messaging) {
        JsonObject msg = new JsonParser().parse(rawJson).getAsJsonObject();
        JsonObject payload = msg.getAsJsonObject("message_payload");
        JsonObject normPayload = new JsonObject();
        for (Map.Entry<String, JsonElement> e : payload.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) {
                String sv = v.getAsString();
                if (sv.equals(JAR_PATH)) {
                    normPayload.add(e.getKey(), new JsonPrimitive("<JAR_PATH>"));
                } else {
                    JsonElement parsed = tryDecompress(messaging, sv);
                    normPayload.add(e.getKey(), parsed != null ? parsed : v);
                }
            } else {
                normPayload.add(e.getKey(), v);
            }
        }
        JsonObject out = new JsonObject();
        out.add("message_info", msg.getAsJsonObject("message_info"));
        out.add("message_payload", normPayload);
        return out;
    }

    private static JsonArray loadGolden() {
        try (InputStream is = ConformanceTest.class.getResourceAsStream("/conformance/golden_messages.json")) {
            assertTrue(is != null, "golden_messages.json not found on the test classpath");
            return new JsonParser().parse(new InputStreamReader(is, StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- the canonical invocations (mirror pycrescolib tests/conformance_util.py CASES) ----

    private static Map<String, Consumer<Clients>> invocations() {
        Map<String, Consumer<Clients>> inv = new LinkedHashMap<>();

        // messaging routing variants
        inv.put("messaging.global_controller_msgevent", c -> c.messaging.global_controller_msgevent(true, "EXEC", payload("x")));
        inv.put("messaging.regional_controller_msgevent", c -> c.messaging.regional_controller_msgevent(true, "EXEC", payload("x")));
        inv.put("messaging.global_agent_msgevent", c -> c.messaging.global_agent_msgevent(true, "EXEC", payload("x"), "R", "A"));
        inv.put("messaging.regional_agent_msgevent", c -> c.messaging.regional_agent_msgevent(true, "EXEC", payload("x"), "A"));
        inv.put("messaging.agent_msgevent", c -> c.messaging.agent_msgevent(true, "EXEC", payload("x")));
        inv.put("messaging.global_plugin_msgevent", c -> c.messaging.global_plugin_msgevent(true, "EXEC", payload("x"), "R", "A", "P"));
        inv.put("messaging.regional_plugin_msgevent", c -> c.messaging.regional_plugin_msgevent(true, "EXEC", payload("x"), "A", "P"));
        inv.put("messaging.plugin_msgevent", c -> c.messaging.plugin_msgevent(true, "EXEC", payload("x"), "P"));

        // admin
        inv.put("admin.stopcontroller", c -> c.admin.stopcontroller("R", "A"));
        inv.put("admin.restartcontroller", c -> c.admin.restartcontroller("R", "A"));
        inv.put("admin.restartframework", c -> c.admin.restartframework("R", "A"));
        inv.put("admin.killjvm", c -> c.admin.killjvm("R", "A"));

        // api
        inv.put("api.get_global_info", c -> c.api.get_global_info());

        // agents (non-file)
        inv.put("agents.is_controller_active", c -> c.agents.is_controller_active("R", "A"));
        inv.put("agents.get_controller_status", c -> c.agents.get_controller_status("R", "A"));
        inv.put("agents.add_plugin_agent", c -> c.agents.add_plugin_agent("R", "A", smap("pluginname", "io.cresco.example"), null));
        inv.put("agents.add_plugin_agent_edges", c -> c.agents.add_plugin_agent("R", "A", smap("pluginname", "io.cresco.example"), smap("edge", "1")));
        inv.put("agents.remove_plugin_agent", c -> c.agents.remove_plugin_agent("R", "A", "PID"));
        inv.put("agents.list_plugin_agent", c -> c.agents.list_plugin_agent("R", "A"));
        inv.put("agents.status_plugin_agent", c -> c.agents.status_plugin_agent("R", "A", "PID"));
        inv.put("agents.get_agent_info", c -> c.agents.get_agent_info("R", "A"));
        inv.put("agents.get_agent_log", c -> c.agents.get_agent_log("R", "A"));
        inv.put("agents.get_broadcast_discovery", c -> c.agents.get_broadcast_discovery("R", "A"));
        inv.put("agents.cepadd", c -> c.agents.cepadd("istream", "idesc", "ostream", "odesc", "select * from istream", "R", "A"));

        // globalcontroller (non-file)
        inv.put("globalcontroller.submit_pipeline", c -> {
            Map<String, Object> cadl = new HashMap<>();
            cadl.put("pipeline_name", "p");
            cadl.put("nodes", new ArrayList<>());
            cadl.put("edges", new ArrayList<>());
            c.gc.submit_pipeline(cadl, "0");
        });
        inv.put("globalcontroller.remove_pipeline", c -> c.gc.remove_pipeline("PID"));
        inv.put("globalcontroller.get_pipeline_list", c -> c.gc.get_pipeline_list());
        inv.put("globalcontroller.get_pipeline_info", c -> c.gc.get_pipeline_info("PID"));
        inv.put("globalcontroller.get_pipeline_export", c -> c.gc.get_pipeline_export("PID"));
        inv.put("globalcontroller.get_pipeline_is_assignment_info", c -> c.gc.get_pipeline_is_assignment_info("INODE", "RES"));
        inv.put("globalcontroller.get_agent_list", c -> c.gc.get_agent_list(null));
        inv.put("globalcontroller.get_agent_list_region", c -> c.gc.get_agent_list("R"));
        inv.put("globalcontroller.get_agent_resources", c -> c.gc.get_agent_resources("R", "A"));
        inv.put("globalcontroller.get_region_resources", c -> c.gc.get_region_resources("R"));
        inv.put("globalcontroller.get_region_list", c -> c.gc.get_region_list());
        inv.put("globalcontroller.get_plugin_repo_list", c -> c.gc.get_plugin_repo_list());
        inv.put("globalcontroller.get_repo_plugins", c -> c.gc.get_repo_plugins());

        // B-2 unified metrics + capability catalog (Java arg order differs from Python; same wire)
        inv.put("globalcontroller.get_metric_inventory", c -> c.gc.get_metric_inventory(null, null, "global", true, true));
        inv.put("globalcontroller.get_metric_inventory_node", c -> c.gc.get_metric_inventory("R", "A", "node", true, false));
        inv.put("globalcontroller.get_capability_inventory", c -> c.gc.get_capability_inventory("global", true, false));

        // plugin-jar (file) methods, driven by the shared fixture jar
        inv.put("agents.repo_pull_plugin_agent", c -> c.agents.repo_pull_plugin_agent("R", "A", JAR_PATH));
        inv.put("agents.upload_plugin_agent", c -> c.agents.upload_plugin_agent("R", "A", JAR_PATH));
        inv.put("agents.update_plugin_agent", c -> c.agents.update_plugin_agent("R", "A", JAR_PATH));
        inv.put("globalcontroller.upload_plugin_global", c -> c.gc.upload_plugin_global(JAR_PATH));

        return inv;
    }

    @TestFactory
    List<DynamicTest> wire_messages_match_golden() {
        JsonArray golden = loadGolden();
        Map<String, JsonObject> byName = new HashMap<>();
        for (JsonElement e : golden) {
            JsonObject o = e.getAsJsonObject();
            byName.put(o.get("name").getAsString(), o);
        }
        Map<String, Consumer<Clients>> inv = invocations();

        // no silent gaps: golden and invocations must cover exactly the same calls
        assertEquals(byName.keySet(), inv.keySet(),
                "golden corpus and Java invocations cover different calls");

        return inv.entrySet().stream().map(entry -> dynamicTest(entry.getKey(), () -> {
            Clients c = new Clients();
            entry.getValue().accept(c);
            assertTrue(c.ws.sent != null, entry.getKey() + ": no message was sent");

            JsonObject actual = normalize(c.ws.sent, c.messaging);
            JsonObject expected = byName.get(entry.getKey());

            assertEquals(expected.getAsJsonObject("message_info"), actual.getAsJsonObject("message_info"),
                    entry.getKey() + " message_info mismatch");
            assertEquals(expected.getAsJsonObject("message_payload"), actual.getAsJsonObject("message_payload"),
                    entry.getKey() + " message_payload mismatch");
        })).collect(Collectors.toList());
    }
}
