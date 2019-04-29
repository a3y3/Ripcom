# What is Ripcom?
Ripcom is an implementation of the Routing Information Protocol ([RIP v2](https://tools
.ietf.org/html/rfc2453 "Click to see the RFC")), written from scratch in Java. `Rover
.java` represents a Rover on Mars. Several of these Rovers communicate with each other
 and find the shortest path between them using RIP. 

Using this shortest path, these Rovers can transmit a large file reliably through the 
Ripcom protocol. See RipomProtocol.md for more details.

### How to build:
Compile Rover and dependencies using `javac *.java`

Run Rover using `java Rover -r ROUTER_ID`. `ROVER_ID` should be an 8 bit number.

There are several other optional flags you can use to fine tune it. See the full list 
using `java Rover --help` or `java Rover -h`.

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

Now that a Rover is running, you can run other Rovers on other computers (or VMs, or 
Docker containers!) and see the Rover calculate the shortest path.

##### Author
Soham Dongargaonkar
