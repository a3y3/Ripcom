import java.net.InetAddress;
import java.net.InterfaceAddress;

class RoutingTableEntry {
    InterfaceAddress address;
    InetAddress nextHop;
    int cost;
}
