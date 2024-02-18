package example;

import com.google.gson.Gson;
import crescoclient.CrescoClient;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
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

public class Testers {

    private Gson gson;
    private CrescoClient client;
    public Testers(CrescoClient client) {
        this.client = client;
        gson = new Gson();
    }

    public String getPipelineIdByName(String pipelineName) {

        String pipelineId = null;

        List<Map<String,String>> pipelineList = client.globalcontroller.get_pipeline_list();
        for(Map<String,String> pipeline : pipelineList) {
            if(pipeline.get("pipeline_name").equals(pipelineName)) {
                pipelineId = pipeline.get("pipeline_id");
            }
        }

        return pipelineId;

    }

    public String deploySingleNodeSTunnel(String pipelineName) throws InterruptedException {

        boolean launchRepo = true;

        //Check if the pipeline is running
        String pipelineId = getPipelineIdByName(pipelineName);

        if(pipelineId != null) {
            //get status of running pipeline
            //int pipelineStatus = client.globalcontroller.get_pipeline_status(pipelineId);

            boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            /*
            if (pipelineStatus == 10) {
                launchRepo = false;
            } else {
                //if pipeline is not in a good status remove
                boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            }

             */
        }

        if(launchRepo) {

            /*
            //Location of latest filerepo
            String uri = "https://github.com/CrescoEdge/filerepo/releases/download/1.1-SNAPSHOT/filerepo-1.1-SNAPSHOT.jar";

            //Local save file
            String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

            if(!(new File(pluginSavePath).isFile())) {
                //pull plugin down from github
                getPlugin(uri, pluginSavePath);
            }

             */
            String pluginSavePath = "/Users/cody/IdeaProjects/stunnel/target/stunnel-1.1-SNAPSHOT.jar";

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
        }

        return pipelineId;

    }

