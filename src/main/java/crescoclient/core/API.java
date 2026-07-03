package crescoclient.core;

import com.google.gson.Gson;
import crescoclient.msgevent.MsgEventInterface;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class API {

    private Gson gson;
    private MsgEventInterface msgEventInterface;
    private Messaging messaging;

    private String globalRegion;
    private String globalAgent;

    public API(MsgEventInterface msgEventInterface, Messaging messaging) {
        gson = new Gson();
        this.msgEventInterface = msgEventInterface;
        this.messaging = messaging;
    }

    /**
     * Get the API region name.
     *
     * @return Region name
     */
    public String get_api_region_name() {
        return msgEventInterface.getRegionName();
    }

    /**
     * Get the API agent name.
     *
     * @return Agent name
     */
    public String get_api_agent_name() {
        return msgEventInterface.getAgentName();
    }

    /**
     * Get the API plugin name.
     *
     * @return Plugin name
     */
    public String get_api_plugin_name() {
        return msgEventInterface.getPluginName();
    }

    /**
     * Get the global region.
     *
     * @return Global region or null
     */
    public String get_global_region() {

        if(globalRegion == null) {
            get_global_info();
        }

        return globalRegion;
    }

    /**
     * Get the global agent.
     *
     * @return Global agent or null
     */
    public String get_global_agent() {

        if(globalAgent == null) {
            get_global_info();
        }

        return globalAgent;
    }

    /**
     * Get global information.
     *
     * @return Pair of (global_region, global_agent): getKey() is the global region, getValue() the global agent
     */
    public Map.Entry<String,String> get_global_info() {
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();

            message_payload.put("action","globalinfo");
            Map<String,String> reply = messaging.plugin_msgevent(true, message_event_type, message_payload, get_api_plugin_name());
            globalRegion = reply.get("global_region");
            globalAgent = reply.get("global_agent");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return new AbstractMap.SimpleEntry<>(globalRegion, globalAgent);
    }

}
