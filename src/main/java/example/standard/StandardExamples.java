package example.standard;

import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Standardized Cresco client examples (Java).
 *
 * Every example here has a byte-for-byte counterpart in the Python client
 * (pycrescolib: examples/standard_examples.py) with the same function name,
 * the same step-by-step structure, and the same narrative comments. Only the
 * language syntax differs. Edit HOST/PORT/SERVICE_KEY, then run one example.
 */
public class StandardExamples {

    static final String HOST = "127.0.0.1";
    static final int PORT = 8282;
    static final String SERVICE_KEY = "a-service-key";

    /** Create a connected client, or null if the connection failed. */
    static CrescoClient new_client() throws Exception {
        CrescoClient client = new CrescoClient(HOST, PORT, SERVICE_KEY, false);
        if (!client.connect()) {
            System.out.println("Failed to connect");
            return null;
        }
        return client;
    }

    /** Connect, print API identity, list the mesh, disconnect. */
    static void example_connect() throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Print the API identity of the agent hosting the wsapi plugin.
        System.out.println("region: " + client.api.get_api_region_name());
        System.out.println("agent : " + client.api.get_api_agent_name());
        System.out.println("plugin: " + client.api.get_api_plugin_name());

        // 2. Print the global controller identity.
        System.out.println("global region: " + client.api.get_global_region());
        System.out.println("global agent : " + client.api.get_global_agent());

        // 3. List the regions and the agents.
        System.out.println("regions: " + client.globalcontroller.get_region_list());
        System.out.println("agents : " + client.globalcontroller.get_agent_list(null));

        // 4. Disconnect.
        client.close();
    }

    /** Submit a CADL pipeline, poll status, fetch info, export, then remove it. */
    static void example_pipeline() throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Define a minimal CADL pipeline.
        Map<String, Object> cadl = new HashMap<>();
        cadl.put("pipeline_id", "0");
        cadl.put("pipeline_name", "example-pipeline");

        // 2. Submit the pipeline for tenant 0.
        Map<String, String> reply = client.globalcontroller.submit_pipeline(cadl, "0");
        String pipeline_id = reply.get("gpipeline_id");
        System.out.println("submitted pipeline: " + pipeline_id);

        // 3. Poll until the pipeline is active (status code 10).
        for (int i = 0; i < 30; i++) {
            int status = client.globalcontroller.get_pipeline_status(pipeline_id);
            System.out.println("status: " + status);
            if (status == 10) {
                break;
            }
            Thread.sleep(1000);
        }

        // 4. Fetch full info and an export of the pipeline.
        System.out.println("info  : " + client.globalcontroller.get_pipeline_info(pipeline_id));
        System.out.println("export: " + client.globalcontroller.get_pipeline_export(pipeline_id));

        // 5. Remove the pipeline and disconnect.
        client.globalcontroller.remove_pipeline(pipeline_id);
        client.close();
    }

    /** Add a plugin to an agent, list/status it, then remove it. */
    static void example_plugin_lifecycle(String dst_region, String dst_agent, String jar_file_path) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Upload the plugin jar to the agent's repo, then add it as a plugin.
        Map<String, String> info = client.agents.repo_pull_plugin_agent(dst_region, dst_agent, jar_file_path);
        System.out.println("repo pull: " + info);
        Map<String, String> configparams = new HashMap<>();
        configparams.put("pluginname", "example");
        configparams.put("jarfile", jar_file_path);
        Map<String, String> added = client.agents.add_plugin_agent(dst_region, dst_agent, configparams, null);
        String plugin_id = added.get("pluginid");
        System.out.println("added plugin: " + plugin_id);

        // 2. List plugins on the agent and check this plugin's status.
        System.out.println("plugins: " + client.agents.list_plugin_agent(dst_region, dst_agent));
        System.out.println("status : " + client.agents.status_plugin_agent(dst_region, dst_agent, plugin_id));

        // 3. Remove the plugin and disconnect.
        client.agents.remove_plugin_agent(dst_region, dst_agent, plugin_id);
        client.close();
    }

    /** Open a dataplane stream, send text + binary + a fragmented message, receive via callback. */
    static void example_dataplane(String stream_query) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Open a dataplane for the stream query, printing anything received.
        OnMessageCallback on_message = new OnMessageCallback() {
            @Override
            public void onMessage(String message) {
                System.out.println("dataplane recv: " + message);
            }
            @Override
            public void onMessage(byte[] b, int offset, int length) {
                System.out.println("dataplane recv: " + length + " bytes");
            }
        };

        DataPlaneInterface dp = client.get_dataplane(stream_query, on_message);
        dp.connect();
        Thread.sleep(2000);

        // 2. Send a text message, a binary message, and a fragmented (partial) message.
        dp.send("hello dataplane");
        dp.send_binary(ByteBuffer.wrap(new byte[]{0, 1, 2, 3}));
        dp.send_partial(ByteBuffer.wrap("part-1".getBytes(StandardCharsets.UTF_8)), false);
        dp.send_partial(ByteBuffer.wrap("part-2".getBytes(StandardCharsets.UTF_8)), true);
        Thread.sleep(2000);

        // 3. Close the stream and disconnect.
        client.close_dataplane(stream_query);
        client.close();
    }

    /** Attach a log streamer, set the log config (incl. a per-baseclass level), stream logs. */
    static void example_logstreamer(String dst_region, String dst_agent) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Attach a log streamer, printing anything received.
        OnMessageCallback on_message = new OnMessageCallback() {
            @Override
            public void onMessage(String message) {
                System.out.println("log: " + message);
            }
            @Override
            public void onMessage(byte[] b, int offset, int length) {
                System.out.println("log: " + length + " bytes");
            }
        };

        LogStreamerInterface ls = client.get_logstreamer("example", on_message);
        ls.connect();
        Thread.sleep(2000);

        // 2. Set the log configuration, then raise one base class to Trace.
        ls.update_config(dst_region, dst_agent);
        ls.update_config_class(dst_region, dst_agent, "Trace", "io.cresco.agent");
        Thread.sleep(5000);

        // 3. Close the stream and disconnect.
        client.close_logstreamer("example");
        client.close();
    }

    /** Check an agent's controller, then restart its framework (guarded). */
    static void example_admin(String dst_region, String dst_agent) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Only act if the target agent's controller is active.
        if (client.agents.is_controller_active(dst_region, dst_agent)) {
            System.out.println("controller status: " + client.agents.get_controller_status(dst_region, dst_agent));

            // 2. Restart the OSGi framework on the target agent.
            client.admin.restartframework(dst_region, dst_agent);
        } else {
            System.out.println("controller not active on " + dst_region + " / " + dst_agent);
        }

        // 3. Disconnect.
        client.close();
    }

    public static void main(String[] args) throws Exception {
        // Run the connect example by default; call the others with your own region/agent/jar/stream.
        example_connect();
    }
}
