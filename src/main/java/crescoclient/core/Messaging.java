package crescoclient.core;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import crescoclient.msgevent.MsgEventInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Raw MsgEvent messaging ({@code client.messaging}): the transport every other submodule is built on, plus
 * the param encode/decode helpers.
 *
 * <p>A MsgEvent is a typed message ({@code EXEC}, {@code CONFIG}, ...) with string params; the {@code action}
 * param selects the handler on the receiving controller or plugin. The eight routing methods differ only in
 * the destination: the global or regional controller, an agent's controller, or a plugin on an agent. All take
 * {@code (is_rpc, message_event_type, message_payload, ...)}; with {@code is_rpc} true the call blocks until the
 * reply arrives (one outstanding RPC per control connection and thread) and returns the reply params.
 *
 * <pre>{@code
 * Map<String, Object> payload = new HashMap<>();
 * payload.put("action", "listregions");
 * Map<String, String> reply = client.messaging.global_controller_msgevent(true, "EXEC", payload);
 * String regionsJson = client.messaging.getCompressedParam(reply.get("regionslist"));
 * }</pre>
 */
public class Messaging {

    private Gson gson;
    private MsgEventInterface ws_interface;
    private Type maptype = new TypeToken<Map<String, String>>(){}.getType();
    private Type mapListMapType = new TypeToken<Map<String, List<Map<String, String>>>>(){}.getType();

    private Type listMapType = new TypeToken<List<Map<String, String>>>(){}.getType();

    /**
     * Create the messaging submodule. {@link crescoclient.CrescoClient} builds this for you as {@code client.messaging}.
     *
     * @param ws_interface the control-channel transport
     */
    public Messaging(MsgEventInterface ws_interface) {
        this.ws_interface = ws_interface;
        gson = new Gson();
    }