    public String deployMultiNodeSTunnel(String pipelineName) throws InterruptedException {

        boolean launchRepo = true;

        //Check if the pipeline is running
        String pipelineId = getPipelineIdByName(pipelineName);

        if(pipelineId != null) {
            //get status of running pipeline
            //int pipelineStatus = client.globalcontroller.get_pipeline_status(pipelineId);

            boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            /*
            if (pipelineStatus == 10) {
                launchRepo = false;
            } else {
                //if pipeline is not in a good status remove
                boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            }

             */
        }

        if(launchRepo) {

            /*
            //Location of latest filerepo
            String uri = "https://github.com/CrescoEdge/filerepo/releases/download/1.1-SNAPSHOT/filerepo-1.1-SNAPSHOT.jar";

            //Local save file
            String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

            if(!(new File(pluginSavePath).isFile())) {
                //pull plugin down from github
                getPlugin(uri, pluginSavePath);
            }

             */

            //get config of second agents

            String globalAgent = client.api.getGlobalAgent();
            List<Map<String,String>> agentList =  client.globalcontroller.get_agent_list(client.api.getGlobalRegion()).get("agents");
            String clientAgent = null;
            for(Map<String,String> agentMap : agentList) {
                if(!agentMap.get("name").equals(globalAgent)) {
                    clientAgent = agentMap.get("name");
                }
            }

            if(clientAgent != null) {

                String pluginSavePath = "/Users/cody/IdeaProjects/stunnel/target/stunnel-1.1-SNAPSHOT.jar";

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

        }

        return pipelineId;

    }


    public String deployFileRepo(String pipelineName, String repo_name_1, String repo_path_1, String repo_name_2, String repo_path_2) throws InterruptedException {

        boolean launchRepo = true;

        //Check if the pipeline is running
        String pipelineId = getPipelineIdByName(pipelineName);

        if(pipelineId != null) {
            //get status of running pipeline
            int pipelineStatus = client.globalcontroller.get_pipeline_status(pipelineId);

            if (pipelineStatus == 10) {
                launchRepo = false;
            } else {
                //if pipeline is not in a good status remove
                boolean isRemoved = client.globalcontroller.remove_pipeline(pipelineId);
            }
        }

        if(launchRepo) {

            //Location of latest filerepo
            String uri = "https://github.com/CrescoEdge/filerepo/releases/download/1.1-SNAPSHOT/filerepo-1.1-SNAPSHOT.jar";

            //Local save file
            String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

            if(!(new File(pluginSavePath).isFile())) {
                //pull plugin down from github
                getPlugin(uri, pluginSavePath);
            }

            //Upload plugin to repo
            Map<String, String> fileRepoMap = client.globalcontroller.upload_plugin_global(pluginSavePath);

            //Get details about plugin
            String fileRepoConfigParamsString = client.messaging.getCompressedParam(fileRepoMap.get("configparams"));
            Map<String, String> fileRepoConfigParams = client.messaging.getMapFromString(fileRepoConfigParamsString);

            //Build the CADL config
            Map<String, Object> cadl = new HashMap<>();
            cadl.put("pipeline_id", "0");
            cadl.put("pipeline_name", pipelineName);
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            Map<String, Object> params0 = new HashMap<>();
            params0.put("pluginname", fileRepoConfigParams.get("pluginname"));
            params0.put("md5", fileRepoConfigParams.get("md5"));
            params0.put("version", fileRepoConfigParams.get("version"));
            params0.put("location_region", client.api.getAPIRegionName());
            params0.put("location_agent", client.api.getAPIAgentName());
            params0.put("filerepo_name", repo_name_1);
            params0.put("scan_dir", repo_path_1);

            Map<String, Object> node0 = new HashMap<>();
            node0.put("type", "dummy");
            node0.put("node_name", "Plugin 0");
            node0.put("node_id", 0);
            node0.put("isSource", false);
            node0.put("workloadUtil", 0);
            node0.put("params", params0);

            Map<String, Object> params1 = new HashMap<>();
            params1.put("pluginname", fileRepoConfigParams.get("pluginname"));
            params1.put("md5", fileRepoConfigParams.get("md5"));
            params1.put("version", fileRepoConfigParams.get("version"));
            params1.put("location_region", client.api.getAPIRegionName());
            params1.put("location_agent", client.api.getAPIAgentName());
            params1.put("filerepo_name", repo_name_2);
            params1.put("scan_dir", repo_path_2);

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
        }

        return pipelineId;

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

    public void getResourcesAndLists() {

        String dst_region = client.api.getGlobalRegion();
        String dst_agent = client.api.getGlobalAgent();

        Map<String, List<Map<String,String>>> agentsList = client.globalcontroller.get_agent_list(dst_region);
        System.out.println("Agent List: " + agentsList);

        //Map<String,List<Map<String,String>>> agentResources = client.globalcontroller.get_agent_resource(dst_region, dst_agent);
        //System.out.println("Agent Resources: " + agentResources);

        Map<String,List<Map<String,String>>> pluginResources = client.globalcontroller.get_repo_plugins();
        System.out.println("Plugin Resources: " + pluginResources);

        //Map<String, List<Map<String,String>>> regionResourcesList = client.globalcontroller.get_region_resources(dst_region);
        //System.out.println("Regional Resources: " + regionResourcesList);

        Map<String, List<Map<String,String>>> regionList = client.globalcontroller.get_region_list();
        System.out.println("Region List: " + regionList);

    }

    public void logStreaming() {

        LogStreamerInterface ls = client.getLogStreamer();
        ls.start();
        while (!ls.connected()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        String dst_region = client.api.getGlobalRegion();
        String dst_agent = client.api.getGlobalAgent();

        ls.update_config(dst_region, dst_agent);

        String identKey = "stream_name";
        String identId = "1234";
        //String streamQuery = "stream_name='" + identId + "'";
        Map<String, String> configDB = new HashMap<>();
        configDB.put("ident_key", identKey);
        configDB.put("ident_id", identId);
        //configDB.put("stream_query",identKey + "='" + identId + "' and type='" + "outgoing" + "'");
        configDB.put("io_type_key", "type");
        configDB.put("output_id", "output");
        configDB.put("input_id", "output");
        Gson gson = new Gson();

        String jsonConfig = gson.toJson(configDB);

        DataPlaneInterface dataPlane = client.getDataPlane(jsonConfig);
        dataPlane.start();

        int count = 25;

        for (int i = 0; i < count; i++) {
            try {
                Thread.sleep(1000);
                System.out.println("count: " + i);
                //System.out.println("Incoming: " + ls.recv());
                if (i < 5) {
                    dataPlane.send(String.valueOf(i));
                } else {
                    dataPlane.close();
                    ls.close();
                    client.close();
                }

            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        System.out.println("EXIT");
        ls.close();

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


    /*
            System.exit(0);

            System.out.println("--Start repeated testing--");

            int count = 0;
            while(true) {
                System.out.println("Count: " + count);
                System.out.println("Client status: " + client.connected());
                try {
                    testers.getResourcesAndLists();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                Thread.sleep(1000);
                count++;
            }

             */


    //client.close();

    //Testers testers = new Testers(client);

    //String dst_region = "global-region";
    //String dst_agent = "global-controller";
    //String jar_file_path = "/Users/cody/IdeaProjects/cepdemo/target/cepdemo-1.1-SNAPSHOT.jar";

    //launch pipeline
        /*
        testers.launch_apps(dst_region,dst_agent,jar_file_path,1);
        String pipeline_id = client.globalcontroller.get_pipeline_list().get(0).get("pipeline_id");
        System.out.println(pipeline_id);
        gPayload gpay = client.globalcontroller.get_pipeline_info(pipeline_id);
        System.out.println(gpay.pipeline_name);
        System.out.println(client.globalcontroller.get_pipeline_status(gpay.pipeline_id));
         */

        /*
        Map<String,String> results = client.globalcontroller.upload_jar_info(jar_file_path);

        Map<String,String> result_agent = client.agents.repo_pull_plugin_agent(dst_region, dst_agent, jar_file_path);
        System.out.println(result_agent);

        String configParamsString = client.messaging.getCompressedParam(results.get("configparams"));
        Map<String,String> configparams = client.messaging.getMapFromString(configParamsString);

        Map<String,String> add_reply = client.agents.add_plugin_agent(dst_region,dst_agent,configparams,null);

        String dst_plugin = add_reply.get("pluginid");

        Map<String,String> remove_reply = client.agents.remove_plugin_agent(dst_region,dst_agent,dst_plugin);

         */

    //String pipeline_id = client.globalcontroller.get_pipeline_list().get(0).get("pipeline_id");

    //System.out.println(gson.toJson(client.globalcontroller.get_pipeline_info(resource_id)));
    //String inode_id = "inode-ea7139fb-389d-4d59-a9e2-e749a5b22e86";
    //Map<String, String> addi = client.globalcontroller.pipeline_getisassignmentinfo(inode_id, pipeline_id);
    //Map<String, String> addi = client.globalcontroller.get_pipeline_export(pipeline_id);
    //System.out.println(client.messaging.getCompressedParam(addi.get("gpipeline")));

    //Map<String,String> responce = client.agents.get_log(dst_region, dst_agent);
    //String info = new String(client.messaging.getCompressedDataParam(responce.get("log")));
    //Map<String,List<Map<String,String>>> regionList = client.globalcontroller.get_region_list();
    //System.out.println(regionList);
    //Map<String,String> responce = client.agents.get_log(dst_region, dst_agent);
    //List<Map<String,String>> responce = client.globalcontroller.get_plugin_list(dst_region, dst_agent);
    //Map<String,List<Map<String,String>>> responce = client.globalcontroller.get_plugin_repo_list();
    //System.out.println(responce);

    //System.exit(0);


    //Thread.sleep(5000);
            /*
            String message_event_type = "EXEC";
            Map<String, Object> message_payload = new HashMap();
            message_payload.put("action", "listensrc");
            message_payload.put("action_stunnel_listen_port", "9000");
            message_payload.put("action_stunnel_id", tunnelId);
            Map<String, String> responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

            System.out.println(responce);

            message_payload.clear();
            message_payload.put("action", "listendst");
            message_payload.put("action_stunnel_id", tunnelId);
            message_payload.put("action_stunnel_dst_host", "128.163.189.58");
            message_payload.put("action_stunnel_dst_port", "80");

            responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

            System.out.println(responce);

             */


            /*
            while(true) {
                String message_event_type = "EXEC";
                Map<String, Object> message_payload = new HashMap();
                message_payload.put("action", "test");
                message_payload.put("action_stunnel_id", tunnelId);
                Map<String, String> responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);
                //System.out.println(responce);
                Thread.sleep(5000);
            }

             */


    //message_payload.clear();
    //message_payload.put("action", "listendst");
    //message_payload.put("action_stunnel_id", "t0");
    //message_payload.put("action_stunnel_listen_port", "9000");
    //responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

    //System.out.println(responce);

    //client.close();

            /*
            //Location of plugin
            String pipelineName = "FileRepoExample";
            String repo_name_1 = "test_repo_1";
            String repo_name_2 = "test_repo_2";
            //String repo_path_1 = "/data/1";
            //String repo_path_2 = "/data/2";
            String repo_path_1 = "/Users/cody/IdeaProjects/agent/data/1";
            String repo_path_2 = "/Users/cody/IdeaProjects/agent/data/2";

            //deploy a pair of filerepo plugins as an application
            String fileRepoAppId = testers.deployFileRepo(pipelineName, repo_name_1, repo_path_1, repo_name_2, repo_path_2);

            class RepoPrinter implements OnMessageCallback {

                int lastTransferId = -1;
                @Override
                public void onMessage(String msg) {

                    Map<String,String> repoMap = client.messaging.getMapFromString(msg);
                    String repoRegionId = repoMap.get("repo_region_id");
                    String repoAgentId = repoMap.get("repo_agent_id");
                    String repoPluginId = repoMap.get("repo_plugin_id");
                    String repoName = repoMap.get("filerepo_name");
                    int incomingTransferId = Integer.parseInt(repoMap.get("transfer_id"));
                    if(lastTransferId != incomingTransferId){
                        System.out.println("UPDATED: " + repoMap);
                        lastTransferId = incomingTransferId;

                        String message_event_type = "EXEC";
                        Map<String, Object> message_payload = new HashMap<>();
                        message_payload.put("action", "getrepofilelist");
                        message_payload.put("repo_name",repoName);
                        Map<String,String> reply = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, repoRegionId, repoAgentId, repoPluginId);
                        String blah = client.messaging.getCompressedParam(reply.get("repofilelist"));
                        System.out.println("Repo file list: \n" + blah);

                    }

                }
            }

            String queryStringRepo1 = "filerepo_name='" + repo_name_1 + "' AND broadcast";
            DataPlaneInterface dataPlaneRepo1 = client.getDataPlane(queryStringRepo1, new RepoPrinter());
            dataPlaneRepo1.start();

            String queryStringRepo2 = "filerepo_name='" + repo_name_2 + "' AND broadcast";
            DataPlaneInterface dataPlaneRepo2 = client.getDataPlane(queryStringRepo2, new RepoPrinter());
            dataPlaneRepo2.start();




            while(true) {
                Thread.sleep(1000);
            }

             */

}
