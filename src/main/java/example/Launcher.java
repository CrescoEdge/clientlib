package example;

import com.google.gson.Gson;
import crescoclient.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Launcher {


    public static void main(String[] args) throws IOException {


        String host = "localhost";
        int port = 8282;
        String service_key = "1234";

        String dst_region = "global-region";
        String dst_agent = "global-controller";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();
        //client.messaging.sendsomething();
        client.connected();
        //System.out.println(client.globalcontroller.get_agent_list(null));


        class LogPrinter implements LogStreamerCallback {
            @Override
            public void onMessage(String msg) {
                System.out.println(msg);
            }
        }

        LogStreamer ls = client.getLogStreamer();
        ls.connect();

        ls.update_config(dst_region,dst_agent);


        class DPPrinter implements DataPlaneCallback {
            @Override
            public void onMessage(String msg) {
                System.out.println(msg);
            }
        }


        String identKey = "stream_name";
        String identId = "1234";
        //String streamQuery = "stream_name='" + identId + "'";
        Map<String,String> configDB = new HashMap<>();
        configDB.put("ident_key",identKey);
        configDB.put("ident_id",identId);
        //configDB.put("stream_query",identKey + "='" + identId + "' and type='" + "outgoing" + "'");
        configDB.put("io_type_key","type");
        configDB.put("output_id","output");
        configDB.put("input_id","output");
        Gson gson = new Gson();

        String jsonConfig = gson.toJson(configDB);

        /*
         worker_filerepo_name = 'autopathworker'
        deident_filerepo_name = 'deident'
        converted_filerepo_name = 'converted'
        metadata_filerepo_name = 'metadata'

        # describe the dataplane query allowing python client to listen in on filerepo communications
        # this is not needed, but lets us see what is being communicated by the plugins
        worker_stream_query = "filerepo_name='" + worker_filerepo_name + "' AND broadcast"
        deident_stream_query = "filerepo_name='" + deident_filerepo_name + "' AND broadcast"
        converted_stream_query = "filerepo_name='" + converted_filerepo_name + "' AND broadcast"
        metadata_stream_query = "filerepo_name='" + metadata_filerepo_name + "' AND broadcast"
         */
        //String metadata_filerepo_name = "metadata";
        //String streamQuery  = "filerepo_name='" + metadata_filerepo_name + "' AND broadcast";
        //String streamName = "toots";
        //String streamQuery = "stream_name='" + streamName + "'";
        //String queryString = identKey + "='" + identId + "' and type='" + "outgoing" + "'";
        String queryString = identKey + "='" + identId;
        DataPlane dataPlane = client.getDataPlane(new DPPrinter(),jsonConfig);
        dataPlane.connect();


        int count = 25;

        for(int i=0; i<count; i++) {
            try {
                Thread.sleep(1000);
                System.out.println("count: " + i);
                //System.out.println("Incoming: " + ls.recv());
                //dataPlane.send(String.valueOf(i));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("EXIT");
        //ls.close();

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

        Map<String, List<Map<String,String>>> agentsList = client.globalcontroller.get_agent_list(dst_region);
        System.out.println(agentsList);

        Map<String,List<Map<String,String>>> agentResources = client.globalcontroller.get_agent_resource(dst_region, dst_agent);
        System.out.println(agentResources);

        Map<String,List<Map<String,String>>> pluginResources = client.globalcontroller.get_plugin_list();
        System.out.println(pluginResources);

        Map<String, List<Map<String,String>>> regionList = client.globalcontroller.get_region_resources(dst_region);
        System.out.println(regionList);

         Map<String, List<Map<String,String>>> regionList = client.globalcontroller.get_region_list();
        System.out.println(regionList);

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

    }

}
