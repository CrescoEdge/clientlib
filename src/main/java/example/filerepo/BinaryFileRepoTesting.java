package example.filerepo;

import com.google.gson.Gson;
import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;

import java.nio.ByteBuffer;
import java.util.*;

public class BinaryFileRepoTesting {

    private CrescoClient client;
    public long bytesTransferred = 0;
    public BinaryFileRepoTesting(CrescoClient client) {
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
                    double bytesPerSecond = (double) bytesTransferred / timeElapsed;

                    // Print the bytes per second
                    System.out.println("Bytes per second: " + bytesPerSecond);
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
                    System.out.println("TEXT MESSAGE! " + msg);
                }

                @Override
                public void onMessage(byte[] b, int offset, int length) {
                    bytesTransferred = bytesTransferred + length;
                    //String s = new String(b, StandardCharsets.UTF_8);
                    //System.out.println("binary: " + s);
                    System.out.println("length: " + b.length + " offset: " + offset + " length: " + length);
                }
            }

            DataPlaneInterface dataPlaneRec = client.getDataPlane("", new BytePrinter());
            dataPlaneRec.start();
            while(!dataPlaneRec.connected()) {
                Thread.sleep(1000);
            }

            String filerepoName = "repopipe";

            Map<String,String> update = new HashMap<>();
            update.put("action","transfer");
            update.put("filerepo_name",filerepoName);

            //String queryString = "filerepo_stream_name='" + filerepoName + "' AND broadcast";

            //((incomingMap.containsKey("transfer_id")) && (incomingMap.containsKey("transaction_id"))) {
            DataPlaneInterface dataPlaneSend = client.getDataPlane(queryString);
            dataPlaneSend.start();
            while(!dataPlaneSend.connected()) {
                Thread.sleep(1000);
            }



            while(true) {
                Thread.sleep(1000);
                dataPlaneSend.send(new Gson().toJson(update));
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


}
