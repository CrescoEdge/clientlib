package example;

import com.google.gson.Gson;
import crescoclient.CrescoClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Testers {

    private Gson gson;
    private CrescoClient client;
    public Testers(CrescoClient client) {
        this.client = client;
        gson = new Gson();
    }

    public void launch_apps(String dst_region, String dst_agent, String jar_file_path, int count) {

        try {
            Map<String, String> jar_info = client.globalcontroller.upload_plugin_global(jar_file_path);

            String configParamsString = client.messaging.getCompressedParam(jar_info.get("configparams"));
            Map<String,String> configparams = client.messaging.getMapFromString(configParamsString);

            System.out.println(configparams);

            for (int i = 0; i < count; i++) {
                submit_app(dst_region, dst_agent, configparams);
                Thread.sleep(1000);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public Map<String,String> submit_app(String dst_region, String dst_agent, Map<String,String> jar_info) {

        Map<String,String> reply = null;
        try {
            Map<String, Object> cadl = new HashMap<>();
            cadl.put("pipeline_id", "0");
            cadl.put("pipeline_name", "mycadl");
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            Map<String, Object> params0 = new HashMap<>();
            params0.put("pluginname", jar_info.get("pluginname"));
            params0.put("md5", jar_info.get("md5"));
            params0.put("jarfile", jar_info.get("jarfile"));
            params0.put("version", jar_info.get("version"));
            params0.put("mode", 0);
            params0.put("location_region", dst_region);
            params0.put("location_agent", dst_agent);

            Map<String, Object> node0 = new HashMap<>();
            node0.put("type", "dummy");
            node0.put("node_name", "Plugin 0");
            node0.put("node_id", 0);
            node0.put("isSource", false);
            node0.put("workloadUtil", 0);
            node0.put("params", params0);

            Map<String, Object> params1 = new HashMap<>();
            params1.put("pluginname", jar_info.get("pluginname"));
            params1.put("md5", jar_info.get("md5"));
            params1.put("jarfile", jar_info.get("jarfile"));
            params1.put("version", jar_info.get("version"));
            params1.put("mode", 1);
            params1.put("location_region", dst_region);
            params1.put("location_agent", dst_agent);

            Map<String, Object> node1 = new HashMap<>();
            node1.put("type", "dummy");
            node1.put("node_name", "Plugin 1");
            node1.put("node_id", 1);
            node1.put("isSource", false);
            node1.put("workloadUtil", 0);
            node1.put("params", params1);

            Map<String, Object> edge0 = new HashMap<>();
            edge0.put("edge_id", 0);
            edge0.put("node_from", 0);
            edge0.put("node_to", 1);
            edge0.put("params", new HashMap<>());

            nodes.add(node0);
            nodes.add(node1);
            edges.add(edge0);

            cadl.put("nodes", nodes);
            cadl.put("edges", edges);

            String message_event_type = "CONFIG";
            Map<String, Object> message_payload = new HashMap<>();
            message_payload.put("action", "gpipelinesubmit");
            String json_cadl = gson.toJson(cadl);
            System.out.println(json_cadl);

            message_payload.put("action_gpipeline", client.messaging.setCompressedParam(json_cadl));
            message_payload.put("action_tenantid", "0");

            reply = client.messaging.global_controller_msgevent(true, message_event_type, message_payload);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return reply;
    }

}
