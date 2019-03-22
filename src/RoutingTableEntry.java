/**
* Holds an entry in the Routing Table of each Rover.
*
* @author Soham Dongargaonkar
*/
class RoutingTableEntry {
    String IPAddress;
    byte mask;
    String nextHop;
    byte cost;

    RoutingTableEntry(String IPAddress, byte mask, String nextHop, byte cost) {
        this.IPAddress = IPAddress;
        this.mask = mask;
        this.nextHop = nextHop;
        this.cost = cost;
    }
}
