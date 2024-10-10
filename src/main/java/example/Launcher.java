package example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.*;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import example.filerepo.FileRepoPerformanceTesting;
import example.stunnel.SingleNodeTunnelTest;
import example.stunnel.TunnelTesting;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Launcher {

    public static void main(String[] args) throws Exception {

        String configFilePath = args.length > 0 ? args[0] : "config.json";

        Map<String, Object> configMap = loadConfig(configFilePath);

        String host = (String) configMap.get("host");
        int port = Integer.parseInt(configMap.get("port").toString());
        String service_key = (String) configMap.get("service_key");//"c988701a-5f2a-43ac-b915-156049c5d1ee";
        String tenantId = (String) configMap.get("tenant_id");
        Map<String, Object> sTunnelInfo = (Map<String, Object>) configMap.get("sTunnel");

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();
        

        //System.out.println("ARE YOU BLOCKING?");

        if(client.connected()) {

            TunnelTesting tunnelTesting = new TunnelTesting(client);
            tunnelTesting.tunnelTest(sTunnelInfo, tenantId);

            //System.out.println("ARE YOU BLOCKING? 2");
            //BinaryPerformanceTesting binaryPerformanceTesting = new BinaryPerformanceTesting(client);
            //binaryPerformanceTesting.runTest();

            //BinaryFileRepoTesting binaryFileRepoTesting = new BinaryFileRepoTesting(client);
            //binaryFileRepoTesting.runTest();

            //TextPerformanceTesting textPerformanceTesting = new TextPerformanceTesting(client);
            //textPerformanceTesting.runTest();

            //FileRepoPerformanceTesting fileRepoPerformanceTesting = new FileRepoPerformanceTesting(client);
            //fileRepoPerformanceTesting.runTest();

            /*

            Map<String,String> update = new HashMap<>();
            update.put("action",action);
            update.put("filerepo_name",filerepoName);
            update.put("repo_region_id", plugin.getRegion());
            update.put("repo_agent_id",plugin.getAgent());
            update.put("repo_plugin_id",plugin.getPluginID());
            update.put("transfer_id", String.valueOf(transferId));

            TextMessage updateMessage = plugin.getAgentService().getDataPlaneService().createTextMessage();
            updateMessage.setText(gson.toJson(update));
            updateMessage.setStringProperty("filerepo_name",filerepoName);
            updateMessage.setBooleanProperty("broadcast",Boolean.TRUE);

             */

            /*
            class BytePrinter implements OnMessageCallback {

                @Override
                public void onMessage(String msg) {

                    System.out.println("TEXT MESSAGE!");

                }

                @Override
                public void onMessage(byte[] b, int offset, int length) {
                    //bytesTransferred = bytesTransferred + length;
                    //String s = new String(b, StandardCharsets.UTF_8);
                    //System.out.println("binary: " + s);
                    //System.out.println("length: " + b.length + " offset: " + offset + " length: " + length);
                }
            }

            DataPlaneInterface dataPlaneRec = client.getDataPlane("tabby=pooter", new BytePrinter());
            dataPlaneRec.start();
            while(!dataPlaneRec.connected()) {
                Thread.sleep(1000);
            }
            System.out.println("WOOO");

            while(true) {
                Thread.sleep(1000);
            }
             */

        } else {
            System.out.println("Could not connect to remote.");
        }
    }

    private static Map<String, Object> loadConfig(String configFilePath) {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        String jsonContent;

        try {
            if (configFilePath != null && new File(configFilePath).exists()) {
                // Read the JSON from the provided file
                jsonContent = new String(Files.readAllBytes(Paths.get(configFilePath)));
            } else {
                throw new RuntimeException("Failed to load configuration");
            }

            // Parse JSON into a Map
            return gson.fromJson(jsonContent, type);

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to load configuration");
        }
    }

}
