package crescoclient.core;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side helper for the io.cresco.stunnel plugin: build and manage secure TCP
 * tunnels across the Cresco mesh. Mirrors the pycrescolib / cppcrescolib stunnel
 * submodule. Plugin ids are resolved by name via GlobalController.find_plugin (the
 * reliable global-side path), so callers never scrape logs for a plugin id.
 */
public class Stunnel {

    public static final String STUNNEL_PLUGIN_NAME = "io.cresco.stunnel";

    private final Messaging messaging;
    private final GlobalController globalcontroller;
    private final Gson gson;

    public Stunnel(Messaging messaging, GlobalController globalcontroller) {
        this.messaging = messaging;
        this.globalcontroller = globalcontroller;
        this.gson = new Gson();
    }

    /** Resolve the stunnel plugin_id loaded on an agent (or null), via the global controller. */
    public String find_plugin(String region, String agent) {
        return globalcontroller.find_plugin(region, agent, STUNNEL_PLUGIN_NAME);
    }

    public Map<String,String> create_tunnel(String stunnel_id, String src_region, String src_agent, String src_port,
                                            String dst_region, String dst_agent, String dst_host, String dst_port,
                                            String buffer_size) {
        return create_tunnel(stunnel_id, src_region, src_agent, src_port, dst_region, dst_agent, dst_host, dst_port,
                buffer_size, null, null);
    }

    /**
     * Create a tunnel: a listener on src_port at the source agent forwards to dst_host:dst_port
     * reachable from the destination agent. Plugin ids are auto-resolved by name when null.
     * Returns the plugin reply (contains status / stunnel_config), or null on failure.
     */
    public Map<String,String> create_tunnel(String stunnel_id, String src_region, String src_agent, String src_port,
                                            String dst_region, String dst_agent, String dst_host, String dst_port,
                                            String buffer_size, String src_plugin_id, String dst_plugin_id) {
        try {
            if (src_plugin_id == null) src_plugin_id = find_plugin(src_region, src_agent);
            if (dst_plugin_id == null) dst_plugin_id = find_plugin(dst_region, dst_agent);
            if (src_plugin_id == null || dst_plugin_id == null) {
                System.err.println("stunnel plugin not found (src=" + src_plugin_id + ", dst=" + dst_plugin_id
                        + "); ensure " + STUNNEL_PLUGIN_NAME + " is loaded on both agents");
                return null;
            }
            Map<String,Object> p = new HashMap<>();
            p.put("action", "configsrctunnel");
            p.put("action_stunnel_id", stunnel_id);
            p.put("action_src_port", src_port);
            p.put("action_dst_host", dst_host);
            p.put("action_dst_port", dst_port);
            p.put("action_dst_region", dst_region);
            p.put("action_dst_agent", dst_agent);
            p.put("action_dst_plugin", dst_plugin_id);
            p.put("action_buffer_size", buffer_size);
            return messaging.global_plugin_msgevent(true, "CONFIG", p, src_region, src_agent, src_plugin_id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public List<Map<String,String>> get_tunnel_list(String region, String agent) {
        return get_tunnel_list(region, agent, null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String,String>> get_tunnel_list(String region, String agent, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", "listtunnels");
            Map<String,String> reply = messaging.global_plugin_msgevent(true, "EXEC", p, region, agent, plugin_id);
            if (reply != null && reply.containsKey("tunnels")) {
                return gson.fromJson(reply.get("tunnels"), List.class);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String get_tunnel_status(String region, String agent, String stunnel_id) {
        return get_tunnel_status(region, agent, stunnel_id, null);
    }

    public String get_tunnel_status(String region, String agent, String stunnel_id, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", "gettunnelstatus");
            p.put("action_stunnel_id", stunnel_id);
            Map<String,String> reply = messaging.global_plugin_msgevent(true, "EXEC", p, region, agent, plugin_id);
            return reply != null ? reply.get("tunnel_status") : null;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public String get_tunnel_config(String region, String agent, String stunnel_id) {
        return get_tunnel_config(region, agent, stunnel_id, null);
    }

    public String get_tunnel_config(String region, String agent, String stunnel_id, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", "gettunnelconfig");
            p.put("action_stunnel_id", stunnel_id);
            Map<String,String> reply = messaging.global_plugin_msgevent(true, "EXEC", p, region, agent, plugin_id);
            return reply != null ? reply.get("tunnel_config") : null;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public Map<String,String> remove_src_tunnel(String region, String agent, String stunnel_id, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", "removesrctunnel");
            p.put("action_stunnel_id", stunnel_id);
            return messaging.global_plugin_msgevent(true, "CONFIG", p, region, agent, plugin_id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public Map<String,String> remove_dst_tunnel(String region, String agent, String stunnel_id, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", "removedsttunnel");
            p.put("action_stunnel_id", stunnel_id);
            return messaging.global_plugin_msgevent(true, "CONFIG", p, region, agent, plugin_id);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /** Remove both ends of a tunnel. Plugin ids are auto-resolved when null. */
    public Map<String,Object> remove_tunnel(String stunnel_id, String src_region, String src_agent,
                                            String dst_region, String dst_agent) {
        Map<String,Object> out = new HashMap<>();
        out.put("stunnel_id", stunnel_id);
        Map<String,String> srcRemoval = remove_src_tunnel(src_region, src_agent, stunnel_id, null);
        Map<String,String> dstRemoval = remove_dst_tunnel(dst_region, dst_agent, stunnel_id, null);
        out.put("src_removal", srcRemoval);
        out.put("dst_removal", dstRemoval);
        out.put("fully_removed", srcRemoval != null && dstRemoval != null);
        return out;
    }
}
