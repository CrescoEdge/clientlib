package example;

import crescoclient.*;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import example.filerepo.FileRepoPerformanceTesting;
import example.messaging.BinaryPerformanceTesting;
import example.messaging.TextPerformanceTesting;
import example.stunnel.SingleNodeTunnelTest;
import example.stunnel.TunnelTesting;

public class Launcher {

    public static void main(String[] args) throws Exception {

        String host = "localhost";
        int port = 8282;
        String service_key = "a6f7f889-2500-46d3-9484-5b6499186456";

        CrescoClient client = new CrescoClient(host,port,service_key);
        client.connect();
        

        //System.out.println("ARE YOU BLOCKING?");

        if(client.connected()) {


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

            String DPQuery = "region_id IS NOT NULL AND agent_id IS NOT NULL";
            DataPlaneInterface dataPlaneRec = client.getDataPlane(DPQuery, new BytePrinter());
            dataPlaneRec.start();
            while(!dataPlaneRec.connected()) {
                Thread.sleep(1000);
            }
            System.out.println("WOOO");

            while(true) {
                Thread.sleep(1000);
            }

            //TunnelTesting tunnelTesting = new TunnelTesting(client);
            //tunnelTesting.tunnelTest();
            //tunnelTesting.tunnelTest("global-region", "agent-controller");


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

}
