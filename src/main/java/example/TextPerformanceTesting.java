package example;

import com.google.gson.Gson;
import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;

import java.util.*;

public class TextPerformanceTesting {

    private CrescoClient client;
    public long bytesTransferred = 0;
    public TextPerformanceTesting(CrescoClient client) {
        this.client = client;
    }

    public void runTest() {

        try {

            long startTime = System.currentTimeMillis();

            //setup performance timer
            Timer timer = new Timer();

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    // Get the number of bytes transferred

                    // Get the time it took to transfer the bytes
                    long timeElapsed = System.currentTimeMillis() - startTime;

                    // Calculate the bytes per second
                    double bytesPerSecond = (double) bytesTransferred / (timeElapsed/1000);

                    // Print the bytes per second
                    System.out.println("Message per second: " + bytesPerSecond);
                }
            };

            // Schedule the timer task to run every second
            timer.schedule(task, 0, 1000);

            System.out.println("API: region: " + client.api.getAPIRegionName() + " agent: " + client.api.getAPIAgentName() + " plugin: " + client.api.getAPIPluginName());
            String dst_region = client.api.getGlobalRegion();
            String dst_agent = client.api.getGlobalAgent();
            System.out.println("Global Controller: region: " + dst_region + " agent:" + dst_agent);
            System.out.println("---");

            //String queryString = "stream_test='" + "bin" + "'";
            String identKey = "stream_name";
            String identId = "1234";
            //String streamQuery = "stream_name='" + identId + "'";
            Map<String, String> configDB = new HashMap<>();
            configDB.put("ident_key", identKey);
            configDB.put("ident_id", identId);
            //configDB.put("stream_query",identKey + "='" + identId + "' and type='" + "outgoing" + "'");
            configDB.put("io_type_key", "type");
            configDB.put("output_id", "output");
            configDB.put("input_id", "output");
            Gson gson = new Gson();

            String queryString = gson.toJson(configDB);

            class BytePrinter implements OnMessageCallback {

                @Override
                public void onMessage(String msg) {

                    //System.out.println("TEXT MESSAGE!");
                    bytesTransferred += 1;
                }

                @Override
                public void onMessage(byte[] b, int offset, int length) {
                    //bytesTransferred = bytesTransferred + length;
                    //String s = new String(b, StandardCharsets.UTF_8);
                    //System.out.println("binary: " + s);
                    //System.out.println("length: " + b.length + " offset: " + offset + " length: " + length);
                }
            }

            DataPlaneInterface dataPlaneRec = client.getDataPlane("", new BytePrinter());
            dataPlaneRec.start();
            while(!dataPlaneRec.connected()) {
                Thread.sleep(1000);
            }

            DataPlaneInterface dataPlaneSend = client.getDataPlane(queryString);
            dataPlaneSend.start();
            while(!dataPlaneSend.connected()) {
                Thread.sleep(1000);
            }

            Thread.sleep(5000);


            String input = "BRRRRUUU";

            for(int i = 0; i<10; i++) {
               launchThread(dataPlaneSend, input);
            }

            while(true) {
            //for(int i = 0; i<25; i++) {
                Thread.sleep(1000);
                //dataPlaneSend.send(str);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void launchThread(DataPlaneInterface dataPlaneSend, String input) {

        new Thread() {
            public void run() {
                try {
                    while(true) {
                        for(int i = 0; i<100; i++) {
                            //Thread.sleep(1000);
                            //dataPlaneSend.send(str);
                            dataPlaneSend.send(input);
                        }
                        //dataPlaneSend.send(input);
                        //Thread.sleep(1000);
                    }

                } catch(Exception v) {
                    System.out.println(v);
                }
            }
        }.start();

    }

}