    /**
     * Send a MsgEvent to the global controller.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> global_controller_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","global_controller_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to the regional controller of the region the wsapi endpoint is in.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> regional_controller_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","regional_controller_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to any agent's controller, routed through the global controller.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @param dst_region         region of the target agent
     * @param dst_agent          target agent name
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> global_agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_region, String dst_agent) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","global_agent_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("dst_region",dst_region);
            message_info.put("dst_agent",dst_agent);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to an agent in the wsapi endpoint's own region, routed through its regional controller.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @param dst_agent          target agent name
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> regional_agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_agent) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","regional_agent_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("dst_agent",dst_agent);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to the controller of the agent hosting the wsapi endpoint.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","agent_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to a plugin on any agent, routed through the global controller.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @param dst_region         region of the target agent
     * @param dst_agent          target agent name
     * @param dst_plugin         plugin id on that agent (see {@link GlobalController#find_plugin})
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> global_plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_region, String dst_agent, String dst_plugin) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","global_plugin_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("dst_region",dst_region);
            message_info.put("dst_agent",dst_agent);
            message_info.put("dst_plugin",dst_plugin);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload", new HashMap<>(message_payload));

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to a plugin on an agent in the wsapi endpoint's own region.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @param dst_agent          target agent name
     * @param dst_plugin         plugin id on that agent
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> regional_plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_agent, String dst_plugin) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","regional_plugin_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("dst_agent",dst_agent);
            message_info.put("dst_plugin",dst_plugin);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Send a MsgEvent to a plugin on the agent hosting the wsapi endpoint.
     *
     * @param is_rpc             true to wait for the reply, false to send without waiting
     * @param message_event_type MsgEvent type: {@code EXEC}, {@code CONFIG}, {@code INFO}, {@code WATCHDOG}, ...
     * @param message_payload    MsgEvent params; {@code action} selects the handler on the receiver
     * @param dst_plugin         plugin id on that agent
     * @return the reply params when {@code is_rpc}, else null. Null too if no reply arrived within the client's
     *         30 s RPC timeout; when the fabric gives up first the reply carries {@code status} "504".
     */
    public Map<String,String> plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_plugin) {
        Map<String,String> response = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","plugin_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("dst_plugin",dst_plugin);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(is_rpc, json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                response = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return response;
    }

    /**
     * Decode a compressed MsgEvent param (base64 of gzip of UTF-8 text). Large reply values (lists,
     * pipelines, configs) arrive this way.
     *
     * @param inputString the base64 value
     * @return the decoded text, or null if the input is null or not gzip data
     */
    public String getCompressedParam(String inputString) {
        if (inputString == null)
            return null;
        try {
            byte[] exportDataRawCompressed = Base64.getDecoder().decode(inputString);
            try(InputStream iss = new ByteArrayInputStream(exportDataRawCompressed);
                InputStream is = new GZIPInputStream(iss);) {
                return new Scanner(is,"UTF-8").useDelimiter("\\A").next();
            }
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Decode a base64 binary param (no decompression).
     *
     * @param value the base64 value
     * @return the bytes, or null
     */
    public byte[] getDataParam(String value) {
        byte[] byteArray = null;
        try {
            if (value != null) {
                //byteArray = DatatypeConverter.parseBase64Binary(value);
                byteArray = Base64.getDecoder().decode(value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return byteArray;
    }

    /**
     * Gunzip bytes.
     *
     * @param compressedData gzip data
     * @return the decompressed bytes, or null on error
     */
    public byte[] dataDecompress(byte[] compressedData) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                compressedData);

        try {
            GZIPInputStream gzipInputStream = new GZIPInputStream(
                    byteArrayInputStream);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            gzipInputStream.close();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Encode text as a compressed MsgEvent param (gzip, then base64); the inverse of {@link #getCompressedParam}.
     *
     * @param inputString the text (usually JSON)
     * @return the base64 value
     */
    public String setCompressedParam(String inputString) {
        return Base64.getEncoder().encodeToString(stringCompress(inputString));
    }

    /**
     * Encode bytes as a base64 MsgEvent param. Despite the name the bytes are not compressed (used for jar uploads).
     *
     * @param value the bytes
     * @return the base64 value
     */
    public String setCompressedDataParam(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    /**
     * Decode a base64 param holding gzip data and gunzip it.
     *
     * @param value the base64 value
     * @return the decompressed bytes, or null on error
     */
    public byte[] getCompressedDataParam(String value) {
        return dataDecompress(getDataParam(value));
    }

    /**
     * Gzip a string's UTF-8 bytes.
     *
     * @param str the text
     * @return the gzip bytes
     */
    public byte[] stringCompress(String str) {

        byte[] dataToCompress = str.getBytes(StandardCharsets.UTF_8);
        return dataCompress(dataToCompress);
    }

    /**
     * Gzip bytes.
     *
     * @param dataToCompress the bytes
     * @return the gzip bytes, or null on error
     */
    public byte[] dataCompress(byte[] dataToCompress) {

        byte[] compressedData;
        try {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream(dataToCompress.length);
            try {
                GZIPOutputStream zipStream =
                        new GZIPOutputStream(byteStream);
                try {
                    zipStream.write(dataToCompress);
                }
                finally {
                    zipStream.close();
                }
            } finally {
                byteStream.close();
            }
            compressedData = byteStream.toByteArray();
        } catch(Exception e) {
            return null;
        }
        return compressedData;
    }

    /**
     * Parse a JSON object of string values.
     *
     * @param mapString the JSON
     * @return the map
     */
    public Map<String,String> getMapFromString(String mapString) {
        return gson.fromJson(mapString,maptype);
    }

    /**
     * Parse JSON shaped like {@code {"key": [ {..}, {..} ]}} (the shape of most list replies).
     *
     * @param mapListMapString the JSON
     * @return the map
     */
    public Map<String,List<Map<String,String>>> getMapListMapFromString(String mapListMapString) {
        return gson.fromJson(mapListMapString,mapListMapType);
    }

    /**
     * Parse a JSON array of objects of string values.
     *
     * @param listMapString the JSON
     * @return the list
     */
    public List<Map<String,String>> getListMapFromString(String listMapString) {
        return gson.fromJson(listMapString,listMapType);
    }

}
