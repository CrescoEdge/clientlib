package crescoclient;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Agents {

    private Gson gson;
    private Utils utils;
    private Messaging messaging;

    public Agents(Messaging messaging) {
        utils = new Utils();
        gson = new Gson();
        this.messaging = messaging;
    }

    public boolean is_controller_active(String dst_region, String dst_agent) {
        boolean isActive = false;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","iscontrolleractive");

            Map<String,String> reply = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);
            if(reply.containsKey("is_controller_active")) {
                isActive = Boolean.parseBoolean(reply.get("is_controller_active"));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return isActive;
    }

    public String get_controller_status(String dst_region, String dst_agent) {
        String controllerStatus = null;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getcontrollerstatus");

            Map<String,String> reply = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);
            if(reply.containsKey("controller_status")) {
                controllerStatus = reply.get("controller_status");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return controllerStatus;
    }

    public Map<String,String> cepadd(String input_stream, String input_stream_dec, String output_stream, String output_stream_desc, String query, String dst_region, String dst_agent){
        Map<String,String> responce = null;
        try {

            Map<String,Object> cepparams  = new HashMap<>();
            cepparams.put("input_stream",input_stream);
            cepparams.put("input_stream_desc",input_stream_dec);
            cepparams.put("output_stream",output_stream);
            cepparams.put("output_stream_desc",output_stream_desc);
            cepparams.put("query",query);

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","cepadd");
            String json_cepparams = gson.toJson(cepparams);
            message_payload.put("configparams",messaging.setCompressedParam(json_cepparams));

            responce = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  responce;
    }

    public Map<String,String> remove_plugin_agent(String dst_region, String dst_agent, String plugin_id){
        Map<String,String> responce = null;
        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","pluginremove");
            message_payload.put("pluginid",plugin_id);

            responce = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  responce;
    }

    public Map<String,String> add_plugin_agent(String dst_region, String dst_agent, Map<String,String> configparams, Map<String,String> edges){
        Map<String,String> responce = null;
        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","pluginadd");
            String json_configparams = gson.toJson(configparams);
            message_payload.put("configparams",messaging.setCompressedParam(json_configparams));
            if(edges != null) {
                String json_edgeparams = gson.toJson(edges);
                message_payload.put("edges",messaging.setCompressedParam(json_edgeparams));
            }

            responce = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  responce;
    }

    public Map<String,String> repo_pull_plugin_agent (String dst_region, String dst_agent, String jar_file_path) {
        Map<String,String> responce = null;
        try {

            Path jar_file = Paths.get(jar_file_path);
            if(jar_file.toFile().exists()) {
                Map<String, Object> configparams = utils.get_jar_info(jar_file_path);

                String message_event_type = "CONFIG";
                Map<String,Object> message_payload = new HashMap<>();
                message_payload.put("action","pluginrepopull");
                String json_configparams = gson.toJson(configparams);
                message_payload.put("configparams",messaging.setCompressedParam(json_configparams));

                responce = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

            } else {
                System.out.println("upload_jar_info: file does not exist: " + jar_file_path);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> upload_plugin_agent(String dst_region, String dst_agent, String jar_file_path) {
        Map<String,String> responce = null;
        try {

            Path jar_file = Paths.get(jar_file_path);
            if(jar_file.toFile().exists()) {
                Map<String, Object> configparams = utils.get_jar_info(jar_file_path);

                String message_event_type = "CONFIG";
                Map<String,Object> message_payload = new HashMap<>();
                message_payload.put("action","pluginupload");
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

    public void update_plugin_agent(String dst_region, String dst_agent, String jar_file_path) {
        try {

            Path jar_file = Paths.get(jar_file_path);
            if(jar_file.toFile().exists()) {
                Map<String, Object> configparams = utils.get_jar_info(jar_file_path);

                String message_event_type = "CONFIG";
                Map<String,Object> message_payload = new HashMap<>();
                message_payload.put("action","controllerupdate");
                message_payload.put("jar_file_path",jar_file_path);

                messaging.global_agent_msgevent(false,message_event_type,message_payload, dst_region, dst_agent);

            } else {
                System.out.println("upload_jar_info: file does not exist: " + jar_file_path);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public Map<String,String> get_broadcast_discovery(String dst_region, String dst_agent) {
        Map<String,String> responce = null;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getbroadcastdiscovery");

            responce = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

}
