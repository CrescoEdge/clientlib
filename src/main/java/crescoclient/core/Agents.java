package crescoclient.core;

import com.google.gson.Gson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Agent-level operations ({@code client.agents}): controller status, plugin lifecycle on one agent
 * (add/remove/list/status/upload), agent info and logs, and CEP queries. Every call is a MsgEvent to the
 * target agent's controller, routed through the global controller ({@code global_agent_msgevent}).
 */
public class Agents {

    private Gson gson;
    private Utils utils;
    private Messaging messaging;

    /**
     * Create the agents submodule. {@link crescoclient.CrescoClient} builds this for you as {@code client.agents}.
     *
     * @param messaging the client's messaging submodule
     */
    public Agents(Messaging messaging) {
        utils = new Utils();
        gson = new Gson();
        this.messaging = messaging;
    }

    /**
     * Whether the controller on an agent is active (EXEC {@code iscontrolleractive}).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @return true if the agent answered that its controller is active; false otherwise (including no reply)
     */
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

    /**
     * Controller status of an agent (EXEC {@code getcontrollerstatus}).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @return the controller status string, or null if the agent did not answer
     */
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

    /**
     * Add a complex-event-processing (Siddhi) query on an agent (CONFIG {@code cepadd}). Events arriving on
     * {@code input_stream} are evaluated by {@code query} and results are published on {@code output_stream}.
     *
     * @param input_stream       name of the input stream
     * @param input_stream_desc  schema of the input stream
     * @param output_stream      name of the output stream
     * @param output_stream_desc schema of the output stream
     * @param query              the CEP query
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @return the agent's reply, or null on failure
     */
    public Map<String,String> cepadd(String input_stream, String input_stream_desc, String output_stream, String output_stream_desc, String query, String dst_region, String dst_agent){
        Map<String,String> response = null;
        try {

            Map<String,Object> cepparams  = new HashMap<>();
            cepparams.put("input_stream",input_stream);
            cepparams.put("input_stream_desc",input_stream_desc);
            cepparams.put("output_stream",output_stream);
            cepparams.put("output_stream_desc",output_stream_desc);
            cepparams.put("query",query);

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","cepadd");
            String json_cepparams = gson.toJson(cepparams);
            // server reads getCompressedParam("cepparams") (AgentExecutor.cepAdd); the old
            // "configparams" key silently delivered null. Caught by the conformance suite.
            message_payload.put("cepparams",messaging.setCompressedParam(json_cepparams));

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  response;
    }

    /**
     * Stop and remove a plugin from an agent (CONFIG {@code pluginremove}).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @param plugin_id  plugin id on that agent (from {@link #list_plugin_agent} or {@link GlobalController#find_plugin})
     * @return the agent's reply, or null on failure
     */
    public Map<String,String> remove_plugin_agent(String dst_region, String dst_agent, String plugin_id){
        Map<String,String> response = null;
        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","pluginremove");
            message_payload.put("pluginid",plugin_id);

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  response;
    }

    /**
     * Load and start a plugin on an agent without a CADL pipeline (CONFIG {@code pluginadd}).
     *
     * <p>{@code configparams} must contain {@code pluginname} (the bundle symbolic name) and identify the jar:
     * {@code jarfile} as a path on the agent (e.g. the {@code jar_file_path} returned by {@link #upload_plugin_agent}) or
     * the name of a jar embedded in the agent (e.g. {@code stunnel.jar}), optionally with {@code version} and
     * {@code md5}; with only a name the agent looks in its local cache and then the plugin repositories. Any other
     * keys become the plugin's configuration.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @param configparams plugin configuration (see above)
     * @param edges        optional pipeline edges for the plugin, or null
     * @return the reply: {@code pluginid} and {@code status_code} ("10" = running) on success, or null on failure
     */
    public Map<String,String> add_plugin_agent(String dst_region, String dst_agent, Map<String,String> configparams, Map<String,String> edges){
        Map<String,String> response = null;
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

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  response;
    }

    /**
     * Ask an agent to pull a plugin into its local cache from the plugin repositories (CONFIG
     * {@code pluginrepopull}). The plugin is identified by the name/version/hash read from the local jar's manifest;
     * the jar itself is not sent.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @param jar_file_path local path of the plugin jar (used for its manifest and hash)
     * @return the agent's reply, or null if the file does not exist or the call failed
     */
    public Map<String,String> repo_pull_plugin_agent (String dst_region, String dst_agent, String jar_file_path) {
        Map<String,String> response = null;
        try {

            Path jar_file = Paths.get(jar_file_path);
            if(jar_file.toFile().exists()) {
                Map<String, Object> configparams = utils.get_jar_info(jar_file_path);

                String message_event_type = "CONFIG";
                Map<String,Object> message_payload = new HashMap<>();
                message_payload.put("action","pluginrepopull");
                String json_configparams = gson.toJson(configparams);
                message_payload.put("configparams",messaging.setCompressedParam(json_configparams));

                response = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

            } else {
                System.out.println("upload_jar_info: file does not exist: " + jar_file_path);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Upload a plugin jar to an agent's local plugin cache (CONFIG {@code pluginupload}). The jar is not started;
     * follow with {@link #add_plugin_agent} using the returned {@code jar_file_path} as {@code jarfile}.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @param jar_file_path local path of the plugin jar
     * @return the reply ({@code jar_file_path} on the agent, {@code is_updated}), or null if the file does not exist
     *         or the call failed
     */
    public Map<String,String> upload_plugin_agent(String dst_region, String dst_agent, String jar_file_path) {
        Map<String,String> response = null;
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

                // pluginupload is an AgentExecutor action: route to the target agent, not the global
                // controller. The old global_controller route ignored dst_region/dst_agent (never
                // reached the agent). Caught by the conformance suite vs the Python client.
                response = messaging.global_agent_msgevent(true,message_event_type,message_payload,dst_region,dst_agent);

            } else {
                System.out.println("upload_jar_info: file does not exist: " + jar_file_path);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Ask an agent to update its controller from a jar (CONFIG {@code controllerupdate}, fire-and-forget).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @param jar_file_path path of the controller jar, as the agent sees it
     */
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

    /**
     * Run a broadcast discovery from an agent and return what it found (EXEC {@code getbroadcastdiscovery}).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     * @return the agent's reply, or null on failure
     */
    public Map<String,String> get_broadcast_discovery(String dst_region, String dst_agent) {
        Map<String,String> response = null;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getbroadcastdiscovery");

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * List plugins on an agent.
     *
     * @param dst_region Destination region
     * @param dst_agent Destination agent
     * @return List of plugins
     */
    public java.util.List<Map<String,String>> list_plugin_agent(String dst_region, String dst_agent) {
        java.util.List<Map<String,String>> pluginlist = null;
        try {
            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","pluginlist");

            Map<String,String> reply = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);
            String pluginlistStr = messaging.getCompressedParam(reply.get("plugin_list"));
            pluginlist = messaging.getListMapFromString(pluginlistStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return pluginlist;
    }

    /**
     * Get plugin status.
     *
     * @param dst_region Destination region
     * @param dst_agent Destination agent
     * @param plugin_id Plugin ID
     * @return Plugin status information
     */
    public Map<String,String> status_plugin_agent(String dst_region, String dst_agent, String plugin_id) {
        Map<String,String> response = null;
        try {
            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","pluginstatus");
            message_payload.put("pluginid",plugin_id);

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Get agent information.
     *
     * @param dst_region Destination region
     * @param dst_agent Destination agent
     * @return Agent information
     */
    public Map<String,String> get_agent_info(String dst_region, String dst_agent) {
        Map<String,String> response = null;
        try {
            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getagentinfo");

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Get agent logs.
     *
     * @param dst_region Destination region
     * @param dst_agent Destination agent
     * @return Agent logs
     */
    public Map<String,String> get_agent_log(String dst_region, String dst_agent) {
        Map<String,String> response = null;
        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getlog");
            message_payload.put("action_inmessage","true");

            response = messaging.global_agent_msgevent(true,message_event_type,message_payload, dst_region, dst_agent);


        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

}
