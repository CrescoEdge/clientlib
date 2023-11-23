package example;

import com.google.gson.Gson;
import crescoclient.*;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import io.cresco.library.app.gPayload;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Launcher {

    public static Gson gson;
    public static void main(String[] args) throws Exception {

        gson = new Gson();

        String host = "localhost";
        int port = 8282;
        String service_key = "c988701a-5f2a-43ac-b915-156049c5d1ee";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();

        if(client.connected()) {

            System.out.println("API: region: " + client.api.getAPIRegionName() + " agent: " + client.api.getAPIAgentName() + " plugin: " + client.api.getAPIPluginName());
            String dst_region = client.api.getGlobalRegion();
            String dst_agent = client.api.getGlobalAgent();
            System.out.println("Global Controller: region: " + dst_region + " agent:" + dst_agent);
            System.out.println("---");

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
            Testers testers = new Testers(client);


            String pipelineName = "sTunnelExample";
            //String sTunnelAppId = testers.getPipelineIdByName(pipelineName);
            String sTunnelAppId = testers.deploySTunnel(pipelineName);
            //String sTunnelAppId = "resource-13d93383-8687-4b5c-8325-31e57445bfb3";
            gPayload st = client.globalcontroller.get_pipeline_info(sTunnelAppId);
            //System.out.println(st.nodes.get(0).node_id);
            System.out.println(st.nodes.get(0).params);

            String srcRegionId = st.nodes.get(0).params.get("region_id");
            String srcAgentId = st.nodes.get(0).params.get("agent_id");
            String srcPluginId = st.nodes.get(0).params.get("plugin_id");
            System.out.println("region: " + srcRegionId + " agent: " + srcAgentId + " plugin: " + srcPluginId);

            String tunnelId = "t0-t0-t0-t0-t0";



            /*
            String message_event_type = "EXEC";
            Map<String, Object> message_payload = new HashMap();
            message_payload.put("action", "listensrc");
            message_payload.put("action_stunnel_listen_port", "9000");
            message_payload.put("action_stunnel_id", tunnelId);
            Map<String, String> responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

            System.out.println(responce);
             */

            class RepoPrinter implements OnMessageCallback {

                @Override
                public void onMessage(String msg) {

                    System.out.println("INCOMING FROM DP: " + msg);
                    }

            }

            //String queryStringRepo1 = "filerepo_name='" + tunnelId + "' AND broadcast";
            String queryStringRepo1 = "";
            DataPlaneInterface dataPlaneRepo1 = client.getDataPlane(queryStringRepo1, new RepoPrinter());
            dataPlaneRepo1.start();


            String message_event_type = "CONFIG";
            Map<String, Object> message_payload = new HashMap();
            message_payload.put("action", "configsrctunnel");
            message_payload.put("action_src_port", "9000");

            message_payload.put("action_dst_host", "128.163.189.58");
            message_payload.put("action_dst_port", "80");
            //message_payload.put("action_dst_host", "128.163.202.50");
            //message_payload.put("action_dst_port", "22");
            //message_payload.put("action_dst_host", "localhost");
            //message_payload.put("action_dst_port", "9001");
            message_payload.put("action_dst_region", srcRegionId);
            message_payload.put("action_dst_agent", srcAgentId);
            message_payload.put("action_dst_plugin", srcPluginId);
            Map<String, String> responce = client.messaging.global_plugin_msgevent(true, message_event_type, message_payload, srcRegionId, srcAgentId, srcPluginId);

            System.out.println(responce);

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


            while(true) {
                Thread.sleep(1000);
            }


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

        } else {
            System.out.println("Could not connect to remote.");
        }
    }

}
