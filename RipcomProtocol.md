# Ripcom Protocol

Note: This document is best viewed on a mark down viewer, such as Github.

### Abstract

NASA has sent Rovers on Mars. They move through the rough terrain on Mars and 
continuously send data to one another. Needless to say, the medium is noisy.

The Ripcom Protocol is designed the provide a reliable data transfer mechanism for 
these Rovers that results in lesser number of packets and avoids the overhead TCP has.

### Introduction

Rovers on Mars do not need TCP to communicate since they know the type of data being  
transferred and the fact that no one else can communicate with them (Mars is lonely!) 

Hence, instead of using TCP, the Ripcom Protocol is a better alternative. It avoids the
 hassles of setting up connections, overhead of using congestion control mechanisms, 
 and results in lesser packets than TCP.
 
### Packet Format
 
 A Ripcom Packet has the following structure -

    0             1                    2                       3                       4
    0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                   Destination IP                         -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                      Source IP                           -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-- Packet Type--- (1 byte only)                                                   --+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                       Number                             -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                       Length                             -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                Contents [Variable Length]                -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+
    
An explanation of these terms is given below.

##### Destination IP
Since a Rover's private IP address is of the form 10.0.{ID}.0, the private IP address 
must be transferred in each Ripcom Packet. Upon receiving a Ripcom Packet, each Rover 
MUST check if it's IP address matches the destination address. If the address does not 
match, the Rover MUST NOT open the packet and must forward it immediately according to 
it's routing tables. 

##### Source IP
Each Rover MUST send its own IP address in the Source IP header. This lets the 
destination Rover know whom to send ACKS to.

##### Packet Type
This field is of size 1 byte. It can be either of the following types:
* ACK. Denotes what packet number the Rover is expecting to RECEIVE next.
* SEQ. Denotes what packet the sender is sending.
* FIN. If this is the last packet the sender is sending. Upon receiving this packet 
type, the receiver Rover declares the transfer to have finished.
* FIN_ACK. Used by the receiver Rover to tell the sender that the final packet has been
 received, and it may close the connection.
 
 Due to the use of FIN and FIN_ACKs, the Ripcom Protocol also serves as a streaming 
 protocol, as the length of the file is not necessary to be known beforehand.
 
##### Number
The packet number. On receiving ACK for packet X, a Rover MUST send packet X+1, and on 
receiving a SEQ for packet X-1, a Rover MUST send an ACK for packet X.

##### Length
The length of the content being transferred. This is necessary because Java's UDP 
accepts fixed sizes buffers and this offers an easy way for Rovers to read data.

##### Contents
Contents may be variable length depending upon the length field. Since Ripcom uses bytes 
for parsing, any format may be used for sending data, not just text.


### Operation
#### Transfer
When a Rover is started with a destination address and a file name, it adds packets to 
its window depending upon the `WINDOW_SIZE` (1 for this simulation). It initiates the 
transfer by sending this first packet and waits for an ACK.

On receiving this packet, the receiver checks if the packet was meant for it. If it was
not (this is decided by looking at the destination IP), the Rover must forward this 
packet to the next hop in it's RoutingTable.
 
If the receiver finds that the packet was meant for it, it checks for the SEQ number 
inside the packet. If the number matches what packet it was expecting (`ackNumber`), it
increments `ackNumber` and sends back an ACK Ripcom packet. It also appends to the 
output file the message that was received.

#### Timers
Each Ripcom Packet has a timer attached to it. That is, each Ripcom Packet that is of 
type SEQ or FIN. An important realization during the development was that ACKs do not 
need timers, since ACKS that are lost will be treated as SEQ packets not reaching, and 
the sender will simply send the packet again. If the receiver receives a duplicate SEQ 
(or it receives a packet that it was not expecting) it sends back an ACK again for the 
number it is expecting to receive.

Once a sender's timer for a SEQ or FIN packet expires, it re-sends the packet again 
with a new timer.


### Packet Integrity
Ripcom Protocol does not need to bother with integrity of the message since UDP handles
checksum by default.

### Testing

For testing, a 5 MB bible was transferred from Rover #2 to Rover #6, in a network of 5 
Rovers with a diamond shape topology. The network had a packet loss of 10 %. This was 
set was IP tables using the command `iptables -A INPUT -m statistic --mode random 
--probability 0.1 -p udp --destination-port 6767 -i eth0 -j DROP`
The Rover was started with the command `java Rover -r 2 -d 10.0.6.0 -f bible`.
 The results are documented below:
 
 | Test              |  Time
 | -------------     |:-------------:  |
 | #1                | 1 minute 32 sec |
 | #2                | 1 minute 37 sec |
 | #3                | 1 minute 25 sec |
 | #4                | 1 minute 30 sec |
 | #2                | 1 minute 27 sec |

In a network with no packet loss, the protocol transfers the file almost instantaneously. 

### Future Work
Ripcom has several ways to improve upon. For instance, instead of sending one packet at
a time and waiting for ACKs, it could use selective repeats. This could considerably 
improve performance. Also, instead of relying on UDP for error detection, the protocol 
could use some form of correction, since the type of data each Rover will be sending 
should be known. However, such a mechanism may impair performance.

##### Author
Soham Dongargaonkar