class RoutingTableEntry {
    String IPAddress;
    byte mask;
    String nextHop;
    byte cost;

    RoutingTableEntry() {

    }

    RoutingTableEntry(String IPAddress, byte mask, String nextHop, byte cost) {
        this.IPAddress = IPAddress;
        this.mask = mask;
        this.nextHop = nextHop;
        this.cost = cost;
    }
}
