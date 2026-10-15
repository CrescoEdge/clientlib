package example;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import crescoclient.CrescoClient;
import crescoclient.core.OnMessageCallback;
import crescoclient.dataplane.DataPlaneInterface;
import example.stunnel.TunnelTesting;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * Config-file driven launcher for the client examples.
 *
 * <pre>
 *   java -cp target/clientlib-1.3-SNAPSHOT.jar example.Launcher [config.json]
 * </pre>
 *
 * The config file (default {@code config.json}; copy {@code config.example.json}) supplies
 * {@code host}, {@code port}, {@code service_key} and an {@code example} to run ({@code stunnel} or
 * {@code dataplane}). Missing keys fall back to the environment ({@code CRESCO_HOST}, {@code CRESCO_PORT},
 * {@code CRESCO_SERVICE_KEY}) and then to localhost:8282. Keep real service keys out of version control:
 * {@code config.json} is git-ignored.
 *
 * <p>Config-file loading contributed by Mitch Klusty (CrescoEdge/clientlib#3).
 */
public class Launcher {

    public static void main(String[] args) throws Exception {

        String configFilePath = args.length > 0 ? args[0] : "config.json";
        Map<String, Object> config = loadConfig(configFilePath);

        String host = setting(config, "host", "CRESCO_HOST", "localhost");
        int port = Integer.parseInt(setting(config, "port", "CRESCO_PORT", "8282"));
        String serviceKey = setting(config, "service_key", "CRESCO_SERVICE_KEY", null);
        String example = setting(config, "example", null, "stunnel");

        if (serviceKey == null) {
            System.out.println("No service_key: set it in " + configFilePath + " or CRESCO_SERVICE_KEY.");
            return;
        }

        CrescoClient client = new CrescoClient(host, port, serviceKey);
        if (!client.connect()) {
            System.out.println("Could not connect to " + host + ":" + port);
            return;
        }

        try {
            switch (example) {
                case "stunnel":
                    new TunnelTesting(client).tunnelTest(asMap(config.get("sTunnel")));
                    break;
                case "dataplane":
                    runDataplaneListener(client, setting(config, "dataplane_query", null,
                            "region_id IS NOT NULL AND agent_id IS NOT NULL"));
                    break;
                default:
                    System.out.println("Unknown example '" + example + "' (expected stunnel or dataplane)");
            }
        } finally {
            client.close();
        }
    }

    /** Print every dataplane message matching the query until the process is stopped. */
    private static void runDataplaneListener(CrescoClient client, String query) throws InterruptedException {
        DataPlaneInterface dp = client.get_dataplane(query, new OnMessageCallback() {
            @Override
            public void onMessage(String msg) {
                System.out.println("text: " + msg);
            }

            @Override
            public void onMessage(byte[] b, int offset, int length) {
                System.out.println("binary: " + length + " bytes");
            }
        });
        dp.connect();
        while (!dp.connected()) {
            Thread.sleep(1000);
        }
        System.out.println("dataplane connected: " + query);
        while (true) {
            Thread.sleep(1000);
        }
    }

    private static String setting(Map<String, Object> config, String key, String envName, String dflt) {
        Object v = config.get(key);
        if (v != null && !v.toString().isEmpty()) {
            // Gson parses bare JSON numbers as doubles (8282 -> 8282.0)
            if (v instanceof Double && ((Double) v) == Math.rint((Double) v)) {
                return String.valueOf(((Double) v).longValue());
            }
            return v.toString();
        }
        if (envName != null) {
            String e = System.getenv(envName);
            if (e != null && !e.isEmpty()) return e;
        }
        return dflt;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : new HashMap<>();
    }

    private static Map<String, Object> loadConfig(String configFilePath) throws Exception {
        if (configFilePath == null || !new File(configFilePath).isFile()) {
            System.out.println("No config file at " + configFilePath + "; using environment/defaults.");
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, Object>>() {}.getType();
        String json = new String(Files.readAllBytes(Paths.get(configFilePath)), StandardCharsets.UTF_8);
        Map<String, Object> config = new Gson().fromJson(json, type);
        return config != null ? config : new HashMap<>();
    }
}
