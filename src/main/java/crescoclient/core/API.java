package crescoclient.core;

import com.google.gson.Gson;
import crescoclient.msgevent.MsgEventInterface;

import java.util.HashMap;
import java.util.List;
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

    public String getAPIRegionName() {
        return msgEventInterface.getRegionName();
    }
    public String getAPIAgentName() {
        return msgEventInterface.getAgentName();
    }
    public String getAPIPluginName() {
        return msgEventInterface.getPluginName();
    }

    private void getGlobalInfo() {
        try {
            String message_event_type = "EXEC";
            Map<String,Object> message_payload = new HashMap<>();

            message_payload.put("action","globalinfo");
            Map<String,String> reply = messaging.plugin_msgevent(true, message_event_type, message_payload, getAPIPluginName());
            globalRegion = reply.get("global_region");
            globalAgent = reply.get("global_agent");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public String getGlobalRegion() {

        if(globalRegion == null) {
            getGlobalInfo();
        }

        return globalRegion;
    }

    public String getGlobalAgent() {

        if(globalAgent == null) {
            getGlobalInfo();
        }

        return globalAgent;
    }

}
