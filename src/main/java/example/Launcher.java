package example;

import crescoclient.*;

public class Launcher {

    public static void main(String[] args) throws Exception {

        String host = "localhost";
        int port = 8282;
        String service_key = "c988701a-5f2a-43ac-b915-156049c5d1ee";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();
        //System.out.println("ARE YOU BLOCKING?");

        if(client.connected()) {

            //System.out.println("ARE YOU BLOCKING? 2");
            //BinaryPerformanceTesting binaryPerformanceTesting = new BinaryPerformanceTesting(client);
            //binaryPerformanceTesting.runTest();

            //BinaryFileRepoTesting binaryFileRepoTesting = new BinaryFileRepoTesting(client);
            //binaryFileRepoTesting.runTest();

            //TextPerformanceTesting textPerformanceTesting = new TextPerformanceTesting(client);
            //textPerformanceTesting.runTest();

            FileRepoPerformanceTesting fileRepoPerformanceTesting = new FileRepoPerformanceTesting(client);
            fileRepoPerformanceTesting.runTest();

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

        } else {
            System.out.println("Could not connect to remote.");
        }
    }

}
