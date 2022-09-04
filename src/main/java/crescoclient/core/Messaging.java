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

public class Messaging {

    private Gson gson;
    private MsgEventInterface ws_interface;
    private Type maptype = new TypeToken<Map<String, String>>(){}.getType();
    private Type mapListMapType = new TypeToken<Map<String, List<Map<String, String>>>>(){}.getType();


    public Messaging(MsgEventInterface ws_interface) {
        this.ws_interface = ws_interface;
        gson = new Gson();
    }


    public Map<String,String> global_controller_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> responce = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","global_controller_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> regional_controller_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> responce = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","regional_controller_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> global_agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_region, String dst_agent) {
        Map<String,String> responce = null;
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

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> regional_agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_agent) {
        Map<String,String> responce = null;
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

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> agent_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload) {
        Map<String,String> responce = null;
        try {
            Map<String,String> message_info = new HashMap<>();
            message_info.put("message_type","agent_msgevent");
            message_info.put("message_event_type",message_event_type);
            message_info.put("is_rpc",String.valueOf(is_rpc));

            Map<String,Object> message = new HashMap<>();
            message.put("message_info",message_info);
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> global_plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_region, String dst_agent, String dst_plugin) {
        Map<String,String> responce = null;
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
            message.put("message_payload",message_payload);

            String json_message = gson.toJson(message);

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> regional_plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_agent, String dst_plugin) {
        Map<String,String> responce = null;
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

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

    public Map<String,String> plugin_msgevent(boolean is_rpc, String message_event_type, Map<String,Object> message_payload, String dst_plugin) {
        Map<String,String> responce = null;
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

            ws_interface.send(json_message);

            if(is_rpc) {
                String json_incoming = ws_interface.recv();
                responce = gson.fromJson(json_incoming,maptype);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return responce;
    }

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

    public String setCompressedParam(String inputString) {
        return Base64.getEncoder().encodeToString(stringCompress(inputString));
    }

    public String setCompressedDataParam(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    public byte[] stringCompress(String str) {

        byte[] dataToCompress = str.getBytes(StandardCharsets.UTF_8);
        return dataCompress(dataToCompress);
    }

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

    public Map<String,String> getMapFromString(String mapString) {
        return gson.fromJson(mapString,maptype);
    }

    public Map<String,List<Map<String,String>>> getMapListMapFromString(String mapListMapString) {
        return gson.fromJson(mapListMapString,mapListMapType);
    }

}
