package crescoclient;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

@WebSocket
public class DataPlaneImpl
{
    public boolean isActive = false;
    private int messageCount = 0;
    private Type type = new TypeToken<Map<String, String>>(){}.getType();
    private final Logger LOG = Log.getLogger(DataPlaneImpl.class);

    private Gson gson;
    private DataPlaneCallback dataPlaneCallback;

    private String streamName;
    public DataPlaneImpl(DataPlaneCallback dataPlaneCallback, String streamName) {
        this.dataPlaneCallback = dataPlaneCallback;
        this.streamName = streamName;
        this.gson = new Gson();
    }

    @OnWebSocketConnect
    public void onConnect(Session sess)
    {
        LOG.debug("onConnect({})", sess);
        try {
            sess.getRemote().sendString(streamName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, String reason)
    {
        LOG.debug("onClose({}, {})", statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Throwable cause)
    {
        LOG.warn(cause);
    }

    @OnWebSocketMessage
    public void onMessage(String msg) {
        try {
            if(messageCount == 0) {
                Map<String, String> statusMap = gson.fromJson(msg, type);
                if(statusMap.get("status_code").equals("10")) {
                    isActive = true;
                }
            } else {
                dataPlaneCallback.onMessage(msg);
            }
            messageCount += 1;

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
