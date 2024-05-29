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
client.admin

### Agents
client.agents

### API
client.api 

### DataPlane
client.getDataPlane

### GlobalController
client.globalcontroller

### LogStreamer
client.getLogStreamer()

### Messaging
client.messaging


## Example Use Cases

### Binary File Repo Testing

### Binary Performance Testing

### File Repo Performance Testing

### Text Performance Testing

### Transfer Stream

### Tunnel Testing



