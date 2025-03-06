# Cresco Platform Client Library

## What is Cresco?

Cresco is a distributed agent-based platform designed to address the challenges of edge computing. It is a multi-agent, multi-platform, and multi-protocol system that enables the rapid development of high-level edge computing applications across diverse operating environments. The platform consists of four layers:

1. Distributed Data and Infrastructure Layer: Agents run across a wide range of devices, from embedded systems to servers, providing a platform for multi-protocol communication and launching points for plugins.
2. Data Transport and Integration Service Bus Layer: Cresco provides communication paths between hosts, enabling multi-hop messaging throughout a distributed infrastructure.
3. Semantic Layer: Cresco provides methods to encode data streams and events with semantic meaning, enabling complex computational networks.
4. Application Layer: Applications can use Cresco for edge functions, build on the platform, or use it to manage or monitor existing applications. Custom plugins can be developed to expand Cresco functions.

Cresco's features include:

* Managing large numbers of geographically distributed services
* Supporting low-latency real-time streaming applications
* Providing resource and workload comparison, performance observation, scheduling, and provisioning
* Allowing for rapid development of high-level edge computing applications
* Providing low-level visibility, communications, and processing across distributed nodes.

## Client Library

Cresco provides numerious core features and plugins that can be used to establish a computational network.  In order for external application to take advantage of agent functions, we provide a client library.  The client library connects to a plugin that provides a websockets interface to the agent and the broader network.  Any Cresco agent is capable of running the websocket [WSAPI](https://github.com/CrescoEdge/wsapi) plugin, which is enabled by default on global controllers.  

The following text describes client library classes and function that can be used to access Cresco resources.  

## CrescoClient

The CrescoClient class is the base class used to access Cresco client functions.

```
# The hostname or IP of the agent running a WSAPI plugin 
String host = "localhost";
# The default port of the WSAPI plugin
int port = 8282;
# The service key defined for the WSAPI interface
String service_key = "some key";

# Create a new client class
CrescoClient client = new CrescoClient(host,port,service_key);
# Connect to the agent 
client.connect();

# Block until the client has connected
if(client.connected()) {
    # Close the connection
    client.close();
}
```

### Admin

These functions are related to the underlying systems that support the agent

#### killjvm

```java 
public void killjvm(String dst_region, String dst_agent)
```
Description: Kill the underlying JVM that is running the agent

#### restartcontroller

```java 
public void restartcontroller(String dst_region, String dst_agent)
```
Description: Restart the embedded agent controller plugin


#### restartframework

```java 
public void restartframework(String dst_region, String dst_agent)
```
Description: Restart the underlying OSGi framework supporting the agent 


### Agents

#### get_controller_status

```java 
public String get_controller_status(String dst_region, String dst_agent) 
```
Description: Reports the status of the controller


#### add_plugin_agent

```java 
Map<String,String> add_plugin_agent(String dst_region, String dst_agent, Map<String,String> configparams, Map<String,String> edges)
```
Description: Method to directly add plugin to an agent outside of a CADL


#### cepadd

```java 
public Map<String,String> cepadd(String input_stream, String input_stream_dec, String output_stream, String output_stream_desc, String query, String dst_region, String dst_agent)
```
Description: Configure complext event processor on the dataplane


#### remove_plugin_agent

```java 
public Map<String,String> remove_plugin_agent(String dst_region, String dst_agent, String plugin_id)
```
Description: Method to directly remove plugin to an agent outside of a CADL

#### get_broadcast_discovery

```java 
public Map<String,String> get_broadcast_discovery(String dst_region, String dst_agent)
```
Description: Get broadcast discovery from agent


#### get_log

```java 
public Map<String,String> get_log(String dst_region, String dst_agent)
```
Description: Get log from remote agent


** Auto generated

# Cresco Client Documentation

## Table of Contents

1. [Introduction](#introduction)
2. [Getting Started](#getting-started)
   - [Installation](#installation)
   - [Initialization](#initialization)
   - [Connection Management](#connection-management)
3. [Core Components](#core-components)
   - [API](#api)
   - [Admin](#admin)
   - [Agents](#agents)
   - [GlobalController](#globalcontroller)
   - [Messaging](#messaging)
4. [Interfaces](#interfaces)
   - [LogStreamerInterface](#logstreamerinterface)
   - [DataPlaneInterface](#dataplaneinterface)
   - [MsgEventInterface](#msgeventinterface)
5. [Common Use Cases](#common-use-cases)
   - [Pipeline Management](#pipeline-management)
   - [Plugin Management](#plugin-management)
   - [Log Streaming](#log-streaming)
   - [Data Plane Operations](#data-plane-operations)
   - [Tunneling](#tunneling)
6. [Reference](#reference)

## Introduction

The Cresco Client is a Java library for interfacing with the Cresco distributed edge computing framework. It provides APIs for managing agents, plugins, pipelines, messaging, logging, and data plane operations within a Cresco environment.

Cresco is a distributed edge computing framework designed for managing and orchestrating applications across multiple nodes, regions, and agents. This client library enables programmatic interaction with the Cresco ecosystem.

## Getting Started

### Installation

Add the Cresco Client library to your project dependencies. If using Maven:

```xml
<dependency>
    <groupId>io.cresco</groupId>
    <artifactId>crescoclient</artifactId>
    <version>1.1-SNAPSHOT</version>
</dependency>
```

### Initialization

Create a `CrescoClient` instance by providing the hostname, port, and service key for the Cresco API endpoint:

```java
import crescoclient.CrescoClient;

public class MyApplication {
    public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 8282;
        String serviceKey = "a6f7f889-2500-46d3-9484-5b6499186456";
        
        CrescoClient client = new CrescoClient(host, port, serviceKey);
        client.connect();
        
        if (client.connected()) {
            // Client is connected, start using it
            System.out.println("Connected to Cresco API");
        } else {
            System.out.println("Failed to connect to Cresco API");
        }
    }
}
```

### Connection Management

The `CrescoClient` provides methods to manage the connection:

```java
// Connect to the API with a 5-second timeout (default)
client.connect();

// Connect with a specific timeout (in seconds)
int timeout = 10;
client.connect(false); // non-blocking connection

// Check connection status
boolean isConnected = client.connected();

// Close the connection when done
client.close();
```

## Core Components

### API

The `API` component provides basic information about the connected Cresco API:

```java
// Get information about the API endpoint
String regionName = client.api.getAPIRegionName();
String agentName = client.api.getAPIAgentName();
String pluginName = client.api.getAPIPluginName();

// Get global controller information
String globalRegion = client.api.getGlobalRegion();
String globalAgent = client.api.getGlobalAgent();

System.out.println("API endpoint: " + regionName + "/" + agentName + "/" + pluginName);
System.out.println("Global controller: " + globalRegion + "/" + globalAgent);
```

### Admin

The `Admin` component provides administrative functions for controllers and agents:

```java
// Stop a controller
client.admin.stopcontroller("global-region", "agent-controller");

// Restart a controller
client.admin.restartcontroller("global-region", "agent-controller");

// Restart the framework on an agent
client.admin.restartframework("global-region", "agent-controller");

// Force terminate the JVM of an agent
client.admin.killjvm("global-region", "agent-controller");
```

### Agents

The `Agents` component allows interaction with Cresco agents:

```java
// Check if a controller is active on an agent
boolean isActive = client.agents.is_controller_active("global-region", "agent-controller");

// Get controller status
String status = client.agents.get_controller_status("global-region", "agent-controller");

// Add Complex Event Processing (CEP) capabilities
Map<String, String> cepResponse = client.agents.cepadd(
    "input_stream", "input_stream_description", 
    "output_stream", "output_stream_description", 
    "SELECT * FROM input_stream", "global-region", "agent-controller"
);

// Get agent logs
Map<String, String> logs = client.agents.get_log("global-region", "agent-controller");
```

#### Plugin Management with Agents

```java
// Upload a plugin to an agent
String jarPath = "/path/to/my-plugin.jar";
Map<String, String> uploadResult = client.agents.upload_plugin_agent(
    "global-region", "agent-controller", jarPath
);

// Add a plugin to an agent
Map<String, String> configParams = new HashMap<>();
configParams.put("param1", "value1");
configParams.put("param2", "value2");

Map<String, String> edges = new HashMap<>();
edges.put("edge1", "value1");

Map<String, String> addResult = client.agents.add_plugin_agent(
    "global-region", "agent-controller", configParams, edges
);

// Remove a plugin from an agent
String pluginId = addResult.get("pluginid");
Map<String, String> removeResult = client.agents.remove_plugin_agent(
    "global-region", "agent-controller", pluginId
);
```

### GlobalController

The `GlobalController` component provides methods for interacting with the global controller, managing pipelines, resources, and plugins:

```java
// Get a list of pipelines
List<Map<String, String>> pipelines = client.globalcontroller.get_pipeline_list();
for (Map<String, String> pipeline : pipelines) {
    System.out.println("Pipeline: " + pipeline.get("pipeline_name") + 
                       " (ID: " + pipeline.get("pipeline_id") + ")");
}

// Get pipeline details
String pipelineId = pipelines.get(0).get("pipeline_id");
gPayload pipelineInfo = client.globalcontroller.get_pipeline_info(pipelineId);

// Get pipeline status
int status = client.globalcontroller.get_pipeline_status(pipelineId);
System.out.println("Pipeline status: " + status);

// Get a list of regions
Map<String, List<Map<String, String>>> regions = client.globalcontroller.get_region_list();
for (Map<String, String> region : regions.get("regions")) {
    System.out.println("Region: " + region.get("name"));
}

// Get a list of agents in a region
String regionName = regions.get("regions").get(0).get("name");
Map<String, List<Map<String, String>>> agents = client.globalcontroller.get_agent_list(regionName);
for (Map<String, String> agent : agents.get("agents")) {
    System.out.println("Agent: " + agent.get("name"));
}
```

#### Pipeline Management with GlobalController

```java
// Create a pipeline CADL (Cresco Application Description Language)
Map<String, Object> cadl = new HashMap<>();
cadl.put("pipeline_id", "0");
cadl.put("pipeline_name", "MyPipeline");

List<Map<String, Object>> nodes = new ArrayList<>();
List<Map<String, Object>> edges = new ArrayList<>();

Map<String, Object> params0 = new HashMap<>();
params0.put("pluginname", "io.cresco.example");
params0.put("version", "1.0");
params0.put("md5", "abc123");
params0.put("location_region", client.api.getAPIRegionName());
params0.put("location_agent", client.api.getAPIAgentName());

Map<String, Object> node0 = new HashMap<>();
node0.put("type", "dummy");
node0.put("node_name", "Plugin 0");
node0.put("node_id", 0);
node0.put("isSource", false);
node0.put("workloadUtil", 0);
node0.put("params", params0);

nodes.add(node0);
cadl.put("nodes", nodes);
cadl.put("edges", edges);

// Submit the pipeline
Map<String, String> response = client.globalcontroller.submit_pipeline("0", cadl);
String newPipelineId = response.get("gpipeline_id");
System.out.println("Created pipeline with ID: " + newPipelineId);

// Wait for the pipeline to become active
while (client.globalcontroller.get_pipeline_status(newPipelineId) != 10) {
    Thread.sleep(1000);
    System.out.println("Waiting for pipeline to become active...");
}
System.out.println("Pipeline is active!");

// Remove a pipeline
boolean removed = client.globalcontroller.remove_pipeline(newPipelineId);
System.out.println("Pipeline removed: " + removed);
```

### Messaging

The `Messaging` component provides methods for sending messages to different parts of the Cresco ecosystem:

```java
// Send a message to the global controller
String messageType = "EXEC";
Map<String, Object> payload = new HashMap<>();
payload.put("action", "listregions");

Map<String, String> response = client.messaging.global_controller_msgevent(
    true, messageType, payload
);

// Send a message to a specific agent
payload.clear();
payload.put("action", "getbroadcastdiscovery");

response = client.messaging.global_agent_msgevent(
    true, messageType, payload, "global-region", "agent-controller"
);

// Send a message to a specific plugin
payload.clear();
payload.put("action", "getmetrics");

response = client.messaging.global_plugin_msgevent(
    true, messageType, payload, "global-region", "agent-controller", "plugin-id"
);
```

## Interfaces

### LogStreamerInterface

The `LogStreamerInterface` provides access to logs from Cresco agents:

```java
// Create a basic log streamer
LogStreamerInterface logStreamer = client.getLogStreamer();
logStreamer.start();

// Wait for connection
while (!logStreamer.connected()) {
    Thread.sleep(1000);
}

// Configure logs to stream from a specific agent
logStreamer.update_config("global-region", "agent-controller");

// Close the log streamer when done
logStreamer.close();
```

### DataPlaneInterface

The `DataPlaneInterface` allows for sending and receiving data over the Cresco data plane:

```java
// Create a data plane interface with a specific query
String query = "region_id IS NOT NULL AND agent_id IS NOT NULL";
DataPlaneInterface dataPlane = client.getDataPlane(query);
dataPlane.start();

// Wait for connection
while (!dataPlane.connected()) {
    Thread.sleep(1000);
}

// Send a text message
dataPlane.send("Hello, Cresco!");

// Close the data plane interface when done
dataPlane.close();
```

Using a callback for received messages:

```java
// Create a callback for handling received messages
class MyCallback implements OnMessageCallback {
    @Override
    public void onMessage(String msg) {
        System.out.println("Received text message: " + msg);
    }
    
    @Override
    public void onMessage(byte[] b, int offset, int length) {
        System.out.println("Received binary message of length: " + length);
    }
}

// Create a data plane interface with the callback
DataPlaneInterface dataPlane = client.getDataPlane(query, new MyCallback());
dataPlane.start();

// Wait for connection and messages will be processed by the callback
```

### MsgEventInterface

The `MsgEventInterface` is primarily used internally by the `CrescoClient` but can be accessed for lower-level messaging:

```java
// The MsgEventInterface is accessed indirectly through the CrescoClient and Messaging components
MsgEventInterface msgInterface = new MsgEventInterface(host, port, serviceKey);
msgInterface.start(5); // 5-second timeout

// Send a message
Map<String, String> messageInfo = new HashMap<>();
messageInfo.put("message_type", "global_controller_msgevent");
messageInfo.put("message_event_type", "EXEC");
messageInfo.put("is_rpc", "true");

Map<String, Object> messagePayload = new HashMap<>();
messagePayload.put("action", "listregions");

Map<String, Object> message = new HashMap<>();
message.put("message_info", messageInfo);
message.put("message_payload", messagePayload);

String jsonMessage = new Gson().toJson(message);
msgInterface.send(true, jsonMessage);

// Receive a response if is_rpc is true
String jsonResponse = msgInterface.recv();
```

## Common Use Cases

### Pipeline Management

Pipelines in Cresco define application topologies with nodes (plugins) and edges (connections).

```java
// Define a simple two-node pipeline
public String createTwoNodePipeline(String pipelineName, Map<String, String> pluginInfo) {
    // Create CADL structure
    Map<String, Object> cadl = new HashMap<>();
    cadl.put("pipeline_id", "0");
    cadl.put("pipeline_name", pipelineName);
    
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<Map<String, Object>> edges = new ArrayList<>();
    
    // First node
    Map<String, Object> params0 = new HashMap<>();
    params0.put("pluginname", pluginInfo.get("pluginname"));
    params0.put("md5", pluginInfo.get("md5"));
    params0.put("version", pluginInfo.get("version"));
    params0.put("location_region", client.api.getAPIRegionName());
    params0.put("location_agent", client.api.getAPIAgentName());
    
    Map<String, Object> node0 = new HashMap<>();
    node0.put("type", "dummy");
    node0.put("node_name", "Plugin 0");
    node0.put("node_id", 0);
    node0.put("isSource", false);
    node0.put("workloadUtil", 0);
    node0.put("params", params0);
    
    // Second node
    Map<String, Object> params1 = new HashMap<>();
    params1.put("pluginname", pluginInfo.get("pluginname"));
    params1.put("md5", pluginInfo.get("md5"));
    params1.put("version", pluginInfo.get("version"));
    params1.put("location_region", client.api.getAPIRegionName());
    params1.put("location_agent", client.api.getAPIAgentName());
    
    Map<String, Object> node1 = new HashMap<>();
    node1.put("type", "dummy");
    node1.put("node_name", "Plugin 1");
    node1.put("node_id", 1);
    node1.put("isSource", false);
    node1.put("workloadUtil", 0);
    node1.put("params", params1);
    
    // Edge connecting the nodes
    Map<String, Object> edge0 = new HashMap<>();
    edge0.put("edge_id", 0);
    edge0.put("node_from", 0);
    edge0.put("node_to", 1);
    edge0.put("params", new HashMap<>());
    
    nodes.add(node0);
    nodes.add(node1);
    edges.add(edge0);
    
    cadl.put("nodes", nodes);
    cadl.put("edges", edges);
    
    // Submit the pipeline
    Map<String, String> response = client.globalcontroller.submit_pipeline("0", cadl);
    return response.get("gpipeline_id");
}

// Find a pipeline by name
public String getPipelineIdByName(String pipelineName) {
    String pipelineId = null;
    List<Map<String, String>> pipelineList = client.globalcontroller.get_pipeline_list();
    for (Map<String, String> pipeline : pipelineList) {
        if (pipeline.get("pipeline_name").equals(pipelineName)) {
            pipelineId = pipeline.get("pipeline_id");
            break;
        }
    }
    return pipelineId;
}

// Wait for a pipeline to become active
public void waitForPipelineActive(String pipelineId) throws InterruptedException {
    int status = -1;
    while (status != 10) {
        status = client.globalcontroller.get_pipeline_status(pipelineId);
        Thread.sleep(1000);
    }
    System.out.println("Pipeline is active!");
}
```

### Plugin Management

Upload, deploy, and manage plugins in the Cresco environment:

```java
// Upload a plugin to the global repository
public Map<String, String> uploadPluginToRepo(String jarPath) {
    return client.globalcontroller.upload_plugin_global(jarPath);
}

// Pull a plugin from the repository to an agent
public Map<String, String> pullPluginToAgent(String region, String agent, String jarPath) {
    return client.agents.repo_pull_plugin_agent(region, agent, jarPath);
}

// Add a plugin to an agent with configuration
public Map<String, String> addPluginToAgent(
        String region, String agent, Map<String, String> config) {
    return client.agents.add_plugin_agent(region, agent, config, null);
}

// Remove a plugin from an agent
public Map<String, String> removePluginFromAgent(String region, String agent, String pluginId) {
    return client.agents.remove_plugin_agent(region, agent, pluginId);
}

// List plugins on an agent
public List<Map<String, String>> listAgentPlugins(String region, String agent) {
    return client.globalcontroller.get_plugin_list(region, agent);
}
```

### Log Streaming

Stream and process logs from Cresco agents:

```java
// Create a custom log handler
class LogHandler implements OnMessageCallback {
    @Override
    public void onMessage(String msg) {
        System.out.println("LOG: " + msg);
        // Process log message here
    }
    
    @Override
    public void onMessage(byte[] b, int offset, int length) {
        // Binary messages are not typically used for logs
    }
}

// Start log streaming with custom handler
public void streamLogs(String region, String agent) {
    LogStreamerInterface logStreamer = client.getLogStreamer(new LogHandler());
    logStreamer.start();
    
    // Wait for connection
    try {
        while (!logStreamer.connected()) {
            Thread.sleep(1000);
        }
        
        // Configure the log stream
        logStreamer.update_config(region, agent);
        
        // Keep streaming until manually stopped
        System.out.println("Streaming logs from " + region + "/" + agent);
    } catch (InterruptedException e) {
        e.printStackTrace();
        logStreamer.close();
    }
}
```

### Data Plane Operations

Send and receive data through the Cresco data plane:

```java
// Set up a data plane for communication with identity parameters
public DataPlaneInterface setupDataPlane(String identKey, String identId) {
    // Create configuration map
    Map<String, String> config = new HashMap<>();
    config.put("ident_key", identKey);
    config.put("ident_id", identId);
    config.put("io_type_key", "type");
    config.put("output_id", "output");
    config.put("input_id", "output");
    
    // Convert to JSON query
    String query = new Gson().toJson(config);
    
    // Create data handler
    class DataHandler implements OnMessageCallback {
        @Override
        public void onMessage(String msg) {
            System.out.println("Received message: " + msg);
        }
        
        @Override
        public void onMessage(byte[] b, int offset, int length) {
            System.out.println("Received binary data of length: " + length);
        }
    }
    
    // Create and start data plane
    DataPlaneInterface dataPlane = client.getDataPlane(query, new DataHandler());
    dataPlane.start();
    
    try {
        while (!dataPlane.connected()) {
            Thread.sleep(1000);
        }
        System.out.println("Data plane connected");
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
    
    return dataPlane;
}

// Send messages through the data plane
public void sendMessages(DataPlaneInterface dataPlane, int count) {
    for (int i = 0; i < count; i++) {
        try {
            dataPlane.send("Message " + i);
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```

### Tunneling

Create and manage tunnels between Cresco agents:

```java
// Deploy a tunnel pipeline between two agents
public String deployTunnel(String pipelineName, String clientRegion, String clientAgent) 
        throws InterruptedException {
    
    // Upload the stunnel plugin
    String jarPath = "/path/to/stunnel.jar";
    Map<String, String> pluginMap = client.globalcontroller.upload_plugin_global(jarPath);
    
    // Get plugin details
    String configParamsString = client.messaging.getCompressedParam(pluginMap.get("configparams"));
    Map<String, String> pluginConfig = client.messaging.getMapFromString(configParamsString);
    
    // Create CADL for tunnel
    Map<String, Object> cadl = new HashMap<>();
    cadl.put("pipeline_id", "0");
    cadl.put("pipeline_name", pipelineName);
    
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<Map<String, Object>> edges = new ArrayList<>();
    
    // Source node
    Map<String, Object> params0 = new HashMap<>();
    params0.put("pluginname", pluginConfig.get("pluginname"));
    params0.put("md5", pluginConfig.get("md5"));
    params0.put("version", pluginConfig.get("version"));
    params0.put("location_region", client.api.getAPIRegionName());
    params0.put("location_agent", client.api.getAPIAgentName());
    
    Map<String, Object> node0 = new HashMap<>();
    node0.put("type", "dummy");
    node0.put("node_name", "Tunnel Source");
    node0.put("node_id", 0);
    node0.put("isSource", false);
    node0.put("workloadUtil", 0);
    node0.put("params", params0);
    
    // Destination node
    Map<String, Object> params1 = new HashMap<>();
    params1.put("pluginname", pluginConfig.get("pluginname"));
    params1.put("md5", pluginConfig.get("md5"));
    params1.put("version", pluginConfig.get("version"));
    params1.put("location_region", clientRegion);
    params1.put("location_agent", clientAgent);
    
    Map<String, Object> node1 = new HashMap<>();
    node1.put("type", "dummy");
    node1.put("node_name", "Tunnel Destination");
    node1.put("node_id", 1);
    node1.put("isSource", false);
    node1.put("workloadUtil", 0);
    node1.put("params", params1);
    
    // Edge connecting the nodes
    Map<String, Object> edge0 = new HashMap<>();
    edge0.put("edge_id", 0);
    edge0.put("node_from", 0);
    edge0.put("node_to", 1);
    edge0.put("params", new HashMap<>());
    
    nodes.add(node0);
    nodes.add(node1);
    edges.add(edge0);
    
    cadl.put("nodes", nodes);
    cadl.put("edges", edges);
    
    // Submit the pipeline
    Map<String, String> response = client.globalcontroller.submit_pipeline("0", cadl);
    String pipelineId = response.get("gpipeline_id");
    
    // Wait for pipeline to be active
    int status = -1;
    while (status != 10) {
        gPayload pipelineStatus = client.globalcontroller.get_pipeline_info(pipelineId);
        status = Integer.parseInt(pipelineStatus.status_code);
        Thread.sleep(1000);
    }
    
    return pipelineId;
}

// Configure a tunnel between two endpoints
public void configureTunnel(String pipelineId, String srcPort, String dstHost, String dstPort) {
    try {
        // Get pipeline info to extract node details
        gPayload pipelineInfo = client.globalcontroller.get_pipeline_info(pipelineId);
        
        String srcRegionId = pipelineInfo.nodes.get(0).params.get("region_id");
        String srcAgentId = pipelineInfo.nodes.get(0).params.get("agent_id");
        String srcPluginId = pipelineInfo.nodes.get(0).params.get("plugin_id");
        
        String dstRegionId = pipelineInfo.nodes.get(1).params.get("region_id");
        String dstAgentId = pipelineInfo.nodes.get(1).params.get("agent_id");
        String dstPluginId = pipelineInfo.nodes.get(1).params.get("plugin_id");
        
        // Configure the tunnel
        String messageType = "CONFIG";
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", "configsrctunnel");
        payload.put("action_src_port", srcPort);
        payload.put("action_dst_host", dstHost);
        payload.put("action_dst_port", dstPort);
        payload.put("action_dst_region", dstRegionId);
        payload.put("action_dst_agent", dstAgentId);
        payload.put("action_dst_plugin", dstPluginId);
        payload.put("action_buffer_size", "256000");
        
        Map<String, String> response = client.messaging.global_plugin_msgevent(
            true, messageType, payload, srcRegionId, srcAgentId, srcPluginId
        );
        
        System.out.println("Tunnel configuration response: " + response);
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

## Reference

For complete method-level documentation, refer to the JavaDoc documentation for each class. The key classes are:

- `CrescoClient`: Main client class for connecting to Cresco
- Core components:
  - `API`: API information and global controller discovery
  - `Admin`: Administrative functions
  - `Agents`: Agent and plugin management
  - `GlobalController`: Pipeline and resource management
  - `Messaging`: Communication framework
- Interfaces:
  - `LogStreamerInterface`: Log streaming
  - `DataPlaneInterface`: Data plane operations
  - `MsgEventInterface`: Low-level message event handling

For examples and additional use cases, refer to the example packages included with the library.


#### Agent Tunnel Test

This example code will deploy two stunnel plugins, a source that will listen on port 5201 and a destination, which will connect to an existing socket.  One plugin will connect to the existing IPerf3 listening port of 5201.  The other plugin will open a socket on port 5202.  You will launch your IPerf3 client to the stunnel listening port, which will forward the traffic to the other plugin.


The plugins will be deployed differently based on the agent topology:
* Single agent: The two plugins will be deployed on the same agent
* Two agents: Global controller with a second agent: The listening (source) plugin will be deployed on the global controller, and the sending plugin will be deployed on the agent.
* Three agents: Global controller, with a connected regional controller, with an agent connected to the regional controller.  The listening (source) plugin will be deployed on the global controller, and the sending plugin will be deployed on the agent, which is connected to the regional controller. 


1. Launch the [iPerf3](https://iperf.fr/iperf-download.php) server on the same node as your agent.  Note the default listening port of 5201, which corresponds to the configuration "action_src_port": "5201"

```bash
./iperf3 -s
-----------------------------------------------------------
Server listening on 5201
-----------------------------------------------------------
```
2. Launch a Cresco agent configured as a global controller with a WSAPI configured

3. Uncomment the following lines in example.Launcher
```java
TunnelTesting tunnelTesting = new TunnelTesting(client);
tunnelTesting.tunnelTest();
```
4. Launch the iPerf client on the same node as your agent, setting the destination port to 5202. Note the destination port of 5202 corresponding to the configuration "action_dst_port": "5202".
```bash
./iperf3 -c localhost -p 5202
Connecting to host localhost, port 5202
[  6] local 127.0.0.1 port 55131 connected to 127.0.0.1 port 5202
[ ID] Interval           Transfer     Bandwidth
[  6]   0.00-1.00   sec  1.09 GBytes  9.32 Gbits/sec                  
[  6]   1.00-2.00   sec  1.09 GBytes  9.37 Gbits/sec                  
[  6]   2.00-3.00   sec   654 MBytes  5.49 Gbits/sec                  
[  6]   3.00-4.00   sec   537 MBytes  4.50 Gbits/sec                  
[  6]   4.00-5.00   sec   408 MBytes  3.43 Gbits/sec                  
[  6]   5.00-6.00   sec   555 MBytes  4.65 Gbits/sec                  
[  6]   6.00-7.00   sec   532 MBytes  4.47 Gbits/sec                  
[  6]   7.00-8.00   sec   538 MBytes  4.51 Gbits/sec                  
[  6]   8.00-9.00   sec   630 MBytes  5.29 Gbits/sec                  
[  6]   9.00-10.00  sec   563 MBytes  4.72 Gbits/sec                  
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth
[  6]   0.00-10.00  sec  6.49 GBytes  5.58 Gbits/sec                  sender
[  6]   0.00-10.00  sec  5.93 GBytes  5.09 Gbits/sec                  receiver
```
