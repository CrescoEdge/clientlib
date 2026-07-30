package crescoclient.core;

import com.google.gson.Gson;
import crescoclient.core.Messaging;
import crescoclient.core.Utils;
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

    public String get_pipeline_id_by_name(String pipelineName) {

        String pipelineId = null;
        try {
            List<Map<String, String>> pipelineList = get_pipeline_list();
            for (Map<String, String> pipeline : pipelineList) {
                if (pipeline.get("pipeline_name").equals(pipelineName)) {
                    pipelineId = pipeline.get("pipeline_id");
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return pipelineId;
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

    public List<Map<String,String>> list_plugins(String dst_region, String dst_agent) {
        // Reliable global-side plugin listing for an agent (global_controller_msgevent
        // 'listplugins'), unlike Agents.list_plugin_agent which addresses the agent
        // directly and can time out on edge nodes. Each record has 'pluginname' and
        // 'name' (the plugin_id).
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","listplugins");
            message_payload.put("action_region", dst_region);
            message_payload.put("action_agent", dst_agent);

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String pluginListStr = messaging.getCompressedParam(reply.get("pluginslist"));
            Map<String,List<Map<String,String>>> pluginlist = messaging.getMapListMapFromString(pluginListStr);
            return pluginlist.get("plugins");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String find_plugin(String dst_region, String dst_agent, String pluginname) {
        // Resolve a plugin's id by name on an agent, via the global controller (reliable;
        // does not depend on the per-agent RPC path). Returns the plugin_id, or null.
        List<Map<String,String>> plugins = list_plugins(dst_region, dst_agent);
        if(plugins != null) {
            for(Map<String,String> p : plugins) {
                if(pluginname.equals(p.get("pluginname"))) {
                    return p.get("name");
                }
            }
        }
        return null;
    }

    public Map<String,List<Map<String,String>>> get_plugin_repo_list() {

        Map<String,List<Map<String,String>>> pluginRepoList = null;
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","listpluginsrepo");

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            String pluginRepolistStr = messaging.getCompressedParam(reply.get("listpluginsrepo"));
            pluginRepoList = messaging.getMapListMapFromString(pluginRepolistStr);

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return pluginRepoList;
    }



    public Map<String,List<Map<String,String>>> get_agent_resources(String dst_region, String dst_agent) {
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

    /**
     * B-2 unified metrics: pull the fabric's unified metric inventory (controller Micrometer groups +
     * every plugin's getmetrics + optional resource summary) as a JSON string.
     *
     * @param dst_region      if non-null with dst_agent, target that agent's controller directly (node scope);
     *                        otherwise the query goes to the global controller.
     * @param dst_agent       see dst_region.
     * @param scope           "node" (this controller), "region" (its region), or "global" (whole mesh).
     * @param include_plugins include each node's plugin metrics (default on server side).
     * @param include_resource include the cpu/mem/disk resource summary (adds sysinfo RPC latency).
     * @return the unified inventory JSON, or null on failure.
     */
    public String get_metric_inventory(String dst_region, String dst_agent, String scope,
                                       boolean include_plugins, boolean include_resource) {
        try {
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getmetricinventory");
            message_payload.put("action_scope", scope != null ? scope : "node");
            message_payload.put("action_include_plugins", String.valueOf(include_plugins));
            message_payload.put("action_include_resource", String.valueOf(include_resource));

            Map<String,String> reply;
            if (dst_region != null && dst_agent != null) {
                reply = messaging.global_agent_msgevent(true, "EXEC", message_payload, dst_region, dst_agent);
            } else {
                reply = messaging.global_controller_msgevent(true, "EXEC", message_payload);
            }
            return reply != null ? reply.get("metricinventory") : null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /** Convenience: whole-mesh unified inventory (scope=global, plugins on, resource on). */
    public String get_metric_inventory() {
        return get_metric_inventory(null, null, "global", true, true);
    }

    /**
     * Pull the fabric's self-describing capability inventory (LLM tool catalog) as JSON. Aggregates each
     * node's controller-tier actions + every plugin's message actions (via getcapabilities). Only MsgEvent
     * actions are callable tools; the optional OSGi surface is informational.
     *
     * @param scope           "node", "region", or "global".
     * @param include_plugins include each node's plugin capability docs.
     * @param include_osgi    include the OSGi Export-Package + registered service surface (informational).
     * @return the capability inventory JSON, or null on failure.
     */
    public String get_capability_inventory(String scope, boolean include_plugins, boolean include_osgi) {
        try {
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getcapabilityinventory");
            message_payload.put("action_scope", scope != null ? scope : "node");
            message_payload.put("action_include_plugins", String.valueOf(include_plugins));
            message_payload.put("action_include_osgi", String.valueOf(include_osgi));
            Map<String,String> reply = messaging.global_controller_msgevent(true, "EXEC", message_payload);
            return reply != null ? reply.get("capabilityinventory") : null;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    /** Convenience: whole-mesh capability catalog (scope=global, plugins on, MsgEvent-only). */
    public String get_capability_inventory() {
        return get_capability_inventory("global", true, false);
    }

    public Map<String,List<Map<String,String>>> get_repo_plugins() {
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


    public Map<String, String> submit_pipeline(Map<String, Object> cadl, String tenantId) {

        Map<String, String> reply = null;

        try {

            String json_cadl = gson.toJson(cadl);

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","gpipelinesubmit");
            message_payload.put("action_tenantid",tenantId);
            message_payload.put("action_gpipeline", messaging.setCompressedParam(json_cadl));

            reply = messaging.global_controller_msgevent(true, message_event_type, message_payload);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return reply;
    }

    public Map<String, String> submit_pipeline(String json_cadl, String tenantId) {

        Map<String, String> reply = null;

        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","gpipelinesubmit");
            message_payload.put("action_tenantid",tenantId);
            message_payload.put("action_gpipeline", messaging.setCompressedParam(json_cadl));

            reply = messaging.global_controller_msgevent(true, message_event_type, message_payload);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return reply;
    }

    public Map<String, String> get_pipeline_is_assignment_info(String inode_id, String resource_id) {

        Map<String, String> reply = null;

        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getisassignmentinfo");
            message_payload.put("action_inodeid", inode_id);
            message_payload.put("action_resourceid", resource_id);
            reply = messaging.global_controller_msgevent(true, message_event_type, message_payload);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return reply;
    }

    public Map<String, String> get_pipeline_export(String pipeline_id) {

        Map<String, String> reply = null;

        try {

            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","getgpipelineexport");
            message_payload.put("action_pipelineid", pipeline_id);
            reply = messaging.global_controller_msgevent(true, message_event_type, message_payload);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return reply;
    }
    
    public boolean remove_pipeline(String pipeline_id) {

        boolean isRemoved = false;

        try {
            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","gpipelineremove");
            message_payload.put("action_pipelineid",pipeline_id);

            Map<String,String> reply = messaging.global_controller_msgevent(true,message_event_type,message_payload);
            isRemoved = Boolean.parseBoolean(reply.get("success"));

        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return isRemoved;
    }

}
