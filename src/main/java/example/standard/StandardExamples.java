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
 * language syntax differs. (example_messaging and example_stunnel are Java-first; their pycrescolib
 * counterparts use the same calls: messaging.global_controller_msgevent and the stunnel submodule.)
 *
 * Connection settings come from the environment (CRESCO_HOST, CRESCO_PORT, CRESCO_SERVICE_KEY), falling
 * back to 127.0.0.1:8282. Run one example by name, e.g.
 *   java -cp target/clientlib-1.3-SNAPSHOT.jar example.standard.StandardExamples connect
 *   java -cp ... example.standard.StandardExamples stunnel global-region global-controller 5202 localhost 5201
 */
public class StandardExamples {

    static final String HOST = env("CRESCO_HOST", "127.0.0.1");
    static final int PORT = Integer.parseInt(env("CRESCO_PORT", "8282"));
    static final String SERVICE_KEY = env("CRESCO_SERVICE_KEY", "a-service-key");

    static String env(String name, String dflt) {
        String v = System.getenv(name);
        return (v == null || v.isEmpty()) ? dflt : v;
    }

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

    /** Send raw MsgEvents: one to the global controller, one to an agent (via the global controller). */
    static void example_messaging(String dst_region, String dst_agent) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. RPC to the global controller: the payload's "action" selects the controller action.
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "listregions");
        Map<String, String> reply = client.messaging.global_controller_msgevent(true, "EXEC", payload);
        // Large values come back gzip+base64 compressed; decode with getCompressedParam.
        System.out.println("regions: " + client.messaging.getCompressedParam(reply.get("regionslist")));

        // 2. RPC to an agent's controller, routed through the global controller (getagentinfo is a CONFIG action).
        Map<String, Object> agent_payload = new HashMap<>();
        agent_payload.put("action", "getagentinfo");
        System.out.println("agent info: " + client.messaging.global_agent_msgevent(true, "CONFIG", agent_payload, dst_region, dst_agent));

        // 3. Disconnect.
        client.close();
    }

    /** Load the stunnel plugin, create a tunnel, inspect it, then remove it. No CADL involved. */
    static void example_stunnel(String region, String agent, String src_port, String dst_host, String dst_port) throws Exception {
        CrescoClient client = new_client();
        if (client == null) {
            return;
        }

        // 1. Make sure the stunnel plugin runs on the agent (loads the copy embedded in the agent if needed).
        System.out.println("stunnel plugin: " + client.stunnel.deploy_plugin(region, agent));

        // 2. Create a tunnel: listen on agent:src_port, forward to dst_host:dst_port (single-node here;
        //    use a different dst region/agent to cross the mesh).
        Map<String, String> config = client.stunnel.create_tunnel("example-tunnel",
                region, agent, src_port, region, agent, dst_host, dst_port, "65536");
        System.out.println("created: " + config);

        // 3. Inspect: status, list, persisted config.
        System.out.println("status : " + client.stunnel.get_tunnel_status(region, agent, "example-tunnel"));
        System.out.println("list   : " + client.stunnel.get_tunnel_list(region, agent));
        System.out.println("config : " + client.stunnel.get_tunnel_config(region, agent, "example-tunnel"));

        // 4. Remove both ends and disconnect.
        System.out.println("removed: " + client.stunnel.remove_tunnel("example-tunnel", region, agent, region, agent));
        client.close();
    }

    public static void main(String[] args) throws Exception {
        // Run the connect example by default; pass an example name (and its arguments) to run another.
        String which = args.length > 0 ? args[0] : "connect";
        switch (which) {
            case "connect": example_connect(); break;
            case "pipeline": example_pipeline(); break;
            case "plugin_lifecycle": example_plugin_lifecycle(args[1], args[2], args[3]); break;
            case "dataplane": example_dataplane(args[1]); break;
            case "logstreamer": example_logstreamer(args[1], args[2]); break;
            case "admin": example_admin(args[1], args[2]); break;
            case "messaging": example_messaging(args[1], args[2]); break;
            case "stunnel": example_stunnel(args[1], args[2], args[3], args[4], args[5]); break;
            default: System.out.println("unknown example: " + which);
        }
    }
}
