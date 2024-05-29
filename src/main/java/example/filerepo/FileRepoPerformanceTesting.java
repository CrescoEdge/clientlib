package example.filerepo;

import com.google.gson.Gson;
import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import io.cresco.library.app.gPayload;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.util.InputStreamResponseListener;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileRepoPerformanceTesting {

    private final AtomicBoolean ioLock = new AtomicBoolean();
    private Map<String, TransferStream> transferStreams;

    private CrescoClient crescoClient;
    public long bytesTransferred = 0;
    public FileRepoPerformanceTesting(CrescoClient client) {
        this.crescoClient = client;
        transferStreams = Collections.synchronizedMap(new HashMap<>());
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
                    //System.out.println("Message per second: " + bytesPerSecond);
                }
            };

            // Schedule the timer task to run every second
            timer.schedule(task, 0, 1000);

            System.out.println("API: region: " + crescoClient.api.getAPIRegionName() + " agent: " + crescoClient.api.getAPIAgentName() + " plugin: " + crescoClient.api.getAPIPluginName());
            String dst_region = crescoClient.api.getGlobalRegion();
            String dst_agent = crescoClient.api.getGlobalAgent();
            System.out.println("Global Controller: region: " + dst_region + " agent:" + dst_agent);
            System.out.println("---");

            String pipelineName = "repopipe";
            String repoName = "testrepo";
            String repoPath = "/Users/cody/IdeaProjects/neuros3sa/repo_data";
            String pipelineId = initRepoTest(pipelineName, repoName, repoPath);

            gPayload fileRepoDeployStatus = crescoClient.globalcontroller.get_pipeline_info(pipelineId);

            Map<String,String> repoMap = fileRepoDeployStatus.nodes.get(0).params;
            String repoRegion = repoMap.get("location_region");
            String repoAgent = repoMap.get("location_agent");
            String repoPlugin = repoMap.get("inode_id");

            String identKey = "stream_name";
            String identId = UUID.randomUUID().toString();
            DataPlaneInterface dataPlaneInterface =  getTransferStreamer(identKey, identId);

            String transferId;
            String fileName = "/Users/cody/IdeaProjects/neuros3sa/repo_data/test.svs";
            long startByte;
            long byteLength = 834850409;

            for(int i=0; i<100; i++) {
                synchronized (ioLock) {
                    startByte = i * 1014;
                    transferId = UUID.randomUUID().toString().replace("-", "");
                    transferStreams.put(transferId, new TransferStream(repoRegion, repoAgent, repoPlugin, fileName, transferId, startByte, byteLength));
                }
                start(identKey, identId, transferId, fileName, startByte, byteLength, repoRegion, repoAgent, repoPlugin);
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }


    public String initRepoTest(String pipelineName, String repoName, String repoPath) {

        String pipelineId = null;
        try {
            System.out.println("API: region: " + crescoClient.api.getAPIRegionName() + " agent: " + crescoClient.api.getAPIAgentName() + " plugin: " + crescoClient.api.getAPIPluginName());
            String dst_region = crescoClient.api.getGlobalRegion();
            String dst_agent = crescoClient.api.getGlobalAgent();
            System.out.println("Global Controller: region: " + dst_region + " agent:" + dst_agent);
            System.out.println("---");

            //String pipelineName = "repopipe";
            //String repoName = "testrepo";
            //String repoPath = "/Users/cody/IdeaProjects/neuros3sa/repo_data";

            pipelineId = getPipelineIdByName(pipelineName);
            if (pipelineId == null) {
                //boolean isRemoved = crescoClient.globalcontroller.remove_pipeline(pipelineId);
                //if (isRemoved) {
                pipelineId = deployFileRepo(pipelineName, repoName, repoPath);
                //}
            }

            System.out.println("Repo PipelineId: " + pipelineId);
        } catch (Exception ex) {
            System.out.println("initRepoTest() " + ex.getMessage());
            ex.printStackTrace();
        }
        return pipelineId;
    }

    public String deployFileRepo(String pipelineName, String repo_name_1, String repo_path_1) throws InterruptedException {

        boolean launchRepo = true;

        //Check if the pipeline is running
        String pipelineId = getPipelineIdByName(pipelineName);

        if(pipelineId != null) {
            //get status of running pipeline
            int pipelineStatus = crescoClient.globalcontroller.get_pipeline_status(pipelineId);

            if (pipelineStatus == 10) {
                launchRepo = false;
            } else {
                //if pipeline is not in a good status remove
                boolean isRemoved = crescoClient.globalcontroller.remove_pipeline(pipelineId);
            }
        }

        if(launchRepo) {

            //Location of latest filerepo
            String uri = "https://github.com/CrescoEdge/filerepo/releases/download/1.1-SNAPSHOT/filerepo-1.1-SNAPSHOT.jar";

            //Local save file
            String pluginSavePath = uri.substring(uri.lastIndexOf('/') + 1);

            if(!(new File(pluginSavePath).isFile())) {
                //pull plugin down from github
                getPlugin(uri, pluginSavePath);
            }

            pluginSavePath = "/Users/cody/IdeaProjects/filerepo/target/filerepo-1.1-SNAPSHOT.jar";

            //Upload plugin to repo
            Map<String, String> fileRepoMap = crescoClient.globalcontroller.upload_plugin_global(pluginSavePath);

            //Get details about plugin
            String fileRepoConfigParamsString = crescoClient.messaging.getCompressedParam(fileRepoMap.get("configparams"));
            Map<String, String> fileRepoConfigParams = crescoClient.messaging.getMapFromString(fileRepoConfigParamsString);

            //Build the CADL config
            Map<String, Object> cadl = new HashMap<>();
            cadl.put("pipeline_id", "0");
            cadl.put("pipeline_name", pipelineName);
            List<Map<String, Object>> nodes = new ArrayList<>();
            List<Map<String, Object>> edges = new ArrayList<>();

            Map<String, Object> params0 = new HashMap<>();
            params0.put("pluginname", fileRepoConfigParams.get("pluginname"));
            params0.put("md5", fileRepoConfigParams.get("md5"));
            params0.put("version", fileRepoConfigParams.get("version"));
            params0.put("location_region", crescoClient.api.getAPIRegionName());
            params0.put("location_agent", crescoClient.api.getAPIAgentName());
            params0.put("filerepo_name", repo_name_1);
            params0.put("scan_dir", repo_path_1);

            Map<String, Object> node0 = new HashMap<>();
            node0.put("type", "dummy");
            node0.put("node_name", "Plugin 0");
            node0.put("node_id", 0);
            node0.put("isSource", false);
            node0.put("workloadUtil", 0);
            node0.put("params", params0);

            Map<String, Object> params1 = new HashMap<>();
            params1.put("pluginname", fileRepoConfigParams.get("pluginname"));
            params1.put("md5", fileRepoConfigParams.get("md5"));
            params1.put("version", fileRepoConfigParams.get("version"));
            params1.put("location_region", crescoClient.api.getAPIRegionName());
            params1.put("location_agent", crescoClient.api.getAPIAgentName());
            params1.put("filerepo_name", repo_name_1);
            params1.put("scan_dir", repo_path_1);


            nodes.add(node0);

            cadl.put("nodes", nodes);
            cadl.put("edges", edges);

            //Submit pipeline
            Map<String, String> reply = crescoClient.globalcontroller.submit_pipeline("0", cadl);
            //Get pipelineId
            pipelineId = reply.get("gpipeline_id");

            System.out.println("Starting PipelineId: " + pipelineId);

            int app_status = -1;

            while (app_status != 10) {

                //get identifier for application
                gPayload fileRepoDeployStatus = crescoClient.globalcontroller.get_pipeline_info(pipelineId);
                app_status = Integer.parseInt(fileRepoDeployStatus.status_code);
                Thread.sleep(1000);
            }
            System.out.println("Started PipelineId: " + pipelineId);
        }

        return pipelineId;

    }

    public String getPipelineIdByName(String pipelineName) {

        String pipelineId = null;

        List<Map<String,String>> pipelineList = crescoClient.globalcontroller.get_pipeline_list();
        for(Map<String,String> pipeline : pipelineList) {
            if(pipeline.get("pipeline_name").equals(pipelineName)) {
                pipelineId = pipeline.get("pipeline_id");
            }
        }

        return pipelineId;

    }

    public void getPlugin(String uri, String fileName) {

        try{

            URI srcUri = URI.create(uri);

            SslContextFactory ssl = new SslContextFactory(true);

            HttpClient client = new HttpClient(ssl);
            try
            {
                client.start();

                Request request = client.newRequest(srcUri);

                //System.out.printf("Using HttpClient v%s%n", getHttpClientVersion());
                System.out.printf("Requesting: %s%n", srcUri);
                InputStreamResponseListener streamResponseListener = new InputStreamResponseListener();
                request.send(streamResponseListener);
                Response response = streamResponseListener.get(5, TimeUnit.SECONDS);

                if (response.getStatus() != HttpStatus.OK_200)
                {
                    throw new IOException(
                            String.format("Failed to GET URI [%d %s]: %s",
                                    response.getStatus(),
                                    response.getReason(),
                                    srcUri));
                }

                Path filePath = Paths.get(fileName);

                try (InputStream inputStream = streamResponseListener.getInputStream();
                     OutputStream outputStream = Files.newOutputStream(filePath))
                {
                    IO.copy(inputStream, outputStream);
                }

                System.out.printf("Downloaded %s%n", srcUri);
                System.out.printf("Destination: %s (%,d bytes)%n", filePath.toString(), Files.size(filePath));
            }
            finally
            {
                client.stop();
            }


        } catch (Exception ex) {
            ex.printStackTrace();
        }


    }

    private DataPlaneInterface getTransferStreamer(String identKey, String identId) {

        DataPlaneInterface transferStreamer = null;
        try {

            Map<String, String> configDB = new HashMap<>();
            configDB.put("ident_key", identKey);
            configDB.put("ident_id", identId);
            configDB.put("io_type_key", "type");
            configDB.put("output_id", "output");
            configDB.put("input_id", "output");
            Gson gson = new Gson();

            String queryString = gson.toJson(configDB);

            transferStreamer = crescoClient.getDataPlane(queryString, new TransferCallback());
            transferStreamer.start();
            while(!transferStreamer.connected()) {
                Thread.sleep(1000);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return  transferStreamer;
    }

    class TransferCallback implements OnMessageCallback {

        @Override
        public void onMessage(String msg) {
            System.out.println("TransferCallback why TEXT MESSAGE? " + msg);
        }

        @Override
        public void onMessage(byte[] b, int offset, int length) {
            try {

                ByteBuffer buffer = ByteBuffer.wrap(b);
                byte[] bytesId = new byte[32];
                buffer.get(bytesId);
                String transferId = new String(bytesId);
                long dataBufferSize = b.length - 32;
                byte[] bytesData = new byte[(int)dataBufferSize];
                buffer.get(bytesData);
                synchronized (ioLock) {
                    if(transferStreams.containsKey(transferId)) {
                        long bytesRemaining = transferStreams.get(transferId).getBytesRemaining();
                        long bytesTotal = transferStreams.get(transferId).getBytesLength();
                        transferStreams.get(transferId).setBytesRemaining(dataBufferSize);
                        transferStreams.get(transferId).setTransferedPackets();
                        //if((bytesTotal - bytesRemaining) > (32 * 1024 * 1024)) {
                            if (!transferStreams.get(transferId).isCanceled()) {
                                transferStreams.get(transferId).setCanceled(true);
                                cancel(transferId, transferStreams.get(transferId).getRepoRegion(), transferStreams.get(transferId).getRepoAgent(), transferStreams.get(transferId).getRepoPlugin());
                            }
                        //}
                        if(!transferStreams.get(transferId).isCanceled()) {
                            System.out.println("transferid: [" + transferId + "] transfered bytes: " + (bytesTotal - bytesRemaining) + " packets:" + transferStreams.get(transferId).getTransferedPackets() + " canceled:" + transferStreams.get(transferId).isCanceled());
                        }
                    } else {
                        System.out.println("[" + transferId + "] BAD TRANSFER ID ");
                    }
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                //System.exit(0);
            }
        }
    }

    public void cancel(String transferId, String repoRegion, String repoAgent, String repoPlugin) {

        try {

            new Thread() {
                public void run() {
                    try {

                        String message_event_type = "EXEC";
                        Map<String, Object> message_payload = new HashMap<>();
                        message_payload.put("action", "streamfilecancel");
                        message_payload.put("transfer_id", transferId);

                        Map<String,String> response = crescoClient.messaging.global_plugin_msgevent(true, message_event_type, message_payload, repoRegion, repoAgent, repoPlugin);
                        System.out.println("close transferFile THREAD MSG SENT: " + Thread.currentThread().getId() + " response: " + response);

                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.start();

        } catch (Exception ex) {
            System.out.println("start() " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void start(String identKey, String identId, String transferId, String fileName, long startByte, long byteLength, String repoRegion, String repoAgent, String repoPlugin) {

        try {

            new Thread() {
                public void run() {
                    try {

                        String message_event_type = "EXEC";
                        Map<String, Object> message_payload = new HashMap<>();
                        message_payload.put("action", "streamfile");
                        message_payload.put("transfer_id", transferId);
                        message_payload.put("file_path", fileName);
                        message_payload.put("start_byte", String.valueOf(startByte));
                        message_payload.put("byte_length", String.valueOf(byteLength));
                        message_payload.put("ident_key", identKey);
                        message_payload.put("ident_id", identId);
                        message_payload.put("io_type_key", "type");
                        message_payload.put("output_id", "output");
                        message_payload.put("input_id", "output");

                        System.out.println("T: " + Thread.currentThread().getId() + " PipedInputStream transferFile START 1 THREAD MSG SENDING request:" + message_payload);
                        Map<String, String> response = crescoClient.messaging.global_plugin_msgevent(true, message_event_type, message_payload, repoRegion, repoAgent, repoPlugin);
                        System.out.println("T: " + Thread.currentThread().getId() + " PipedInputStream transferFile START 2 THREAD MSG SENT response: " + response);
                        System.out.println("-");


                    } catch(Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }.start();

        } catch (Exception ex) {
            System.out.println("start() " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void launchThread(DataPlaneInterface dataPlaneSend, String input) {

        new Thread() {
            public void run() {
                try {
                    //while(true) {
                        for(int i = 0; i<10; i++) {
                            //Thread.sleep(1000);
                            //dataPlaneSend.send(str);
                            dataPlaneSend.send(input);
                        }
                        Thread.sleep(45 * 1000);

                        for(int i = 0; i<10; i++) {
                            //Thread.sleep(1000);
                            //dataPlaneSend.send(str);
                            dataPlaneSend.send(input);
                        }
                        System.out.println("ENDED");
                        //dataPlaneSend.send(input);
                        //Thread.sleep(1000);
                    //}

                } catch(Exception v) {
                    System.out.println(v);
                }
            }
        }.start();

    }

}
