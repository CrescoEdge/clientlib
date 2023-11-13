package example;

import com.google.gson.Gson;
import crescoclient.*;
import crescoclient.dataplane.DataPlaneInterface;
import crescoclient.logstreamer.LogStreamerInterface;
import io.cresco.library.app.gPayload;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.BufferingResponseListener;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Launcher {


    public static void main(String[] args) throws Exception {

        String host = "localhost";
        int port = 8282;
        String service_key = "c988701a-5f2a-43ac-b915-156049c5d1ee";

        //String dst_region = "dp";
        //String dst_agent = "agent-c988701a-5f2a-43ac-b915-156049c5d1ee";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();

        if(client.connected()) {

            System.out.println("region: " + client.api.getAPIRegionName());
            System.out.println("agent: " + client.api.getAPIAgentName());
            System.out.println("plugin: " + client.api.getAPIPluginName());

            String dst_region = client.api.getGlobalRegion();
            String dst_agent = client.api.getGlobalAgent();
            System.out.println("global region: " + dst_region + " agent:" + dst_agent);


            Testers testers = new Testers(client);

            //Download this file and put it in the project folder:
            //https://github.com/CrescoEdge/filerepo/releases/download/1.1-SNAPSHOT/filerepo-1.1-SNAPSHOT.jar

            //Location of plugin
            String fileRepoFile = "filerepo-1.1-SNAPSHOT.jar";

            //deploy a pair of filerepo plugins as an application
            Map<String,String> fileRepoDeployResults = testers.deployFileRepo(fileRepoFile);

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

            //client.close();
        } else {
            System.out.println("Could not connect to remote.");
        }
    }

}
