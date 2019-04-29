# Ripcom Protocol

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

#### Source IP
Each Rover MUST send its own IP address in the Source IP header. This lets the 
destination Rover know whom to send ACKS to.

#### Packet Type
This field is of size 1 byte. It can be either of the following types:
* ACK. Denotes what packet number the Rover is expecting to RECEIVE next.
* SEQ. Denotes what packet the sender is sending.
* FIN. If this is the last packet the sender is sending. Upon receiving this packet 
type, the receiver Rover declares the transfer to have finished.
* FIN_ACK. Used by the receiver Rover to tell the sender that the final packet has been
 received, and it may close the connection.
 
 Due to the use of FIN and FIN_ACKs, the Ripcom Protocol also serves as a streaming 
 protocol, as the length of the file is not necessary to be known beforehand.
 
#### Number
The packet number. On receiving ACK for packet X, a Rover MUST send packet X+1, and on 
receiving a SEQ for packet X-1, a Rover MUST send an ACK for packet X.

#### Length
The length of the content being transferred. This is necessary because Java's UDP 
accepts fixed sizes buffers and this offers an easy way for Rovers to read data.

#### Contents
Contents may be variable length depending upon the length. Since Ripcom uses bytes for 
parsing, any format may be used for sending data, not just text.


### What now