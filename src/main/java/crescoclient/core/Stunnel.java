package crescoclient.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side abstraction of the <a href="https://github.com/CrescoEdge/stunnel">io.cresco.stunnel</a>
 * plugin: build and manage secure TCP tunnels across the Cresco mesh without writing a CADL pipeline.
 *
 * <p>A tunnel has two ends. The <b>source</b> stunnel plugin listens on {@code src_port} on the source
 * agent's host; every TCP connection accepted there is carried over the Cresco data plane to the
 * <b>destination</b> stunnel plugin, which opens a matching connection to {@code dst_host:dst_port}
 * (resolved from the destination agent's host). Creating a tunnel is a single CONFIG
 * ({@code configsrctunnel}) sent to the source plugin; the source plugin configures the destination end
 * itself ({@code configdsttunnel}) before it starts listening.
 *
 * <p>Plugin ids are resolved by name through {@link GlobalController#find_plugin} (the reliable,
 * global-side path), so callers address agents by region/agent name only. Every method that takes a
 * trailing {@code plugin_id} argument accepts {@code null} to auto-resolve it; when that lookup itself
 * fails the method throws {@link CrescoQueryException} rather than reporting "plugin not loaded".
 *
 * <p>Wire actions used (all defined in the stunnel plugin's {@code PluginExecutor}):
 * <ul>
 *   <li>CONFIG {@code configsrctunnel}, {@code removesrctunnel}, {@code removedsttunnel}, {@code nettuning}</li>
 *   <li>EXEC {@code listtunnels}, {@code gettunnelstatus}, {@code gettunnelconfig}, {@code tunnelhealthcheck},
 *       {@code getmetrics}</li>
 * </ul>
 * Plugin replies carry {@code status} ("10" success, "9" failure/not found, "400" bad request, "500"
 * internal error) and a human-readable {@code status_desc}.
 *
 * <p>Typical use:
 * <pre>{@code
 * CrescoClient client = new CrescoClient("localhost", 8282, serviceKey);
 * client.connect();
 * client.stunnel.deploy_plugin("global-region", "agent-a");      // load the stunnel plugin if missing
 * client.stunnel.deploy_plugin("global-region", "agent-b");
 * Map<String,String> cfg = client.stunnel.create_tunnel("ssh-tunnel",
 *         "global-region", "agent-a", "2222",                   // listen on agent-a:2222
 *         "global-region", "agent-b", "localhost", "22",        // forward to agent-b's localhost:22
 *         "8192");
 * System.out.println(client.stunnel.get_tunnel_status("global-region", "agent-a", "ssh-tunnel"));  // ACTIVE
 * client.stunnel.remove_tunnel("ssh-tunnel", "global-region", "agent-a", "global-region", "agent-b");
 * }</pre>
 *
 * <p>Mirrors the pycrescolib {@code stunnel} submodule: the shared methods have the same names, send the
 * same wire messages and decode replies the same way.
 */
public class Stunnel {

    /** Bundle-SymbolicName of the stunnel plugin; used to resolve plugin ids by name. */
    public static final String STUNNEL_PLUGIN_NAME = "io.cresco.stunnel";

    /** Path of the stunnel jar embedded in the Cresco agent jar (used by {@link #deploy_plugin(String, String)}). */
    public static final String STUNNEL_EMBEDDED_JAR = "stunnel.jar";

    /** Plugin status code for success. */
    public static final String STATUS_OK = "10";

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type LIST_MAP_TYPE = new TypeToken<List<Map<String, String>>>() {}.getType();

    private final Messaging messaging;
    private final GlobalController globalcontroller;
    private final Agents agents;
    private final Gson gson;

    /**
     * Create the stunnel submodule. {@link crescoclient.CrescoClient} builds this for you as
     * {@code client.stunnel}.
     *
     * @param messaging        the client's messaging submodule (transport for all plugin calls)
     * @param globalcontroller the client's global controller submodule (plugin-id resolution)
     */
    public Stunnel(Messaging messaging, GlobalController globalcontroller) {
        this(messaging, globalcontroller, new Agents(messaging));
    }

    /**
     * Create the stunnel submodule with an explicit agents submodule (used by {@link #deploy_plugin}).
     *
     * @param messaging        the client's messaging submodule
     * @param globalcontroller the client's global controller submodule
     * @param agents           the client's agents submodule
     */
    public Stunnel(Messaging messaging, GlobalController globalcontroller, Agents agents) {
        this.messaging = messaging;
        this.globalcontroller = globalcontroller;
        this.agents = agents;
        this.gson = new Gson();
    }

    // ---------------------------------------------------------------- plugin lifecycle

    /**
     * Resolve the stunnel plugin_id loaded on an agent.
     *
     * @param region region name of the agent
     * @param agent  agent name
     * @return the live plugin_id (e.g. {@code plugin-1a2b...}), or {@code null} if the global answered "not loaded"
     * @throws CrescoQueryException if the global's plugin inventory could not be read
     */
    public String find_plugin(String region, String agent) {
        return globalcontroller.find_plugin(region, agent, STUNNEL_PLUGIN_NAME);
    }

    /**
     * Ensure the stunnel plugin is running on an agent, loading the copy embedded in the agent jar if it
     * is not. No CADL pipeline is involved: this is a direct {@code pluginadd} to the agent.
     *
     * @param region region name of the agent
     * @param agent  agent name
     * @return the plugin_id of the (existing or newly loaded) stunnel plugin, or {@code null} if the add failed
     * @throws CrescoQueryException if the plugin inventory could not be read (nothing was added)
     */
    public String deploy_plugin(String region, String agent) {
        return deploy_plugin(region, agent, null);
    }

    /**
     * Ensure the stunnel plugin is running on an agent. If it is not loaded and {@code jar_file_path} is
     * given, the jar is uploaded to the agent ({@code pluginupload}) and started from there; otherwise the
     * stunnel jar embedded in the agent is started.
     *
     * @param region        region name of the agent
     * @param agent         agent name
     * <p>"Not loaded" is confirmed with the agent itself ({@code pluginlist}) before anything is added: the
     * global's registration state can lag the agent briefly (e.g. right after a global restart, until the agent
     * re-sends its plugin state), and that must not start a second copy. If either inventory read fails the
     * call throws and adds nothing (before {@link CrescoQueryException} existed a failed read looked like
     * "not loaded" and started a duplicate).
     *
     * @param jar_file_path local path of a stunnel plugin jar to upload, or {@code null} for the embedded copy
     * @return the plugin_id of the (existing or newly loaded) stunnel plugin, or {@code null} if the upload/add failed
     * @throws CrescoQueryException if the plugin inventory could not be read (nothing was added; retry later)
     */
    public String deploy_plugin(String region, String agent, String jar_file_path) {
        String existing = find_plugin(region, agent);
        if (existing != null) {
            return existing;
        }
        // the global says "not loaded": confirm with the agent (authoritative for what runs there)
        existing = agents.find_plugin(region, agent, STUNNEL_PLUGIN_NAME);
        if (existing != null) {
            return existing;
        }
        try {
            Map<String, String> configparams = new HashMap<>();
            configparams.put("pluginname", STUNNEL_PLUGIN_NAME);
            if (jar_file_path != null) {
                Map<String, String> upload = agents.upload_plugin_agent(region, agent, jar_file_path);
                if (upload == null || upload.get("jar_file_path") == null) {
                    System.err.println("stunnel deploy_plugin: upload of " + jar_file_path + " to "
                            + region + "/" + agent + " failed: " + upload);
                    return null;
                }
                Map<String, Object> jarInfo = new Utils().get_jar_info(jar_file_path);
                configparams.put("jarfile", upload.get("jar_file_path"));
                if (jarInfo != null) {
                    configparams.put("version", String.valueOf(jarInfo.get("version")));
                    configparams.put("md5", String.valueOf(jarInfo.get("md5")));
                }
            } else {
                configparams.put("jarfile", STUNNEL_EMBEDDED_JAR);
            }
            Map<String, String> added = agents.add_plugin_agent(region, agent, configparams, null);
            return added != null ? added.get("pluginid") : null;
        } catch (CrescoQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // ---------------------------------------------------------------- create

    /**
     * Create a tunnel with the plugin's default watchdog and performance-report settings; plugin ids are
     * auto-resolved.
     *
     * @see #create_tunnel(String, String, String, String, String, String, String, String, String, String, String, String, String)
     */
    public Map<String,String> create_tunnel(String stunnel_id, String src_region, String src_agent, String src_port,
                                            String dst_region, String dst_agent, String dst_host, String dst_port,
                                            String buffer_size) {
        return create_tunnel(stunnel_id, src_region, src_agent, src_port, dst_region, dst_agent, dst_host, dst_port,
                buffer_size, null, null, null, null);
    }

    /**
     * Create a tunnel with explicit (or {@code null} = auto-resolved) plugin ids.
     *
     * @see #create_tunnel(String, String, String, String, String, String, String, String, String, String, String, String, String)
     */
    public Map<String,String> create_tunnel(String stunnel_id, String src_region, String src_agent, String src_port,
                                            String dst_region, String dst_agent, String dst_host, String dst_port,
                                            String buffer_size, String src_plugin_id, String dst_plugin_id) {
        return create_tunnel(stunnel_id, src_region, src_agent, src_port, dst_region, dst_agent, dst_host, dst_port,
                buffer_size, src_plugin_id, dst_plugin_id, null, null);
    }

    /**
     * Create a tunnel: a listener on {@code src_port} at the source agent forwards every accepted TCP
     * connection to {@code dst_host:dst_port} as reachable from the destination agent.
     *
     * <p>The call returns after the destination end is configured and the source listener is started
     * (the plugin does both synchronously). If the destination is not reachable yet the plugin keeps the
     * config and retries in the background; the reply then has {@code status} "9" and
     * {@link #get_tunnel_status} reports {@code RECOVERING} until it comes up.
     *
     * @param stunnel_id              unique tunnel id; {@code null}/empty generates a random UUID
     * @param src_region              region of the source (listening) agent
     * @param src_agent               source agent name
     * @param src_port                TCP port to listen on at the source agent's host
     * @param dst_region              region of the destination agent
     * @param dst_agent               destination agent name
     * @param dst_host                host the destination agent connects to (e.g. {@code localhost})
     * @param dst_port                port the destination agent connects to
     * @param buffer_size             per-read buffer size in bytes ({@code null} = plugin default)
     * @param src_plugin_id           source stunnel plugin id, or {@code null} to resolve by name
     * @param dst_plugin_id           destination stunnel plugin id, or {@code null} to resolve by name
     * @param watchdog_timeout        tunnel liveness-probe timeout in ms ({@code null} = plugin default)
     * @param performance_report_rate performance-report interval in ms ({@code null} = plugin default)
     * @return on success ({@code status} "10") the resolved tunnel config as stored by the plugin
     *         ({@code stunnel_id, src_*, dst_*, buffer_size, ...}); on a plugin-side failure the raw reply
     *         ({@code status}, {@code status_desc}); {@code null} if a plugin could not be resolved or the
     *         call failed
     */
    public Map<String,String> create_tunnel(String stunnel_id, String src_region, String src_agent, String src_port,
                                            String dst_region, String dst_agent, String dst_host, String dst_port,
                                            String buffer_size, String src_plugin_id, String dst_plugin_id,
                                            String watchdog_timeout, String performance_report_rate) {
        try {
            if (stunnel_id == null || stunnel_id.isEmpty()) stunnel_id = UUID.randomUUID().toString();
            if (src_plugin_id == null) src_plugin_id = find_plugin(src_region, src_agent);
            if (dst_plugin_id == null) dst_plugin_id = find_plugin(dst_region, dst_agent);
            if (src_plugin_id == null || dst_plugin_id == null) {
                System.err.println("stunnel plugin not found (src=" + src_plugin_id + ", dst=" + dst_plugin_id
                        + "); ensure " + STUNNEL_PLUGIN_NAME + " is loaded on both agents (see deploy_plugin)");
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
            if (buffer_size != null) p.put("action_buffer_size", buffer_size);
            if (watchdog_timeout != null) p.put("action_watchdog_timeout", watchdog_timeout);
            if (performance_report_rate != null) p.put("action_performance_report_rate", performance_report_rate);
            Map<String,String> reply = messaging.global_plugin_msgevent(true, "CONFIG", p, src_region, src_agent, src_plugin_id);
            if (reply != null && reply.get("stunnel_config") != null) {
                return gson.fromJson(messaging.getCompressedParam(reply.get("stunnel_config")), MAP_TYPE);
            }
            return reply;
        } catch (CrescoQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // ---------------------------------------------------------------- query

    /** List the tunnels hosted on an agent (plugin id auto-resolved). @see #get_tunnel_list(String, String, String) */
    public List<Map<String,String>> get_tunnel_list(String region, String agent) {
        return get_tunnel_list(region, agent, null);
    }

    /**
     * List the tunnels hosted by the stunnel plugin on an agent (both source and destination ends).
     *
     * @param region    region name of the agent
     * @param agent     agent name
     * @param plugin_id stunnel plugin id, or {@code null} to resolve by name
     * @return one {@code {stunnel_id, status}} map per tunnel (status as in {@link #get_tunnel_status});
     *         empty if the plugin is not loaded or the listtunnels call failed
     * @throws CrescoQueryException if the plugin id had to be resolved and the inventory could not be read
     */
    public List<Map<String,String>> get_tunnel_list(String region, String agent, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return new ArrayList<>();
            Map<String,Object> p = new HashMap<>();
            p.put("action", "listtunnels");
            Map<String,String> reply = messaging.global_plugin_msgevent(true, "EXEC", p, region, agent, plugin_id);
            if (reply != null && reply.get("tunnels") != null) {
                return gson.fromJson(reply.get("tunnels"), LIST_MAP_TYPE);
            }
        } catch (CrescoQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new ArrayList<>();
    }

    /** Tunnel status (plugin id auto-resolved). @see #get_tunnel_status(String, String, String, String) */
    public String get_tunnel_status(String region, String agent, String stunnel_id) {
        return get_tunnel_status(region, agent, stunnel_id, null);
    }

    /**
     * Get a tunnel's status on one end.
     *
     * @param region     region name of the agent hosting the tunnel end
     * @param agent      agent name
     * @param stunnel_id tunnel id
     * @param plugin_id  stunnel plugin id, or {@code null} to resolve by name
     * @return {@code ACTIVE}, {@code RECOVERING}, {@code DOWN} or {@code UNKNOWN}; {@code null} if the tunnel
     *         is not configured on that agent or the call failed
     */
    public String get_tunnel_status(String region, String agent, String stunnel_id, String plugin_id) {
        Map<String,String> reply = exec(region, agent, plugin_id, "gettunnelstatus", stunnel_id);
        return reply != null ? reply.get("tunnel_status") : null;
    }

    /** Tunnel config (plugin id auto-resolved). @see #get_tunnel_config(String, String, String, String) */
    public Map<String,String> get_tunnel_config(String region, String agent, String stunnel_id) {
        return get_tunnel_config(region, agent, stunnel_id, null);
    }

    /**
     * Get the config the stunnel plugin persisted for a tunnel (it is restored when the plugin restarts).
     *
     * @param region     region name of the agent hosting the tunnel end
     * @param agent      agent name
     * @param stunnel_id tunnel id
     * @param plugin_id  stunnel plugin id, or {@code null} to resolve by name
     * @return the tunnel config ({@code stunnel_id, src_port, dst_host, dst_port, src_/dst_ region/agent/plugin,
     *         buffer_size, ...}), or {@code null} if not found
     */
    public Map<String,String> get_tunnel_config(String region, String agent, String stunnel_id, String plugin_id) {
        Map<String,String> reply = exec(region, agent, plugin_id, "gettunnelconfig", stunnel_id);
        if (reply != null && reply.get("tunnel_config") != null) {
            return gson.fromJson(reply.get("tunnel_config"), MAP_TYPE);
        }
        return null;
    }

    /** Tunnel health check (plugin id auto-resolved). @see #tunnel_health_check(String, String, String, String) */
    public boolean tunnel_health_check(String region, String agent, String stunnel_id) {
        return tunnel_health_check(region, agent, stunnel_id, null);
    }

    /**
     * Ask an agent's stunnel plugin whether it holds a config for the tunnel ({@code tunnelhealthcheck};
     * this is the probe the two tunnel ends use on each other).
     *
     * @param region     region name of the agent
     * @param agent      agent name
     * @param stunnel_id tunnel id
     * @param plugin_id  stunnel plugin id, or {@code null} to resolve by name
     * @return {@code true} if the tunnel is configured on that agent
     */
    public boolean tunnel_health_check(String region, String agent, String stunnel_id, String plugin_id) {
        Map<String,String> reply = exec(region, agent, plugin_id, "tunnelhealthcheck", stunnel_id);
        return reply != null && STATUS_OK.equals(reply.get("status"));
    }

    /** Plugin metrics (plugin id auto-resolved). @see #get_metrics(String, String, String) */
    public Map<String,Object> get_metrics(String region, String agent) {
        return get_metrics(region, agent, null);
    }

    /**
     * Get the stunnel plugin's live metrics (per-tunnel throughput, sessions, flow-control counters).
     *
     * @param region    region name of the agent
     * @param agent     agent name
     * @param plugin_id stunnel plugin id, or {@code null} to resolve by name
     * @return the plugin's metrics JSON as a map, or {@code null} on failure
     */
    @SuppressWarnings("unchecked")
    public Map<String,Object> get_metrics(String region, String agent, String plugin_id) {
        Map<String,String> reply = exec(region, agent, plugin_id, "getmetrics", null);
        if (reply != null && reply.get("metrics") != null) {
            return gson.fromJson(reply.get("metrics"), Map.class);
        }
        return null;
    }

    // ---------------------------------------------------------------- tuning

    /** Network tuning (plugin id auto-resolved). @see #set_net_tuning(String, String, Map, String) */
    public Map<String,String> set_net_tuning(String region, String agent, Map<String,String> tuning) {
        return set_net_tuning(region, agent, tuning, null);
    }

    /**
     * Apply plugin-wide network tuning on an agent's stunnel plugin ({@code nettuning}). Recognized keys
     * (all integers): {@code net_socket_buffer_bytes}, {@code net_read_chunk_bytes},
     * {@code net_write_high_water_bytes}, {@code net_fc_window_bytes}, {@code net_trace_sample_n}.
     * New values apply to connections opened afterwards.
     *
     * @param region    region name of the agent
     * @param agent     agent name
     * @param tuning    tuning keys/values (unknown keys are ignored by the plugin)
     * @param plugin_id stunnel plugin id, or {@code null} to resolve by name
     * @return the plugin reply ({@code status} "10" on success), or {@code null} on failure
     */
    public Map<String,String> set_net_tuning(String region, String agent, Map<String,String> tuning, String plugin_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>(tuning);
            p.put("action", "nettuning");
            return messaging.global_plugin_msgevent(true, "CONFIG", p, region, agent, plugin_id);
        } catch (CrescoQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    // ---------------------------------------------------------------- remove

    /** Remove the source end (plugin id auto-resolved). @see #remove_src_tunnel(String, String, String, String) */
    public Map<String,String> remove_src_tunnel(String region, String agent, String stunnel_id) {
        return remove_src_tunnel(region, agent, stunnel_id, null);
    }

    /**
     * Tear down the source end of a tunnel: closes the listener and its sessions and deletes the persisted
     * config. The source plugin also asks the destination plugin to drop its end.
     *
     * @param region     region of the source agent
     * @param agent      source agent name
     * @param stunnel_id tunnel id
     * @param plugin_id  stunnel plugin id, or {@code null} to resolve by name
     * @return the plugin reply ({@code status} "10" on success), or {@code null} on failure
     */
    public Map<String,String> remove_src_tunnel(String region, String agent, String stunnel_id, String plugin_id) {
        return config(region, agent, plugin_id, "removesrctunnel", stunnel_id);
    }

    /** Remove the destination end (plugin id auto-resolved). @see #remove_dst_tunnel(String, String, String, String) */
    public Map<String,String> remove_dst_tunnel(String region, String agent, String stunnel_id) {
        return remove_dst_tunnel(region, agent, stunnel_id, null);
    }

    /**
     * Tear down the destination end of a tunnel (its sessions and persisted config).
     *
     * @param region     region of the destination agent
     * @param agent      destination agent name
     * @param stunnel_id tunnel id
     * @param plugin_id  stunnel plugin id, or {@code null} to resolve by name
     * @return the plugin reply ({@code status} "10" on success), or {@code null} on failure
     */
    public Map<String,String> remove_dst_tunnel(String region, String agent, String stunnel_id, String plugin_id) {
        return config(region, agent, plugin_id, "removedsttunnel", stunnel_id);
    }

    /** Remove both ends of a tunnel (plugin ids auto-resolved). @see #remove_tunnel(String, String, String, String, String, String, String) */
    public Map<String,Object> remove_tunnel(String stunnel_id, String src_region, String src_agent,
                                            String dst_region, String dst_agent) {
        return remove_tunnel(stunnel_id, src_region, src_agent, dst_region, dst_agent, null, null);
    }

    /**
     * Remove both ends of a tunnel (source first, then destination).
     *
     * @return {@code {stunnel_id, src_removal, dst_removal, fully_removed}} where the removals are the plugin
     *         replies and {@code fully_removed} is true when both ends answered
     */
    public Map<String,Object> remove_tunnel(String stunnel_id, String src_region, String src_agent,
                                            String dst_region, String dst_agent,
                                            String src_plugin_id, String dst_plugin_id) {
        Map<String,Object> out = new HashMap<>();
        out.put("stunnel_id", stunnel_id);
        Map<String,String> srcRemoval = remove_src_tunnel(src_region, src_agent, stunnel_id, src_plugin_id);
        Map<String,String> dstRemoval = remove_dst_tunnel(dst_region, dst_agent, stunnel_id, dst_plugin_id);
        out.put("src_removal", srcRemoval);
        out.put("dst_removal", dstRemoval);
        out.put("fully_removed", srcRemoval != null && dstRemoval != null);
        return out;
    }

    // ---------------------------------------------------------------- internals

    private Map<String,String> exec(String region, String agent, String plugin_id, String action, String stunnel_id) {
        return call("EXEC", region, agent, plugin_id, action, stunnel_id);
    }

    private Map<String,String> config(String region, String agent, String plugin_id, String action, String stunnel_id) {
        return call("CONFIG", region, agent, plugin_id, action, stunnel_id);
    }

    private Map<String,String> call(String type, String region, String agent, String plugin_id,
                                    String action, String stunnel_id) {
        try {
            if (plugin_id == null) plugin_id = find_plugin(region, agent);
            if (plugin_id == null) return null;
            Map<String,Object> p = new HashMap<>();
            p.put("action", action);
            if (stunnel_id != null) p.put("action_stunnel_id", stunnel_id);
            return messaging.global_plugin_msgevent(true, type, p, region, agent, plugin_id);
        } catch (CrescoQueryException ex) {
            throw ex;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
