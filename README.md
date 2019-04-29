# What is Ripcom?
 - Ripcom is an implementation of the Routing Information Protocol ([RIP v2](https://tools.ietf.org/html/rfc2453 "Click to see the RFC")), written from scratch in Java. 
 - `Rover.java` represents a Rover on Mars. This Rover:
    1. Has a built in router
    2. Has a private IP 
    3. Uses RIP to find the shortest path between itself and other Rovers. 
 - Ripcom is also a [protocol](https://github.com/a3y3/Ripcom/blob/master/RipcomProtocol.md) that can reliably transfer a file through a lossy network.

### Demonstration 
<img src="images/Rover Topology.png">

^ For this Topology (created using Docker containers and iptables), Ripcom produces the following output:

<img src="images/Ripcom Operation.png">

If some Rovers go out of range (simulated by shutting down that particular Rover process), the other Rovers will automatically adjust the shortest path using RIP. 

### How to build:
- Compile Rover and dependencies using `javac *.java`

- Run Rover using `java Rover -r <ID>`. `ID` should be an 8 bit number (can't exceed 127).

- There are several other optional flags you can use to fine tune the simulation. See the full list using `java Rover --help` or `java Rover -h`.

### Example:

`java Rover -r 2 -i 233.31.31.31 -d 10.0.3.0 -f bible -v 1` will start a new `Rover` with:


| Field             | Value           
| -------------     |:-------------:|
| roverID           | 2             |
| Multicast IP      | 233.31.31.31  |
| Destination IP    | 10.0.3.0      |
| File name to send | bible         |
| Verbose Level     | 1             |

Note that the only field that is not optional is `-r`.

Now that a Rover is running, you can run other Rovers on other computers (or VMs, or Docker containers!) and see the Rovers calculate the shortest path to each other.

##### Author
Soham Dongargaonkar
