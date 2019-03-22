# What is Ripcom?
Ripcom is an implementation of the Routing Information Protocol (RIP v2), written from scratch in Java. `Rover.java` represents a Rover on Mars. Several of these Rovers communicate with each other and find the shortest path between them using RIP. 

### How to build:
Compile Rover and dependencies using `javac *.java`

Run Rover using `java Rover -r ROUTER_ID`. `ROVER_ID` should be an 8 bit number.

There are several other optional flags you can use to fine tune it. See the full list using `java Rover --help` or `java Rover -h`.

Now that a Rover is running, you can run other Rovers on other computers (or VMs, or Docker containers!) and see the Rover calculate the shortest path.

##### Author
Soham Dongargaonkar , RIT ID: sd4324
