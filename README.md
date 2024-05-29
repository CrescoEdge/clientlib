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

#### Single Node Tunnel Test

1. Launch the [iPerf3](https://iperf.fr/iperf-download.php) server on the same node as your agent.

```bash
./iperf3 -s
-----------------------------------------------------------
Server listening on 5201
-----------------------------------------------------------
```
2. Uncomment the following lines in example.Launcher
```java
SingleNodeTunnelTest singleNodeTunnelTest = new SingleNodeTunnelTest(client);
singleNodeTunnelTest.deploySingleNodeSTunnel("iperf_tunnel");
```
3. Launch the iPerf client on the same node as your agent
```bash
./iperf3 -c localhost 5202
Connecting to host localhost, port 5201
[  6] local 127.0.0.1 port 54749 connected to 127.0.0.1 port 5201
[ ID] Interval           Transfer     Bandwidth
[  6]   0.00-1.00   sec  6.94 GBytes  59.6 Gbits/sec                  
[  6]   1.00-2.00   sec  7.14 GBytes  61.3 Gbits/sec                  
[  6]   2.00-3.00   sec  7.25 GBytes  62.3 Gbits/sec                  
[  6]   3.00-4.00   sec  7.32 GBytes  62.9 Gbits/sec                  
[  6]   4.00-5.00   sec  7.09 GBytes  60.9 Gbits/sec                  
[  6]   5.00-6.00   sec  7.37 GBytes  63.3 Gbits/sec                  
[  6]   6.00-7.00   sec  7.36 GBytes  63.2 Gbits/sec                  
[  6]   7.00-8.00   sec  7.38 GBytes  63.4 Gbits/sec                  
[  6]   8.00-9.00   sec  7.32 GBytes  62.9 Gbits/sec                  
[  6]   9.00-10.00  sec  7.38 GBytes  63.4 Gbits/sec                  
- - - - - - - - - - - - - - - - - - - - - - - - -
[ ID] Interval           Transfer     Bandwidth
[  6]   0.00-10.00  sec  72.6 GBytes  62.3 Gbits/sec                  sender
[  6]   0.00-10.00  sec  72.6 GBytes  62.3 Gbits/sec                  receiver
```



