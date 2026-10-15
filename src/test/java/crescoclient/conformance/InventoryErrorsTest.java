package crescoclient.conformance;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import crescoclient.core.Agents;
import crescoclient.core.CrescoQueryException;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Not loaded" vs "couldn't ask" (mirror of pycrescolib tests/test_inventory_errors.py).
 *
 * <p>An inventory query that fails -- no reply (RPC timeout, e.g. right after a global restart), a wsapi failure
 * reply (status 504), an agent-side status_code 9, or a reply without the list -- must throw, never look like an
 * empty inventory; and deploy_plugin must never add unless the inventory was read and confirmed absent (observed
 * before the fix: listplugins timed out, read as "not loaded", and a second stunnel instance was started).
 */
public class InventoryErrorsTest {

    /** Scripted reply meaning "no reply arrived in time" (MsgEventInterface.recv() returns null). */
    static final String TIMEOUT = "\u0000timeout";

    static class ScriptedMsgEvent extends MsgEventInterface {
        final List<String> sent = new ArrayList<>();
        final Deque<String> replies = new ArrayDeque<>();
        ScriptedMsgEvent() { super("host", 0, "key"); }
        @Override public void send(boolean isRPC, String message) { sent.add(message); }
        @Override public String recv() {
            String r = replies.isEmpty() ? "{}" : replies.poll();
            return TIMEOUT.equals(r) ? null : r;
        }
        @Override public boolean connected() { return true; }
        @Override public String getRegionName() { return "test-region"; }
        @Override public String getAgentName() { return "test-agent"; }
        @Override public String getPluginName() { return "test-plugin"; }
    }

    private static final Gson GSON = new Gson();
    private final ScriptedMsgEvent ws = new ScriptedMsgEvent();
    private final Messaging messaging = new Messaging(ws);
    private final GlobalController gc = new GlobalController(messaging);
    private final Agents agents = new Agents(messaging);
    private final Stunnel stunnel = new Stunnel(messaging, gc, agents);

    /** wsapi's reply when the fabric does not answer in time (ApiSocketWsHandler.failure). */
    private static final String GATEWAY_TIMEOUT = GSON.toJson(Map.of("action", "listplugins", "status", "504",
            "status_desc", "no reply within 30000 ms",
            "error", "no reply within 30000 ms; the operation may still complete"));
    private static final String AGENT_LIST_FAILED = GSON.toJson(Map.of("action", "pluginlist", "status_code", "9",
            "status_desc", "Plugins Could Not Be Listed"));

    private String compressed(String field, Object value) {
        return GSON.toJson(Map.of(field, messaging.setCompressedParam(GSON.toJson(value))));
    }

    private String globalPlugins(String stunnelId) {
        List<Map<String, String>> plugins = new ArrayList<>();
        plugins.add(Map.of("pluginname", "io.cresco.example", "name", "plugin-other"));
        if (stunnelId != null) plugins.add(Map.of("pluginname", Stunnel.STUNNEL_PLUGIN_NAME, "name", stunnelId));
        return compressed("pluginslist", Map.of("plugins", plugins));
    }

    private String agentPlugins(String stunnelId) {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(Map.of("pluginname", "io.cresco.example", "plugin_id", "plugin-other"));
        if (stunnelId != null) rows.add(Map.of("pluginname", Stunnel.STUNNEL_PLUGIN_NAME, "plugin_id", stunnelId));
        Map<String, String> r = new HashMap<>();
        r.put("status_code", "10");
        r.put("plugin_list", messaging.setCompressedParam(GSON.toJson(rows)));
        return GSON.toJson(r);
    }

    private List<String> actions() {
        return ws.sent.stream().map(m -> new JsonParser().parse(m).getAsJsonObject()
                .getAsJsonObject("message_payload").get("action").getAsString()).collect(Collectors.toList());
    }

    // ------------------------------------------------------------ listings throw on failure

    @Test
    void list_plugins_throws_on_every_kind_of_failure() {
        for (String failure : List.of(TIMEOUT, GATEWAY_TIMEOUT, "{}")) {
            ws.replies.add(failure);
            assertThrows(CrescoQueryException.class, () -> gc.list_plugins("R", "A"), failure);
        }
    }

    @Test
    void failure_reason_is_in_the_message() {
        ws.replies.add(GATEWAY_TIMEOUT);
        CrescoQueryException e = assertThrows(CrescoQueryException.class, () -> gc.list_plugins("R", "A"));
        assertTrue(e.getMessage().contains("no reply within 30000 ms") && e.getMessage().contains("status 504"),
                e.getMessage());
        ws.replies.add(TIMEOUT);
        e = assertThrows(CrescoQueryException.class, () -> gc.list_plugins("R", "A"));
        assertTrue(e.getMessage().contains("no reply"), e.getMessage());
    }

    @Test
    void find_plugin_throws_on_failure_and_answers_null_when_absent() {
        ws.replies.add(TIMEOUT);
        ws.replies.add(globalPlugins(null));
        assertThrows(CrescoQueryException.class, () -> gc.find_plugin("R", "A", Stunnel.STUNNEL_PLUGIN_NAME));
        assertNull(gc.find_plugin("R", "A", Stunnel.STUNNEL_PLUGIN_NAME));       // asked: not loaded
    }

