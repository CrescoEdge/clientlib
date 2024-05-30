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
