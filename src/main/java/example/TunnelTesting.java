package example;

import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import io.cresco.library.app.gNode;
import io.cresco.library.app.gPayload;

import java.util.HashMap;
import java.util.Map;

public class TunnelTesting {

    private CrescoClient client;
    public TunnelTesting(CrescoClient client) {
        this.client = client;
    }

    public void runTest() {

        try {

            Testers testers = new Testers(client);

            String pipelineName = "sTunnelExample";
            String pipelineId = testers.getPipelineIdByName(pipelineName);

            boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            //System.out.println(isRemoved);


            //System.exit(0);

            //String sTunnelAppId = testers.getPipelineIdByName(pipelineName);
            //String sTunnelAppId = testers.deploySingleNodeSTunnel(pipelineName);
            String sTunnelAppId = testers.deployMultiNodeSTunnel(pipelineName);
            //String sTunnelAppId = "resource-13d93383-8687-4b5c-8325-31e57445bfb3";
            gPayload st = client.globalcontroller.get_pipeline_info(sTunnelAppId);
            //System.out.println(st.nodes.get(0).node_id);
            System.out.println(st.nodes.get(0).params);
            for(gNode node : st.nodes) {
                System.out.println("--");
                System.out.println(node.params);
            }

            String srcRegionId = st.nodes.get(0).params.get("region_id");
            String srcAgentId = st.nodes.get(0).params.get("agent_id");
            String srcPluginId = st.nodes.get(0).params.get("plugin_id");

            String dstRegionId = st.nodes.get(1).params.get("region_id");
            String dstAgentId = st.nodes.get(1).params.get("agent_id");
            String dstPluginId = st.nodes.get(1).params.get("plugin_id");

            System.out.println("region: " + srcRegionId + " agent: " + srcAgentId + " plugin: " + srcPluginId);

            class RepoPrinter implements OnMessageCallback {

                @Override
                public void onMessage(String msg) {

                    System.out.println("INCOMING FROM DP: " + msg);
                }

                @Override
                public void onMessage(byte[] b, int offset, int length) {
                    System.out.println("Launcher RepoPrinter onMessage(Bytes[] b) not implemented");
                }

            }

            //String queryStringRepo1 = "filerepo_name='" + tunnelId + "' AND broadcast";
            String queryStringRepo1 = "";
            DataPlaneInterface dataPlaneRepo1 = client.getDataPlane(queryStringRepo1, new RepoPrinter());
            dataPlaneRepo1.start();

            String message_event_type = "CONFIG";
            Map<String, Object> message_payload = new HashMap();
            message_payload.put("action", "configsrctunnel");
            message_payload.put("action_src_port", "5201");

            //message_payload.put("action_dst_host", "128.163.189.58");
            //message_payload.put("action_dst_port", "80");
            //message_payload.put("action_dst_host", "128.163.202.50");
            //message_payload.put("action_dst_port", "5201");
            message_payload.put("action_dst_host", "localhost");
            message_payload.put("action_dst_port", "5202");
            message_payload.put("action_dst_region", dstRegionId);
            message_payload.put("action_dst_agent", dstAgentId);
            message_payload.put("action_dst_plugin", dstPluginId);
            message_payload.put("action_buffer_size",String.valueOf(256000));
            Map<String, String> responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

            System.out.println(responce);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

}
