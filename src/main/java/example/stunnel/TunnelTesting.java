package example.stunnel;

import crescoclient.CrescoClient;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stunnel example: build tunnels through the client library's {@code stunnel} submodule — no CADL
 * pipeline, no plugin-id lookups, no hand-built CONFIG messages.
 *
 * <p>Tunnels come from the {@code "sTunnel"} section of the launcher config (see
 * {@code config.example.json}):
 * <pre>{@code
 * "sTunnel": {
 *   "plugin_jar": "",            // optional local stunnel jar; empty = the copy embedded in the agent
 *   "remove_on_exit": true,
 *   "tunnels": [
 *     { "stunnel_id": "example-tunnel",
 *       "src_region": "global-region", "src_agent": "global-controller", "src_port": "5202",
 *       "dst_region": "global-region", "dst_agent": "global-controller",
 *       "dst_host": "localhost", "dst_port": "5201", "buffer_size": "65536" } ]
 * }
 * }</pre>
 * Region/agent fields left empty default to the agent hosting the wsapi endpoint, so the default config
 * runs a single-node tunnel. With {@code "echo_test": true} the example also starts a local echo server on
 * {@code dst_port} and round-trips a message through {@code localhost:src_port} (only meaningful when the
 * client runs on the source agent's host and the destination resolves to this host).
 *
 * <p>Based on the config-file driven tunnel test contributed by Mitch Klusty (CrescoEdge/clientlib#3).
 */
public class TunnelTesting {

    private final CrescoClient client;

    public TunnelTesting(CrescoClient client) {
        this.client = client;
    }

    /** Run the example for every tunnel in the {@code sTunnel} config section. */
    @SuppressWarnings("unchecked")
    public void tunnelTest(Map<String, Object> sTunnelConfig) throws Exception {
        if (sTunnelConfig == null) sTunnelConfig = new HashMap<>();
        String pluginJar = str(sTunnelConfig.get("plugin_jar"), null);
        boolean removeOnExit = !Boolean.FALSE.equals(sTunnelConfig.get("remove_on_exit"));
        boolean echoTest = Boolean.TRUE.equals(sTunnelConfig.get("echo_test"));

        List<Map<String, Object>> tunnels = (List<Map<String, Object>>) sTunnelConfig.get("tunnels");
        if (tunnels == null || tunnels.isEmpty()) {
            tunnels = new ArrayList<>();
            tunnels.add(new HashMap<>());   // one all-defaults tunnel
        }

        String apiRegion = client.api.get_api_region_name();
        String apiAgent = client.api.get_api_agent_name();

        for (Map<String, Object> t : tunnels) {
            String stunnelId = str(t.get("stunnel_id"), "example-tunnel");
            String srcRegion = str(t.get("src_region"), apiRegion);
            String srcAgent = str(t.get("src_agent"), apiAgent);
            String srcPort = str(t.get("src_port"), "5202");
            String dstRegion = str(t.get("dst_region"), apiRegion);
            String dstAgent = str(t.get("dst_agent"), apiAgent);
            String dstHost = str(t.get("dst_host"), "localhost");
            String dstPort = str(t.get("dst_port"), "5201");
            String bufferSize = str(t.get("buffer_size"), "65536");

            // 1. Make sure the stunnel plugin runs on both ends (no-op if it already does).
            String srcPlugin = client.stunnel.deploy_plugin(srcRegion, srcAgent, pluginJar);
            String dstPlugin = client.stunnel.deploy_plugin(dstRegion, dstAgent, pluginJar);
            System.out.println("stunnel plugins: src=" + srcPlugin + " dst=" + dstPlugin);
            if (srcPlugin == null || dstPlugin == null) {
                System.out.println("could not load the stunnel plugin; skipping " + stunnelId);
                continue;
            }

            ServerSocket echo = echoTest ? startEcho(Integer.parseInt(dstPort)) : null;
            try {
                // 2. Create the tunnel: listen on srcAgent:srcPort, forward to dstHost:dstPort from dstAgent.
                Map<String, String> created = client.stunnel.create_tunnel(stunnelId,
                        srcRegion, srcAgent, srcPort, dstRegion, dstAgent, dstHost, dstPort, bufferSize);
                System.out.println("create_tunnel: " + created);

                // 3. Inspect it.
                System.out.println("status (src): " + client.stunnel.get_tunnel_status(srcRegion, srcAgent, stunnelId));
                System.out.println("status (dst): " + client.stunnel.get_tunnel_status(dstRegion, dstAgent, stunnelId));
                System.out.println("tunnels on src agent: " + client.stunnel.get_tunnel_list(srcRegion, srcAgent));
                System.out.println("config: " + client.stunnel.get_tunnel_config(srcRegion, srcAgent, stunnelId));

                // 4. Optionally push bytes through it.
                if (echo != null) {
                    System.out.println("echo through tunnel: " + echoRoundTrip(Integer.parseInt(srcPort), "hello stunnel"));
                }
            } finally {
                // 5. Tear it down (both ends).
                if (removeOnExit) {
                    System.out.println("remove_tunnel: " + client.stunnel.remove_tunnel(stunnelId,
                            srcRegion, srcAgent, dstRegion, dstAgent));
                }
                if (echo != null) echo.close();
            }
        }
    }

    private static String str(Object o, String dflt) {
        return (o == null || o.toString().isEmpty()) ? dflt : o.toString();
    }

    /** Minimal echo server standing in for the tunnelled service. */
    private static ServerSocket startEcho(int port) throws Exception {
        ServerSocket ss = new ServerSocket(port);
        Thread th = new Thread(() -> {
            while (!ss.isClosed()) {
                try (Socket s = ss.accept()) {
                    InputStream in = s.getInputStream();
                    OutputStream out = s.getOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                        out.flush();
                    }
                } catch (Exception ignore) {
                    // closed
                }
            }
        }, "stunnel-example-echo");
        th.setDaemon(true);
        th.start();
        return ss;
    }

    private static String echoRoundTrip(int port, String msg) throws Exception {
        try (Socket s = new Socket("localhost", port)) {
            s.setSoTimeout(10000);
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            s.getOutputStream().write(data);
            s.getOutputStream().flush();
            byte[] back = s.getInputStream().readNBytes(data.length);
            return new String(back, StandardCharsets.UTF_8);
        }
    }
}
