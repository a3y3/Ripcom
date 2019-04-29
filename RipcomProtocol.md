#Ripcom Protocol

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
 

    0             1                    2                       3                       4
    0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31
    +--------------------------------------+---------------------------------------------+
    |                                                                                    |
    +-------------                  Destination IP                          -------------+
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
    +-------------                 Contents [Variable Length]               -------------+
    |                                                                                    |
    +--------------------------------------+---------------------------------------------+