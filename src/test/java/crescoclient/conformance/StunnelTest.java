package crescoclient.conformance;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import crescoclient.core.Agents;
import crescoclient.core.GlobalController;
import crescoclient.core.Messaging;
import crescoclient.core.Stunnel;
import crescoclient.msgevent.MsgEventInterface;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stunnel submodule tests: lock the outgoing wire messages against the stunnel plugin's real
 * PluginExecutor contract (action names, MsgEvent type, action_* params, routing) and the reply decode
 * path (compressed stunnel_config, tunnels list, status/config/health/metrics), including plugin-id
 * auto-resolution through the global controller.
 */
public class StunnelTest {

    /** Scripted transport: records every outgoing message, replies from a queue (default "{}"). */
    static class ScriptedMsgEvent extends MsgEventInterface {
        final List<String> sent = new ArrayList<>();
        final Deque<String> replies = new ArrayDeque<>();
        ScriptedMsgEvent() { super("host", 0, "key"); }
        @Override public void send(boolean isRPC, String message) { sent.add(message); }
        @Override public String recv() { return replies.isEmpty() ? "{}" : replies.poll(); }
        @Override public boolean connected() { return true; }
        @Override public String getRegionName() { return "test-region"; }
        @Override public String getAgentName() { return "test-agent"; }
        @Override public String getPluginName() { return "test-plugin"; }
    }

    private static final Gson GSON = new Gson();

    private final ScriptedMsgEvent ws = new ScriptedMsgEvent();
    private final Messaging messaging = new Messaging(ws);
    private final Stunnel stunnel = new Stunnel(messaging, new GlobalController(messaging), new Agents(messaging));

    private JsonObject sent(int i) {
        return new JsonParser().parse(ws.sent.get(i)).getAsJsonObject();
    }

    private static String info(JsonObject m, String k) {
        return m.getAsJsonObject("message_info").get(k).getAsString();
    }

    private static String payload(JsonObject m, String k) {
        return m.getAsJsonObject("message_payload").has(k)
                ? m.getAsJsonObject("message_payload").get(k).getAsString() : null;
    }

    private String reply(Map<String, String> m) {
        return GSON.toJson(m);
    }

    /** Global-controller listplugins reply naming the stunnel plugin on an agent. */
    private String pluginsReply(String pluginId) {
        Map<String, Object> list = new HashMap<>();
        List<Map<String, String>> plugins = new ArrayList<>();
        plugins.add(Map.of("pluginname", "io.cresco.example", "name", "plugin-other"));
        if (pluginId != null) plugins.add(Map.of("pluginname", Stunnel.STUNNEL_PLUGIN_NAME, "name", pluginId));
        list.put("plugins", plugins);
        return reply(Map.of("pluginslist", messaging.setCompressedParam(GSON.toJson(list))));
    }

