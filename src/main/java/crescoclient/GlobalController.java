package crescoclient;

import com.google.gson.Gson;
import io.cresco.library.app.gPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalController {

    private Gson gson;
    private Utils utils;
    private Messaging messaging;

    public GlobalController(Messaging messaging) {
        gson = new Gson();
        this.messaging = messaging;
        utils = new Utils();
    }

    public List<Map<String,String>> get_pipeline_list(){
        List<Map<String,String>> responce = null;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getgpipelinestatus");

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String pluginlistStr = messaging.getCompressedParam(reply.get("pipelineinfo"));
            Map<String,List<Map<String,String>>> pluginlist = messaging.getMapListMapFromString(pluginlistStr);
            responce = pluginlist.get("pipelines");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  responce;
    }

    public gPayload get_pipeline_info(String pipeline_id) {
        gPayload gpay = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getgpipeline");
            message_payload.put("action_pipelineid",pipeline_id);

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String pipelineInfoStr = messaging.getCompressedParam(reply.get("gpipeline"));
            gpay = gson.fromJson(pipelineInfoStr,gPayload.class);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return gpay;
    }

    public int get_pipeline_status(String pipeline_id) {
        int status = -1;
        try {

            gPayload gpay = get_pipeline_info(pipeline_id);
            if(gpay != null) {
                status = Integer.parseInt(gpay.status_code);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return status;
    }

    public Map<String,List<Map<String,String>>> get_agent_list(String dst_region) {
        Map<String,List<Map<String,String>>> agentlist = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","listagents");
            if(dst_region != null) {
                message_payload.put("action_region",dst_region);
            }

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String agentlistStr = messaging.getCompressedParam(reply.get("agentslist"));
            agentlist = messaging.getMapListMapFromString(agentlistStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return agentlist;
    }

    public Map<String,List<Map<String,String>>> get_agent_resource(String dst_region, String dst_agent) {
        Map<String,List<Map<String,String>>> agentlist = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","resourceinfo");
            if(dst_region != null) {
                message_payload.put("action_region",dst_region);
                if(dst_agent != null) {
                    message_payload.put("action_agent",dst_agent);
                }
            }

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String resourceInfoStr = messaging.getCompressedParam(reply.get("resourceinfo"));
            agentlist = messaging.getMapListMapFromString(resourceInfoStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return agentlist;
    }

    public Map<String,List<Map<String,String>>> get_plugin_list() {
        Map<String,List<Map<String,String>>> agentlist = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","listplugins");


            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String pluginListStr = messaging.getCompressedParam(reply.get("pluginslist"));
            Map<String,List<Map<String,String>>> pluginlist = messaging.getMapListMapFromString(pluginListStr);

            Map<String,String> repo_plugin = null;
            for(Map<String,String> plugin_info : pluginlist.get("plugins")) {
                if(plugin_info.get("pluginname").equals("io.cresco.repo")) {
                    repo_plugin = plugin_info;
                }
            }

            if(repo_plugin != null) {

                System.out.println("\n\n");
                message_payload.put("action","repolist");
                Map<String,String> repo_reply = messaging.global_plugin_msgevent(true, message_event_type, message_payload, repo_plugin.get("region"), repo_plugin.get("agent"), repo_plugin.get("name"));
                String repoListStr = messaging.getCompressedParam(repo_reply.get("repolist"));
                agentlist = messaging.getMapListMapFromString(repoListStr);

            } else {
                System.out.println("NO PLUGIN REPO FOUND");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return agentlist;
    }

    public Map<String,String> upload_plugin_global(String jar_file_path) {
        Map<String,String> responce = null;
        try {

            Path jar_file = Paths.get(jar_file_path);
            if(jar_file.toFile().exists()) {
                Map<String, Object> configparams = utils.get_jar_info(jar_file_path);

                String message_event_type = "CONFIG";
                Map<String,Object> message_payload = new HashMap<>();
                message_payload.put("action","savetorepo");
                String json_configparams = gson.toJson(configparams);
                message_payload.put("configparams",messaging.setCompressedParam(json_configparams));
                message_payload.put("jardata",messaging.setCompressedDataParam(Files.readAllBytes(jar_file)));

                responce = messaging.global_controller_msgevent(true,message_event_type,message_payload);

            } else {
                System.out.println("upload_jar_info: file does not exist: " + jar_file_path);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,List<Map<String,String>>> get_region_resources(String dst_region) {

        Map<String,List<Map<String,String>>> regionlist = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","resourceinfo");
            message_payload.put("action_region",dst_region);

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String agentlistStr = messaging.getCompressedParam(reply.get("resourceinfo"));
            regionlist = messaging.getMapListMapFromString(agentlistStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return regionlist;
    }

    public Map<String,List<Map<String,String>>> get_region_list() {
        Map<String,List<Map<String,String>>> regionlist = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","listregions");

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String agentlistStr = messaging.getCompressedParam(reply.get("regionslist"));
            regionlist = messaging.getMapListMapFromString(agentlistStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return regionlist;
    }


}
