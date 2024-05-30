package example.stunnel;

import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import example.TestUtils;
import io.cresco.library.app.gNode;
import io.cresco.library.app.gPayload;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TunnelTesting {

    private CrescoClient client;
    public TunnelTesting(CrescoClient client) {
        this.client = client;
    }

    public void singleAgentTunnelTest() throws InterruptedException {

        String pipelineName = "sTunnelExample single agent";
        String pipelineId = client.globalcontroller.getPipelineIdByName(pipelineName);
        client.globalcontroller.remove_pipeline(pipelineId);

        String sTunnelAppId = deploySingleNodeSTunnel(pipelineName);
        runTest(sTunnelAppId);
    }

    public void twoAgentTunnelTest() throws InterruptedException {
        twoAgentTunnelTest(null, null);
    }

    public void twoAgentTunnelTest(String clientRegion, String clientAgent) throws InterruptedException {

        String pipelineName = "sTunnelExample two agent";
        String pipelineId = client.globalcontroller.getPipelineIdByName(pipelineName);
        client.globalcontroller.remove_pipeline(pipelineId);

        String sTunnelAppId = deployMultiNodeSTunnel(pipelineName, clientRegion, clientAgent);
        runTest(sTunnelAppId);
    }


    public void runTest(String sTunnelAppId) {

        try {


            //String sTunnelAppId = testers.getPipelineIdByName(pipelineName);
            //String sTunnelAppId = deploySingleNodeSTunnel(pipelineName);
            //String sTunnelAppId = deployMultiNodeSTunnel(pipelineName);
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

            String message_event_type = "CONFIG";
            Map<String, Object> message_payload = new HashMap();
            message_payload.put("action", "configsrctunnel");
            message_payload.put("action_src_port", "5202");
            message_payload.put("action_dst_host", "localhost");
            message_payload.put("action_dst_port", "5201");
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

    public String deploySingleNodeSTunnel(String pipelineName) throws InterruptedException {

        //Check if there is an existing pipeline with the same name
        String pipelineId = client.globalcontroller.getPipelineIdByName(pipelineName);
        //If there is a pipeline remove it
        if (pipelineId != null) {
            client.globalcontroller.remove_pipeline(pipelineId);
        }

        //Location of latest stunnel
        String uri = "https://github.com/CrescoEdge/stunnel/releases/download/1.1-SNAPSHOT/stunnel-1.1-SNAPSHOT.jar";

        //Local save file
        String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

        if(!(new File(pluginSavePath).isFile())) {
            //pull plugin down from github
            getPlugin(uri, pluginSavePath);
        }

        //String pluginSavePath = "/Users/cody/IdeaProjects/stunnel/target/stunnel-1.1-SNAPSHOT.jar";

        //Upload plugin to repo
        Map<String, String> sTunnelMap = client.globalcontroller.upload_plugin_global(pluginSavePath);

        //Get details about plugin
        String sTunnelConfigParamsString = client.messaging.getCompressedParam(sTunnelMap.get("configparams"));
        Map<String, String> sTunnelConfigParams = client.messaging.getMapFromString(sTunnelConfigParamsString);

        //Build the CADL config
        Map<String, Object> cadl = new HashMap<>();
        cadl.put("pipeline_id", "0");
        cadl.put("pipeline_name", pipelineName);
        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        Map<String, Object> params0 = new HashMap<>();
        params0.put("pluginname", sTunnelConfigParams.get("pluginname"));
        params0.put("md5", sTunnelConfigParams.get("md5"));
        params0.put("version", sTunnelConfigParams.get("version"));
        params0.put("location_region", client.api.getAPIRegionName());
        params0.put("location_agent", client.api.getAPIAgentName());

        Map<String, Object> node0 = new HashMap<>();
        node0.put("type", "dummy");
        node0.put("node_name", "Plugin 0");
        node0.put("node_id", 0);
        node0.put("isSource", false);
        node0.put("workloadUtil", 0);
        node0.put("params", params0);

        Map<String, Object> params1 = new HashMap<>();
        params1.put("pluginname", sTunnelConfigParams.get("pluginname"));
        params1.put("md5", sTunnelConfigParams.get("md5"));
        params1.put("version", sTunnelConfigParams.get("version"));
        params1.put("location_region", client.api.getAPIRegionName());
        params1.put("location_agent", client.api.getAPIAgentName());

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

        //Submit pipeline
        Map<String, String> reply = client.globalcontroller.submit_pipeline("0", cadl);
        //Get pipelineId
        pipelineId = reply.get("gpipeline_id");

        System.out.println("Starting PipelineId: " + pipelineId);

        int app_status = -1;

        while (app_status != 10) {

            //get identifier for application
            gPayload fileRepoDeployStatus = client.globalcontroller.get_pipeline_info(pipelineId);
            app_status = Integer.parseInt(fileRepoDeployStatus.status_code);
            Thread.sleep(1000);
        }
        System.out.println("Started PipelineId: " + pipelineId);

        return pipelineId;
    }
    public String deployMultiNodeSTunnel(String pipelineName, String clientRegion, String clientAgent) throws InterruptedException {

        //Check if there is an existing pipeline with the same name
        String pipelineId = client.globalcontroller.getPipelineIdByName(pipelineName);
        //If there is a pipeline remove it
        if (pipelineId != null) {
            client.globalcontroller.remove_pipeline(pipelineId);
        }

        //Location of latest stunnel
        String uri = "https://github.com/CrescoEdge/stunnel/releases/download/1.1-SNAPSHOT/stunnel-1.1-SNAPSHOT.jar";

        //Local save file
        String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

        if(!(new File(pluginSavePath).isFile())) {
            //pull plugin down from github
            getPlugin(uri, pluginSavePath);
        }

        if(clientRegion == null) {
            String globalRegion = client.api.getGlobalRegion();
            List<Map<String, String>> regionList = client.globalcontroller.get_region_list().get("regions");
            for (Map<String, String> regionMap : regionList) {
                if (!regionMap.get("name").equals(globalRegion)) {
                    clientRegion = regionMap.get("name");
                }
            }
            if(clientRegion == null) {
                clientRegion = globalRegion;
            }

            String globalAgent = client.api.getGlobalAgent();
            if(clientAgent == null) {

                List<Map<String, String>> agentList = client.globalcontroller.get_agent_list(clientRegion).get("agents");
                for (Map<String, String> agentMap : agentList) {
                    if (!agentMap.get("name").equals(globalAgent)) {
                        clientAgent = agentMap.get("name");
                    }
                }
            }
            if(clientAgent == null) {
                clientAgent = globalAgent;
            }
        }

        //String pluginSavePath = "/Users/cody/IdeaProjects/stunnel/target/stunnel-1.1-SNAPSHOT.jar";

        if(clientAgent != null) {

            //Upload plugin to repo
            Map<String, String> sTunnelMap = client.globalcontroller.upload_plugin_global(pluginSavePath);

            //Get details about plugin
            String sTunnelConfigParamsString = client.messaging.getCompressedParam(sTunnelMap.get("configparams"));
            Map<String, String> sTunnelConfigParams = client.messaging.getMapFromString(sTunnelConfigParamsString);

            //Build the CADL config
            Map<String, Object> cadl = new HashMap<>();
            cadl.put("pipeline_id", "0");
            cadl.put("pipeline_name", pipelineName);
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            Map<String, Object> params0 = new HashMap<>();
            params0.put("pluginname", sTunnelConfigParams.get("pluginname"));
            params0.put("md5", sTunnelConfigParams.get("md5"));
            params0.put("version", sTunnelConfigParams.get("version"));
            params0.put("location_region", client.api.getAPIRegionName());
            params0.put("location_agent", client.api.getAPIAgentName());

            Map<String, Object> node0 = new HashMap<>();
            node0.put("type", "dummy");
            node0.put("node_name", "Plugin 0");
            node0.put("node_id", 0);
            node0.put("isSource", false);
            node0.put("workloadUtil", 0);
            node0.put("params", params0);

            Map<String, Object> params1 = new HashMap<>();
            params1.put("pluginname", sTunnelConfigParams.get("pluginname"));
            params1.put("md5", sTunnelConfigParams.get("md5"));
            params1.put("version", sTunnelConfigParams.get("version"));
            if(clientRegion != null) {
                params1.put("location_region", clientRegion);
            } else {
                params1.put("location_region", client.api.getAPIRegionName());
            }
            params1.put("location_agent", clientAgent);

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

            //Submit pipeline
            Map<String, String> reply = client.globalcontroller.submit_pipeline("0", cadl);
            //Get pipelineId
            pipelineId = reply.get("gpipeline_id");

            System.out.println("Starting PipelineId: " + pipelineId);

            int app_status = -1;

            while (app_status != 10) {

                //get identifier for application
                gPayload fileRepoDeployStatus = client.globalcontroller.get_pipeline_info(pipelineId);
                app_status = Integer.parseInt(fileRepoDeployStatus.status_code);
                Thread.sleep(1000);
            }
            System.out.println("Started PipelineId: " + pipelineId);
        } else {
            System.out.println("Could not find second agent");
        }

        return pipelineId;

    }
    public void getPlugin(String uri, String fileName) {

        try{

            URI srcUri = URI.create(uri);

            SslContextFactory ssl = new SslContextFactory(true);

            HttpClient client = new HttpClient(ssl);
            try
            {
                client.start();

                Request request = client.newRequest(srcUri);

                //System.out.printf("Using HttpClient v%s%n", getHttpClientVersion());
                System.out.printf("Requesting: %s%n", srcUri);
                InputStreamResponseListener streamResponseListener = new InputStreamResponseListener();
                request.send(streamResponseListener);
                Response response = streamResponseListener.get(5, TimeUnit.SECONDS);

                if (response.getStatus() != HttpStatus.OK_200)
                {
                    throw new IOException(
                            String.format("Failed to GET URI [%d %s]: %s",
                                    response.getStatus(),
                                    response.getReason(),
                                    srcUri));
                }

                Path filePath = Paths.get(fileName);

                try (InputStream inputStream = streamResponseListener.getInputStream();
                     OutputStream outputStream = Files.newOutputStream(filePath))
                {
                    IO.copy(inputStream, outputStream);
                }

                System.out.printf("Downloaded %s%n", srcUri);
                System.out.printf("Destination: %s (%,d bytes)%n", filePath.toString(), Files.size(filePath));
            }
            finally
            {
                client.stop();
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }



}