    /** The agent's own pluginlist reply (rows carry plugin_id / pluginname). */
    private String agentPluginsReply(String pluginId) {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("pluginname", "io.cresco.example", "plugin_id", "plugin-other"));
        if (pluginId != null) rows.add(Map.of("pluginname", Stunnel.STUNNEL_PLUGIN_NAME, "plugin_id", pluginId));
        return reply(Map.of("status_code", "10", "plugin_list", messaging.setCompressedParam(GSON.toJson(rows))));
    }

    private void assertPluginRoute(JsonObject m, String type, String action, String region, String agent, String plugin) {
        assertEquals("global_plugin_msgevent", info(m, "message_type"));
        assertEquals(type, info(m, "message_event_type"));
        assertEquals("true", info(m, "is_rpc"));
        assertEquals(region, info(m, "dst_region"));
        assertEquals(agent, info(m, "dst_agent"));
        assertEquals(plugin, info(m, "dst_plugin"));
        assertEquals(action, payload(m, "action"));
    }

    @Test
    void create_tunnel_sends_configsrctunnel_and_decodes_stunnel_config() {
        Map<String, String> cfg = Map.of("stunnel_id", "t1", "src_port", "5202", "dst_port", "5201");
        ws.replies.add(reply(Map.of("status", "10", "stunnel_id", "t1",
                "stunnel_config", messaging.setCompressedParam(GSON.toJson(cfg)))));

        Map<String, String> out = stunnel.create_tunnel("t1", "R1", "A1", "5202", "R2", "A2", "localhost", "5201",
                "65536", "P-SRC", "P-DST", "15000", "5000");

        assertEquals(cfg, out);
        assertEquals(1, ws.sent.size());
        JsonObject m = sent(0);
        assertPluginRoute(m, "CONFIG", "configsrctunnel", "R1", "A1", "P-SRC");
        assertEquals("t1", payload(m, "action_stunnel_id"));
        assertEquals("5202", payload(m, "action_src_port"));
        assertEquals("localhost", payload(m, "action_dst_host"));
        assertEquals("5201", payload(m, "action_dst_port"));
        assertEquals("R2", payload(m, "action_dst_region"));
        assertEquals("A2", payload(m, "action_dst_agent"));
        assertEquals("P-DST", payload(m, "action_dst_plugin"));
        assertEquals("65536", payload(m, "action_buffer_size"));
        assertEquals("15000", payload(m, "action_watchdog_timeout"));
        assertEquals("5000", payload(m, "action_performance_report_rate"));
    }

    @Test
    void create_tunnel_omits_unset_optionals_and_returns_failure_reply() {
        ws.replies.add(reply(Map.of("status", "9", "status_desc", "Failed to create SRC tunnel")));
        Map<String, String> out = stunnel.create_tunnel("t1", "R1", "A1", "5202", "R2", "A2", "localhost", "5201",
                null, "P-SRC", "P-DST");
        assertEquals("9", out.get("status"));
        JsonObject m = sent(0);
        assertNull(payload(m, "action_buffer_size"));
        assertNull(payload(m, "action_watchdog_timeout"));
        assertNull(payload(m, "action_performance_report_rate"));
    }

    @Test
    void create_tunnel_generates_id_when_missing() {
        stunnel.create_tunnel(null, "R1", "A1", "5202", "R2", "A2", "localhost", "5201", "8192", "P-SRC", "P-DST");
        String id = payload(sent(0), "action_stunnel_id");
        assertNotNull(id);
        assertEquals(36, id.length());
    }

    @Test
    void create_tunnel_auto_resolves_both_plugin_ids() {
        ws.replies.add(pluginsReply("plugin-src"));
        ws.replies.add(pluginsReply("plugin-dst"));
        ws.replies.add(reply(Map.of("status", "10")));

        stunnel.create_tunnel("t1", "R1", "A1", "5202", "R2", "A2", "localhost", "5201", "8192");

        assertEquals(3, ws.sent.size());
        JsonObject lookupSrc = sent(0);
        assertEquals("global_controller_msgevent", info(lookupSrc, "message_type"));
        assertEquals("listplugins", payload(lookupSrc, "action"));
        assertEquals("R1", payload(lookupSrc, "action_region"));
        assertEquals("A1", payload(lookupSrc, "action_agent"));
        assertEquals("R2", payload(sent(1), "action_region"));
        JsonObject create = sent(2);
        assertPluginRoute(create, "CONFIG", "configsrctunnel", "R1", "A1", "plugin-src");
        assertEquals("plugin-dst", payload(create, "action_dst_plugin"));
    }

    @Test
    void create_tunnel_returns_null_without_sending_when_plugin_missing() {
        ws.replies.add(pluginsReply(null));
        ws.replies.add(pluginsReply("plugin-dst"));
        assertNull(stunnel.create_tunnel("t1", "R1", "A1", "5202", "R2", "A2", "localhost", "5201", "8192"));
        assertEquals(2, ws.sent.size());   // only the two lookups, no configsrctunnel
    }

    @Test
    void get_tunnel_list_decodes_tunnels() {
        ws.replies.add(reply(Map.of("status", "10",
                "tunnels", GSON.toJson(List.of(Map.of("stunnel_id", "t1", "status", "ACTIVE"))))));
        List<Map<String, String>> out = stunnel.get_tunnel_list("R", "A", "P");
        assertEquals(1, out.size());
        assertEquals("t1", out.get(0).get("stunnel_id"));
        assertEquals("ACTIVE", out.get(0).get("status"));
        assertPluginRoute(sent(0), "EXEC", "listtunnels", "R", "A", "P");
    }

    @Test
    void get_tunnel_list_is_empty_when_plugin_missing() {
        ws.replies.add(pluginsReply(null));
        assertTrue(stunnel.get_tunnel_list("R", "A").isEmpty());
        assertEquals(1, ws.sent.size());
    }

    @Test
    void get_tunnel_status_reads_tunnel_status() {
        ws.replies.add(reply(Map.of("status", "10", "stunnel_id", "t1", "tunnel_status", "RECOVERING")));
        assertEquals("RECOVERING", stunnel.get_tunnel_status("R", "A", "t1", "P"));
        JsonObject m = sent(0);
        assertPluginRoute(m, "EXEC", "gettunnelstatus", "R", "A", "P");
        assertEquals("t1", payload(m, "action_stunnel_id"));
    }

    @Test
    void get_tunnel_status_null_when_not_found() {
        ws.replies.add(reply(Map.of("status", "9", "status_desc", "Tunnel config not found for t1")));
        assertNull(stunnel.get_tunnel_status("R", "A", "t1", "P"));
    }

    @Test
    void get_tunnel_config_decodes_json() {
        ws.replies.add(reply(Map.of("status", "10",
                "tunnel_config", GSON.toJson(Map.of("stunnel_id", "t1", "dst_port", "5201")))));
        Map<String, String> out = stunnel.get_tunnel_config("R", "A", "t1", "P");
        assertEquals("5201", out.get("dst_port"));
        assertPluginRoute(sent(0), "EXEC", "gettunnelconfig", "R", "A", "P");
    }

    @Test
    void tunnel_health_check_maps_status() {
        ws.replies.add(reply(Map.of("status", "10")));
        ws.replies.add(reply(Map.of("status", "9")));
        assertTrue(stunnel.tunnel_health_check("R", "A", "t1", "P"));
        assertFalse(stunnel.tunnel_health_check("R", "A", "t1", "P"));
        assertPluginRoute(sent(0), "EXEC", "tunnelhealthcheck", "R", "A", "P");
        assertEquals("t1", payload(sent(0), "action_stunnel_id"));
    }

    @Test
    void get_metrics_parses_json() {
        ws.replies.add(reply(Map.of("status", "10", "metrics", "{\"tunnels\":{\"t1\":{\"bytes\":5}}}")));
        Map<String, Object> out = stunnel.get_metrics("R", "A", "P");
        assertTrue(out.containsKey("tunnels"));
        assertPluginRoute(sent(0), "EXEC", "getmetrics", "R", "A", "P");
        assertNull(payload(sent(0), "action_stunnel_id"));
    }

    @Test
    void set_net_tuning_sends_keys_as_params() {
        ws.replies.add(reply(Map.of("status", "10")));
        Map<String, String> out = stunnel.set_net_tuning("R", "A", Map.of("net_socket_buffer_bytes", "4194304"), "P");
        assertEquals("10", out.get("status"));
        JsonObject m = sent(0);
        assertPluginRoute(m, "CONFIG", "nettuning", "R", "A", "P");
        assertEquals("4194304", payload(m, "net_socket_buffer_bytes"));
    }

    @Test
    void remove_tunnel_removes_src_then_dst() {
        ws.replies.add(reply(Map.of("status", "10")));
        ws.replies.add(reply(Map.of("status", "10")));
        Map<String, Object> out = stunnel.remove_tunnel("t1", "R1", "A1", "R2", "A2", "P-SRC", "P-DST");
        assertEquals(Boolean.TRUE, out.get("fully_removed"));
        assertPluginRoute(sent(0), "CONFIG", "removesrctunnel", "R1", "A1", "P-SRC");
        assertPluginRoute(sent(1), "CONFIG", "removedsttunnel", "R2", "A2", "P-DST");
        assertEquals("t1", payload(sent(0), "action_stunnel_id"));
        assertEquals("t1", payload(sent(1), "action_stunnel_id"));
    }

    @Test
    void remove_src_tunnel_three_arg_resolves_plugin() {
        ws.replies.add(pluginsReply("plugin-src"));
        ws.replies.add(reply(Map.of("status", "10")));
        assertEquals("10", stunnel.remove_src_tunnel("R", "A", "t1").get("status"));
        assertPluginRoute(sent(1), "CONFIG", "removesrctunnel", "R", "A", "plugin-src");
    }

    @Test
    void deploy_plugin_is_noop_when_loaded() {
        ws.replies.add(pluginsReply("plugin-src"));
        assertEquals("plugin-src", stunnel.deploy_plugin("R", "A"));
        assertEquals(1, ws.sent.size());
    }

    @Test
    void deploy_plugin_adds_embedded_jar_when_missing() {
        ws.replies.add(pluginsReply(null));
        ws.replies.add(agentPluginsReply(null));
        ws.replies.add(reply(Map.of("status_code", "10", "pluginid", "plugin-new")));
        assertEquals("plugin-new", stunnel.deploy_plugin("R", "A"));
        JsonObject confirm = sent(1);     // "not loaded" confirmed with the agent before adding
        assertEquals("global_agent_msgevent", info(confirm, "message_type"));
        assertEquals("pluginlist", payload(confirm, "action"));
        JsonObject add = sent(2);
        assertEquals("global_agent_msgevent", info(add, "message_type"));
        assertEquals("CONFIG", info(add, "message_event_type"));
        assertEquals("R", info(add, "dst_region"));
        assertEquals("A", info(add, "dst_agent"));
        assertEquals("pluginadd", payload(add, "action"));
        Map<String, String> configparams = messaging.getMapFromString(
                messaging.getCompressedParam(payload(add, "configparams")));
        assertEquals(Stunnel.STUNNEL_PLUGIN_NAME, configparams.get("pluginname"));
        assertEquals(Stunnel.STUNNEL_EMBEDDED_JAR, configparams.get("jarfile"));
    }
}
