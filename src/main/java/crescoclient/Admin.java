package crescoclient;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class Admin {

    private Gson gson;
    private Utils utils;
    private Messaging messaging;

    public Admin(Messaging messaging) {
        utils = new Utils();
        gson = new Gson();
        this.messaging = messaging;
    }

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
