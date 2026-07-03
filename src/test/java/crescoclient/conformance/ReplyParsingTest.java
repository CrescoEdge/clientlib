package crescoclient.conformance;

import com.google.gson.Gson;
import crescoclient.core.API;
import crescoclient.core.Agents;
import crescoclient.core.GlobalController;
import crescoclient.core.Messaging;
import crescoclient.msgevent.MsgEventInterface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reply-parsing tests for the Java client (mirror pycrescolib tests/test_reply_parsing.py).
 *
 * The conformance suite locks the outgoing message; these lock the incoming decode path: given a
 * canned reply, each method must return the correctly-decoded value (decompress + parse).
 */
public class ReplyParsingTest {

    /** Fake transport returning a fixed reply for every RPC. */
    static class ReplyMsgEvent extends MsgEventInterface {
        final String reply;
        ReplyMsgEvent(String reply) { super("host", 0, "key"); this.reply = reply; }
        @Override public void send(boolean isRPC, String message) { }
        @Override public String recv() { return reply; }
        @Override public boolean connected() { return true; }
        @Override public String getRegionName() { return "test-region"; }
        @Override public String getAgentName() { return "test-agent"; }
        @Override public String getPluginName() { return "test-plugin"; }
    }

    private static final Gson GSON = new Gson();
    /** Compression helper (uses the client's own setCompressedParam so decode round-trips). */
    private static final Messaging COMPRESSOR = new Messaging(new ReplyMsgEvent("{}"));

    private static String compress(Object obj) {
        return COMPRESSOR.setCompressedParam(GSON.toJson(obj));
    }

    private static GlobalController gcWithReply(String reply) {
        return new GlobalController(new Messaging(new ReplyMsgEvent(reply)));
    }

    private static Agents agentsWithReply(String reply) {
        return new Agents(new Messaging(new ReplyMsgEvent(reply)));
    }

    @Test
    void get_pipeline_list_decodes_compressed_pipelines() {
        String reply = "{\"pipelineinfo\":\"" + compress(Map.of("pipelines",
                List.of(Map.of("pipeline_id", "1", "pipeline_name", "p")))) + "\"}";
        List<Map<String, String>> out = gcWithReply(reply).get_pipeline_list();
        assertEquals(1, out.size());
        assertEquals("1", out.get(0).get("pipeline_id"));
        assertEquals("p", out.get(0).get("pipeline_name"));
    }

    @Test
    void get_pipeline_status_reads_status_code() {
        String reply = "{\"gpipeline\":\"" + compress(Map.of("status_code", "10")) + "\"}";
        assertEquals(10, gcWithReply(reply).get_pipeline_status("PID"));
    }

    @Test
    void get_agent_list_decodes_compressed_agents() {
        String reply = "{\"agentslist\":\"" + compress(Map.of("agents",
                List.of(Map.of("agent_id", "a1")))) + "\"}";
        Map<String, List<Map<String, String>>> out = gcWithReply(reply).get_agent_list(null);
        assertEquals("a1", out.get("agents").get(0).get("agent_id"));
    }

    @Test
    void list_plugin_agent_decodes_compressed_list() {
        String reply = "{\"plugin_list\":\"" + compress(List.of(Map.of("name", "plug"))) + "\"}";
        List<Map<String, String>> out = agentsWithReply(reply).list_plugin_agent("R", "A");
        assertEquals("plug", out.get(0).get("name"));
    }

    @Test
    void is_controller_active_true() {
        assertTrue(agentsWithReply("{\"is_controller_active\":\"true\"}").is_controller_active("R", "A"));
    }

    @Test
    void is_controller_active_false_when_absent() {
        assertFalse(agentsWithReply("{}").is_controller_active("R", "A"));
    }

    @Test
    void get_pipeline_id_by_name_matches() {
        String reply = "{\"pipelineinfo\":\"" + compress(Map.of("pipelines", List.of(
                Map.of("pipeline_id", "1", "pipeline_name", "alpha"),
                Map.of("pipeline_id", "2", "pipeline_name", "beta")))) + "\"}";
        assertEquals("2", gcWithReply(reply).get_pipeline_id_by_name("beta"));
    }

    @Test
    void get_global_info_reads_region_and_agent() {
        API api = new API(new ReplyMsgEvent("{\"global_region\":\"gr\",\"global_agent\":\"ga\"}"),
                new Messaging(new ReplyMsgEvent("{\"global_region\":\"gr\",\"global_agent\":\"ga\"}")));
        Map.Entry<String, String> info = api.get_global_info();
        assertEquals("gr", info.getKey());
        assertEquals("ga", info.getValue());
    }
}
