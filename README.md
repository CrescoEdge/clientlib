# Cresco Java Client (clientlib)

Java client library for the **[Cresco](https://github.com/CrescoEdge/agent)** edge-computing framework. It connects
to the [wsapi](https://github.com/CrescoEdge/wsapi) WebSocket plugin (`wss://host:8282`, authenticated with the
`cresco_service_key` header) to control agents, deploy plugins, run pipelines, stream the
data plane, and build stunnel tunnels.

Part of the Cresco framework — see the **[agent repository](https://github.com/CrescoEdge/agent)** for the full
architecture and the [Python client](https://github.com/CrescoEdge/pycrescolib) for the equivalent in Python.

> **Standardized clients.** The Python and Java clients are kept feature- and name-identical: the same
> submodules (`messaging`, `agents`, `admin`, `api`, `globalcontroller`, `stunnel`), the same method names (snake_case
> in both languages), the same behavior, and the same examples. Anything you can do in one, you can do in the
> other with the same call. The API reference below is shared with the Python client's README; the quick
> start and usage guide are Java-specific, and a few Java-first additions are marked.

**Docs:** [Java client guide](https://crescoedge.github.io/docs/clients/java/) on the Cresco docs site, the
[usage guide](#usage-guide) below, and Javadoc on every public class and method.

**Build:** `mvn package` (JDK 21) → shaded `target/clientlib-1.3-SNAPSHOT.jar`.

## Quick start

```java
import crescoclient.CrescoClient;

// host/ip of an agent running the wsapi plugin, its port, and the wsapi service key
CrescoClient client = new CrescoClient("localhost", 8282, "your-service-key", false);

if (client.connect()) {
    System.out.println(client.api.get_api_region_name() + " " + client.api.get_api_agent_name());
    System.out.println(client.globalcontroller.get_agent_list(null));
    client.close();
}
```

Runnable, standardized examples (matching the Python client's `examples/standard_examples.py`)
live in [`example/standard/StandardExamples.java`](src/main/java/example/standard/StandardExamples.java): `example_connect`, `example_pipeline`,
`example_plugin_lifecycle`, `example_dataplane`, `example_logstreamer`, `example_admin`, plus the Java-first
`example_messaging` and `example_stunnel`. See the [usage guide](#usage-guide) below.

## Usage guide

Every snippet below assumes a connected client. Runnable versions live in
[`StandardExamples.java`](src/main/java/example/standard/StandardExamples.java); run one with
`java -cp target/clientlib-1.3-SNAPSHOT.jar example.standard.StandardExamples <name> [args]` where `<name>` is
`connect`, `pipeline`, `plugin_lifecycle`, `dataplane`, `logstreamer`, `admin`, `messaging` or `stunnel`. They read
`CRESCO_HOST`, `CRESCO_PORT` and `CRESCO_SERVICE_KEY` from the environment.

```java
CrescoClient client = new CrescoClient("localhost", 8282, System.getenv("CRESCO_SERVICE_KEY"));
if (!client.connect()) throw new IllegalStateException("cannot reach wsapi");
```

### List the mesh

```java
System.out.println(client.api.get_api_region_name() + "/" + client.api.get_api_agent_name()); // the agent you are connected to
System.out.println(client.globalcontroller.get_region_list());       // {regions=[{name=..., agents=...}]}
for (Map<String, String> a : client.globalcontroller.get_agent_list(null).get("agents")) {
    System.out.println(a.get("region_id") + "/" + a.get("agent_id") + " plugins=" + a.get("plugins"));
}
System.out.println(client.globalcontroller.list_plugins("global-region", "global-controller"));
```

### Send a MsgEvent

The submodules wrap the common actions; anything else is one `messaging` call. The `action` param selects the
handler on the receiver, the event type (`EXEC`, `CONFIG`, ...) must match what the handler expects, and large
reply values come back compressed.

```java
Map<String, Object> payload = new HashMap<>();
payload.put("action", "listregions");
Map<String, String> reply = client.messaging.global_controller_msgevent(true, "EXEC", payload);
System.out.println(client.messaging.getCompressedParam(reply.get("regionslist")));

// to a plugin on any agent: resolve its id by name, then address it
String repo = client.globalcontroller.find_plugin("global-region", "global-controller", "io.cresco.repo");
Map<String, Object> p = new HashMap<>();
p.put("action", "repolist");
client.messaging.global_plugin_msgevent(true, "EXEC", p, "global-region", "global-controller", repo);
```

An RPC returns `null` if no reply arrives within 30 s.

### Data plane

A dataplane is its own WebSocket. Its first frame is the stream handshake: a JSON stream config (send and
receive) or a selector such as `stream_name='demo'` (receive only). Messages sent on a stream are tagged
`ident_key=ident_id`, and every stream whose handshake selects that tag receives them.

```java
String stream = "{\"ident_key\":\"stream_name\",\"ident_id\":\"demo\",\"io_type_key\":\"type\",\"input_id\":\"in\"}";
DataPlaneInterface dp = client.get_dataplane(stream, new OnMessageCallback() {
    public void onMessage(String msg) { System.out.println("text: " + msg); }
    public void onMessage(byte[] b, int off, int len) { System.out.println("binary: " + len + " bytes"); }
});
dp.connect();
dp.send("hello dataplane");
dp.send_binary(ByteBuffer.wrap(new byte[]{0, 1, 2, 3}));
client.close_dataplane(stream);
```

### Log streaming

```java
LogStreamerInterface ls = client.get_logstreamer("logs", callback);   // same OnMessageCallback shape
ls.connect();
ls.update_config("global-region", "global-controller");               // stream that agent's logs
ls.update_config_class("global-region", "global-controller", "Debug", "io.cresco.agent");
client.close_logstreamer("logs");
```

### Tunnels (stunnel)

```java
// 1. make sure the stunnel plugin runs on both ends (loads the copy embedded in the agent if needed)
client.stunnel.deploy_plugin("global-region", "agent-a");
client.stunnel.deploy_plugin("global-region", "agent-b");

// 2. listen on agent-a:2222 and forward to localhost:22 as seen from agent-b
Map<String, String> cfg = client.stunnel.create_tunnel("ssh", "global-region", "agent-a", "2222",
        "global-region", "agent-b", "localhost", "22", "65536");

// 3. inspect and tear down
client.stunnel.get_tunnel_status("global-region", "agent-a", "ssh");  // ACTIVE
client.stunnel.get_tunnel_list("global-region", "agent-a");           // [{stunnel_id=ssh, status=ACTIVE}]
client.stunnel.remove_tunnel("ssh", "global-region", "agent-a", "global-region", "agent-b");
```

[`example.Launcher`](src/main/java/example/Launcher.java) runs the same flow for a list of tunnels read from a
JSON config file (`cp config.example.json config.json`, set `service_key`, then
`java -cp target/clientlib-1.3-SNAPSHOT.jar example.Launcher config.json`); with `"echo_test": true` it also pushes
bytes through each tunnel.

### Javadoc

Every public class and method is documented. Build the HTML API reference with

```sh
mvn -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
javadoc -d target/apidocs -sourcepath src/main/java -subpackages crescoclient -cp "$(cat target/cp.txt)"
```

## Concepts

- **Connect once, use submodules.** A `clientlib` opens one authenticated control connection and exposes six
  submodule handles: `messaging`, `agents`, `admin`, `api`, `globalcontroller`, `stunnel`.
- **Resolve plugins by name, not by log-scraping.** `globalcontroller.find_plugin(region, agent, name)` returns the
  live `plugin_id` for a plugin loaded on an agent, so you never parse logs to address a plugin.
- **Streams are separate sockets.** `get_dataplane(...)` and `get_logstreamer(...)` open their own WebSocket
  connections, are tracked by name on the client, and auto-reconnect if the socket drops.
- **RPC vs fire-and-forget.** Messaging calls take an `is_rpc` flag: when true the call blocks for the reply,
  when false it is sent without waiting.

## API reference

Signatures are shown language-neutrally; names and behavior are identical in both clients.

### Client — `clientlib` / `CrescoClient`

| Method | Description |
|---|---|
| `clientlib(host, port, service_key, verify_ssl=False)` | Create the client for a wsapi endpoint. |
| `connect(block_on_connect=True) -> bool` | Connect to the wsapi endpoint; block until connected when `block_on_connect`. |
| `connected() -> bool` | Return True if connected to the wsapi endpoint. |
| `close() -> bool` | Close the client connection and all managed streams. |
| `connection()` | Return the underlying websocket transport interface. |
| `get_dataplane(stream_name, callback=None)` | Get (and register) a dataplane for the given stream query. |
| `close_dataplane(stream_name) -> bool` | Close and deregister the named dataplane. |
| `get_active_dataplanes() -> list` | List names of currently registered dataplanes. |
| `get_logstreamer(name=None, callback=None)` | Get (and register) a logstreamer. |
| `close_logstreamer(name) -> bool` | Close and deregister the named logstreamer. |
| `get_active_logstreamers() -> list` | List names of currently registered logstreamers. |

Submodule handles: `messaging`, `agents`, `admin`, `api`, `globalcontroller`, `stunnel`.

### `api`

| Method | Description |
|---|---|
| `get_api_region_name()` | Region name of the agent hosting the wsapi plugin. |
| `get_api_agent_name()` | Agent name of the agent hosting the wsapi plugin. |
| `get_api_plugin_name()` | Plugin name of the wsapi plugin. |
| `get_global_region()` | Global controller region. |
| `get_global_agent()` | Global controller agent. |
| `get_global_info()` | Pair of (global_region, global_agent). |

### `admin`

| Method | Description |
|---|---|
| `stopcontroller(dst_region, dst_agent)` | Stop the agent's controller plugin. |
| `restartcontroller(dst_region, dst_agent)` | Restart the agent's controller plugin. |
| `restartframework(dst_region, dst_agent)` | Restart the underlying OSGi framework. |
| `killjvm(dst_region, dst_agent)` | Force-terminate the agent JVM. |

### `agents`

| Method | Description |
|---|---|
| `is_controller_active(dst_region, dst_agent)` | Whether the target controller is active. |
| `get_controller_status(dst_region, dst_agent)` | Controller status. |
| `add_plugin_agent(dst_region, dst_agent, configparams, edges=None)` | Add a plugin to an agent. |
| `remove_plugin_agent(dst_region, dst_agent, plugin_id)` | Remove a plugin from an agent. |
| `list_plugin_agent(dst_region, dst_agent)` | List plugins on an agent. |
| `status_plugin_agent(dst_region, dst_agent, plugin_id)` | Get plugin status. |
| `get_agent_info(dst_region, dst_agent)` | Get agent information. |
| `get_agent_log(dst_region, dst_agent)` | Get agent logs. |
| `repo_pull_plugin_agent(dst_region, dst_agent, jar_file_path)` | Pull a plugin jar to the agent repo. |
| `upload_plugin_agent(dst_region, dst_agent, jar_file_path)` | Upload a plugin jar to the agent. |
| `update_plugin_agent(dst_region, dst_agent, jar_file_path)` | Update a plugin on the agent. |
| `get_broadcast_discovery(dst_region, dst_agent)` | Get broadcast discovery from an agent. |
| `cepadd(input_stream, input_stream_desc, output_stream, output_stream_desc, query, dst_region, dst_agent)` | Add a complex-event-processing query. |

### `globalcontroller`

| Method | Description |
|---|---|
| `submit_pipeline(cadl, tenant_id='0')` | Submit a CADL pipeline. |
| `remove_pipeline(pipeline_id)` | Remove a pipeline. |
| `get_pipeline_list()` | List pipelines. |
| `get_pipeline_info(pipeline_id)` | Get pipeline information. |
| `get_pipeline_status(pipeline_id)` | Get pipeline status code. |
| `get_pipeline_id_by_name(pipeline_name)` | Look up a pipeline ID by name. |
| `get_pipeline_export(pipeline_id)` | Export a pipeline. |
| `get_pipeline_is_assignment_info(inode_id, resource_id)` | Get inode-to-resource assignment info. |
| `get_agent_list(dst_region=None)` | List agents (optionally in one region). |
| `get_agent_resources(dst_region, dst_agent)` | Get an agent's resource info. |
| `get_region_resources(dst_region)` | Get a region's resource info. |
| `get_region_list()` | List regions. |
| `get_plugin_repo_list()` | List plugins available in the repositories. |
| `get_repo_plugins()` | List plugins known to the repositories. |
| `list_plugins(dst_region, dst_agent)` | List the plugins loaded on an agent (via the global controller — reliable from anywhere in the mesh). |
| `find_plugin(dst_region, dst_agent, pluginname)` | Resolve the live `plugin_id` for a plugin loaded on an agent by its plugin name (or `null`). |
| `upload_plugin_global(jar_file_path)` | Upload a plugin jar to the global repo. |
| `get_metric_inventory(scope, dst_region, dst_agent, include_plugins, include_resource)` | Pull the fabric's unified metric inventory (controller + plugin metrics, optional resource summary). |
| `get_capability_inventory(scope, dst_region, dst_agent, include_plugins, include_osgi)` | Pull the fabric's self-describing capability catalog (LLM-facing tool descriptors). |

> The metric/capability inventories take `scope` = `'node'`, `'region'`, or `'global'`; pass
> `dst_region`+`dst_agent` to target one agent's controller. (In the Java client these two methods
> currently order the positional args as `dst_region, dst_agent, scope, …`.)

### `stunnel`

Build and manage secure TCP tunnels through the [io.cresco.stunnel](https://github.com/CrescoEdge/stunnel)
plugin, with no CADL pipeline: a listener on `src_port` at the source agent forwards every accepted connection to
`dst_host:dst_port` reachable from the destination agent, with tunnel data riding the Cresco data plane. Plugin ids
are auto-resolved by name via `find_plugin`; every method that takes a trailing `plugin_id` also accepts `null`.

| Method | Plugin action | Description |
|---|---|---|
| `find_plugin(region, agent)` | — | Resolve the `io.cresco.stunnel` plugin_id loaded on an agent (or `null`). |
| `deploy_plugin(region, agent[, jar_file_path])` | `pluginadd` (+`pluginupload`) | Java: ensure the plugin runs on an agent (the copy embedded in the agent, or an uploaded jar); returns its plugin_id. |
| `create_tunnel(stunnel_id, src_region, src_agent, src_port, dst_region, dst_agent, dst_host, dst_port, buffer_size[, src_plugin_id, dst_plugin_id[, watchdog_timeout, performance_report_rate]])` | CONFIG `configsrctunnel` | Create a tunnel. Returns the resolved tunnel config on success, the plugin reply (`status`, `status_desc`) on failure. |
| `get_tunnel_list(region, agent)` | EXEC `listtunnels` | `[{stunnel_id, status}]` for the tunnels on an agent (empty if none / plugin missing). |
| `get_tunnel_status(region, agent, stunnel_id)` | EXEC `gettunnelstatus` | `ACTIVE`, `RECOVERING`, `DOWN` or `UNKNOWN` (`null` if not configured there). |
| `get_tunnel_config(region, agent, stunnel_id)` | EXEC `gettunnelconfig` | The tunnel config the plugin persisted (restored on plugin restart). |
| `tunnel_health_check(region, agent, stunnel_id)` | EXEC `tunnelhealthcheck` | Java: whether the tunnel is configured on that agent. |
| `get_metrics(region, agent)` | EXEC `getmetrics` | Java: the plugin's live metrics (per tunnel throughput, sessions, flow control). |
| `set_net_tuning(region, agent, tuning)` | CONFIG `nettuning` | Java: plugin-wide socket tuning (`net_socket_buffer_bytes`, `net_read_chunk_bytes`, `net_write_high_water_bytes`, `net_fc_window_bytes`, `net_trace_sample_n`). |
| `remove_tunnel(stunnel_id, src_region, src_agent, dst_region, dst_agent)` | `removesrctunnel` + `removedsttunnel` | Remove both ends; returns `{stunnel_id, src_removal, dst_removal, fully_removed}`. |
| `remove_src_tunnel(region, agent, stunnel_id)` | CONFIG `removesrctunnel` | Remove only the source end (the plugin cascades the matching dst removal). |
| `remove_dst_tunnel(region, agent, stunnel_id)` | CONFIG `removedsttunnel` | Remove only the destination end. |

Methods marked "Java" are Java-only for now; the rest match pycrescolib.

### `messaging`

All take `(is_rpc, message_event_type, message_payload, ...)` and return the reply dict for RPC calls.

| Method | Routes to |
|---|---|
| `global_controller_msgevent(...)` | the global controller |
| `regional_controller_msgevent(...)` | the regional controller |
| `global_agent_msgevent(..., dst_region, dst_agent)` | an agent via the global controller |
| `regional_agent_msgevent(..., dst_agent)` | an agent in the local region |
| `agent_msgevent(...)` | the local agent |
| `global_plugin_msgevent(..., dst_region, dst_agent, dst_plugin)` | a plugin via the global controller |
| `regional_plugin_msgevent(..., dst_agent, dst_plugin)` | a plugin in the local region |
| `plugin_msgevent(..., dst_plugin)` | a local plugin |

### dataplane (from `get_dataplane`)

| Method | Description |
|---|---|
| `send(message)` | Send a text message (needs a JSON stream config handshake). |
| `send_binary(data)` | Send a binary message. |
| `send_binary_file(file_path)` | Read and send a binary file. |
| `send_partial(data, complete)` | Send a payload as a fragmented message (flushed when `complete`). |
| `update_config(dst_region, dst_agent)` | Update the dataplane stream configuration. |
| `connect()` | Open the stream. |
| `connected()` | Whether the stream is connected. |
| `close()` | Close the stream. |
| `get_metrics()` | Client-side counters for this stream (messages/bytes sent + received, active). |

### logstreamer (from `get_logstreamer`)

| Method | Description |
|---|---|
| `send(message)` | Send a message over the log stream. |
| `update_config(dst_region, dst_agent)` | Point the log stream at an agent. |
| `update_config_class(dst_region, dst_agent, loglevel, baseclass)` | Set the log level for one base class. |
| `connect()` | Open the stream. |
| `connected()` | Whether the stream is connected. |
| `close()` | Close the stream. |

## SSL verification

By default SSL certificate verification is disabled (Cresco agents use self-signed certs). Enable it with the
constructor flag:

```java
CrescoClient client = new CrescoClient("localhost", 8282, "your-service-key", true);
```

## Testing & cross-language parity

Both clients ship a self-contained test suite (no live mesh needed) that asserts they emit
**identical wire messages for identical API calls** — the Python and Java suites assert the same
generated golden corpus, so a green run on both proves the clients produce the same results.

- **Python:** `pip install -e .[test] && pytest`
- **Java:** `mvn test`

See the [test-suite README](src/test/README.md) for coverage, the canonical wire contract, and how
to regenerate the golden corpus after adding or changing a method.

## License

See [LICENSE.txt](LICENSE.txt).