    @Test
    void agent_plugin_list_throws_on_failure() {
        for (String failure : List.of(TIMEOUT, AGENT_LIST_FAILED)) {
            ws.replies.add(failure);
            ws.replies.add(failure);
            assertThrows(CrescoQueryException.class, () -> agents.list_plugin_agent("R", "A"));
            assertThrows(CrescoQueryException.class, () -> agents.find_plugin("R", "A", Stunnel.STUNNEL_PLUGIN_NAME));
        }
        ws.replies.add(agentPlugins("plugin-7"));
        assertEquals("plugin-7", agents.find_plugin("R", "A", Stunnel.STUNNEL_PLUGIN_NAME));
    }

    @Test
    void other_inventories_throw_on_failure() {
        List<Consumer<GlobalController>> calls = List.of(g -> g.get_agent_list(null), g -> g.get_region_list(),
                g -> g.get_pipeline_list(), g -> g.get_pipeline_id_by_name("p"));
        for (Consumer<GlobalController> call : calls) {
            ws.replies.add(GATEWAY_TIMEOUT);
            ws.replies.add(TIMEOUT);
            assertThrows(CrescoQueryException.class, () -> call.accept(gc));
            assertThrows(CrescoQueryException.class, () -> call.accept(gc));
        }
    }

    @Test
    void empty_answers_are_still_empty() {
        ws.replies.add(compressed("agentslist", Map.of("agents", List.of())));
        ws.replies.add(compressed("regionslist", Map.of("regions", List.of())));
        ws.replies.add(compressed("pipelineinfo", Map.of("pipelines", List.of())));
        ws.replies.add(compressed("pluginslist", Map.of("plugins", List.of())));
        assertTrue(gc.get_agent_list(null).get("agents").isEmpty());
        assertTrue(gc.get_region_list().get("regions").isEmpty());
        assertNull(gc.get_pipeline_id_by_name("p"));
        assertTrue(gc.list_plugins("R", "A").isEmpty());
    }

    // ------------------------------------------------------------ deploy_plugin never blind-adds

    @Test
    void deploy_plugin_does_not_add_when_global_inventory_fails() {
        for (String failure : List.of(TIMEOUT, GATEWAY_TIMEOUT, "{}")) {
            ws.sent.clear();
            ws.replies.add(failure);
            assertThrows(CrescoQueryException.class, () -> stunnel.deploy_plugin("R", "A"));
            assertEquals(List.of("listplugins"), actions());
        }
    }

    @Test
    void deploy_plugin_does_not_add_when_agent_confirmation_fails() {
        for (String failure : List.of(TIMEOUT, AGENT_LIST_FAILED)) {
            ws.sent.clear();
            ws.replies.add(globalPlugins(null));
            ws.replies.add(failure);
            assertThrows(CrescoQueryException.class, () -> stunnel.deploy_plugin("R", "A", "unused.jar"));
            assertEquals(List.of("listplugins", "pluginlist"), actions());
        }
    }

    @Test
    void deploy_plugin_uses_agent_copy_the_global_does_not_list_yet() {
        ws.replies.add(globalPlugins(null));
        ws.replies.add(agentPlugins("plugin-running"));
        assertEquals("plugin-running", stunnel.deploy_plugin("R", "A"));
        assertEquals(List.of("listplugins", "pluginlist"), actions());
    }

    @Test
    void deploy_plugin_retry_after_failure_adds_once() {
        ws.replies.add(TIMEOUT);
        ws.replies.add(globalPlugins(null));
        ws.replies.add(agentPlugins(null));
        ws.replies.add(GSON.toJson(Map.of("status_code", "10", "pluginid", "p1")));
        ws.replies.add(globalPlugins("p1"));
        assertThrows(CrescoQueryException.class, () -> stunnel.deploy_plugin("R", "A"));
        assertEquals("p1", stunnel.deploy_plugin("R", "A"));
        assertEquals("p1", stunnel.deploy_plugin("R", "A"));
        assertEquals(1, actions().stream().filter("pluginadd"::equals).count());
    }

    // ------------------------------------------------------------ stunnel lookups

    @Test
    void stunnel_lookup_failure_throws_but_absent_plugin_is_empty() {
        ws.replies.add(GATEWAY_TIMEOUT);
        ws.replies.add(globalPlugins(null));
        assertThrows(CrescoQueryException.class, () -> stunnel.get_tunnel_list("R", "A"));
        assertTrue(stunnel.get_tunnel_list("R", "A").isEmpty());          // asked: stunnel not loaded
        ws.replies.add(TIMEOUT);
        assertThrows(CrescoQueryException.class, () -> stunnel.get_tunnel_status("R", "A", "t1"));
        ws.replies.add(globalPlugins("ps"));
        ws.replies.add(TIMEOUT);
        assertThrows(CrescoQueryException.class, () -> stunnel.create_tunnel("t1", "R", "A", "2222", "R2", "B",
                "localhost", "22", null));
        assertTrue(!actions().contains("configsrctunnel"));
    }
}
