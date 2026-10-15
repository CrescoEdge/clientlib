package crescoclient.core;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

/**
 * Administrative control of agents ({@code client.admin}). All calls are fire-and-forget CONFIG
 * MsgEvents routed through the global controller to the target agent; check the result with
 * {@link Agents#is_controller_active} or {@link GlobalController#get_agent_list}.
 */
public class Admin {

    private Gson gson;
    private Utils utils;
    private Messaging messaging;

    /**
     * Create the admin submodule. {@link crescoclient.CrescoClient} builds this for you as {@code client.admin}.
     *
     * @param messaging the client's messaging submodule
     */
    public Admin(Messaging messaging) {
        utils = new Utils();
        gson = new Gson();
        this.messaging = messaging;
    }

    /**
     * Stop the controller on an agent (CONFIG {@code stopcontroller}, fire-and-forget). The agent leaves the
     * mesh until it is restarted out of band.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     */
    public void stopcontroller(String dst_region, String dst_agent) {

        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","stopcontroller");

            messaging.global_agent_msgevent(false,message_event_type,message_payload,dst_region,dst_agent);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Restart the controller on an agent (CONFIG {@code restartcontroller}, fire-and-forget). The agent
     * re-registers with its region when the controller is back.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     */
    public void restartcontroller(String dst_region, String dst_agent) {

        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","restartcontroller");

            messaging.global_agent_msgevent(false,message_event_type,message_payload,dst_region,dst_agent);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Restart the agent's OSGi framework, i.e. every bundle (CONFIG {@code restartframework}, fire-and-forget).
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     */
    public void restartframework(String dst_region, String dst_agent) {

        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","restartframework");

            messaging.global_agent_msgevent(false,message_event_type,message_payload,dst_region,dst_agent);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    /**
     * Terminate the agent's JVM (CONFIG {@code killjvm}, fire-and-forget). Nothing restarts it unless the
     * agent runs under a supervisor.
     *
     * @param dst_region region name of the target agent
     * @param dst_agent  name of the target agent
     */
    public void killjvm(String dst_region, String dst_agent) {

        try {

            String message_event_type = "CONFIG";
            Map<String,Object> message_payload = new HashMap<>();
            message_payload.put("action","killjvm");

            messaging.global_agent_msgevent(false,message_event_type,message_payload,dst_region,dst_agent);


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


}
