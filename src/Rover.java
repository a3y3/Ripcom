import java.io.IOException;
import java.net.*;
import java.util.*;

public class Rover extends Thread {
    private ArrayList<RoutingTableEntry> routingTable;
    private HashMap<String, Timer> timers = new HashMap<>();

    private final static int MULTICAST_PORT = 20001;
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds
    private final static byte DEFAULT_MASK = 32;
    private final static int INFINITY = 16;
    private final static int TIMEOUT = 10000;   // unreachable at 10 secs

    //flags
    private boolean verboseOutputs;

    private Rover() {
        routingTable = new ArrayList<>();

        Thread listenerThread =
                new Thread(this::startListening); //starts the listener thread
        listenerThread.start();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    sendRIPMessage();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }, 0, UPDATE_FREQUENCY);
    }

    public static void main(String[] args) {
//        String usage = "Usage: java Rover <rover_id>";
//        int roverID;

//        try {
//            roverID = Integer.parseInt(args[0]);
//        } catch (Exception n) {
//            throw new ArgumentException(usage); //Prevents further execution
//        }

        Rover rover = new Rover();
        rover.parseArguments(args);
    }

    private void parseArguments(String[] args){
        for (String argument: args){
            if (argument.equals("-v")) verboseOutputs = true;
        }
    }
    private void startListening() {
        try {
            MulticastSocket socket = new MulticastSocket(MULTICAST_PORT);
            byte[] buffer = new byte[256];
            InetAddress iGroup = InetAddress.getByName("233.33.33.33");
            socket.joinGroup(iGroup);

            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(buffer,
                        buffer.length);
                socket.receive(datagramPacket);
                ArrayList<RoutingTableEntry> receivedEntries =
                        unpackRIPEntries(datagramPacket);
                addSingleRoutingEntry(datagramPacket.getAddress());
                startTimerFor(datagramPacket.getAddress().getHostAddress());
                updateRoutingTable(receivedEntries, datagramPacket.getAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendRIPMessage() throws UnknownHostException {
        byte[] buffer = getRIPPacket(true);
        InetAddress iGroup = null;

        try {
            iGroup = InetAddress.getByName("233.33.33.33");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length,
                iGroup, MULTICAST_PORT);

        DatagramSocket datagramSocket;
        try {
            datagramSocket = new DatagramSocket();
            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void startTimerFor(String ipAddress) {
        Timer timer;
        if (timers.containsKey(ipAddress)) {
            timer = timers.get(ipAddress);
            timer.cancel();
        }
        timer = new Timer();
        timers.put(ipAddress, timer);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(ipAddress + " timed out!");
                RoutingTableEntry r = findRoutingTableEntryForIp(ipAddress);
                r.cost = INFINITY;
                ArrayList<RoutingTableEntry> arrayList =
                        getEntriesUsingIp(ipAddress);
                for (RoutingTableEntry routingTableEntry: arrayList){
                    routingTableEntry.cost = INFINITY;
                }
                displayRoutingTable();
            }
        }, TIMEOUT);

    }

    /**
     * Constructs a RIP packet and returns the byte array formed.
     *
     * @return the RIP packet in a byte array
     */
    @SuppressWarnings("SameParameterValue")
    private byte[] getRIPPacket(boolean isRequest) throws UnknownHostException {
        ArrayList<Byte> arrayList = new ArrayList<>();

        byte command = (byte) (isRequest ? 1 : 2);
        byte zero = 0;

        arrayList.add(command);     // Command

        byte version = 2;
        arrayList.add(version);     // Version

        arrayList.add(zero);
        arrayList.add(zero);     // Unused zeros

        for (RoutingTableEntry r : routingTable) {
            arrayList.add(zero);
            arrayList.add((byte) 2);     // Address Family Identifier

            byte routeTag = 1;
            arrayList.add(routeTag);
            arrayList.add(routeTag);    // Route Tag (placeholder 1 for now)

            String ip = r.IPAddress;
            byte[] ipBytes = InetAddress.getByName(ip).getAddress();

            for (byte b : ipBytes) {
                arrayList.add(b);       //IP Address
            }

            byte subnetMask = r.mask;
            arrayList.add(zero);
            arrayList.add(zero);
            arrayList.add(zero);
            arrayList.add(subnetMask);  //Subnet Mask

            String nextHop = r.nextHop;
            byte[] nextHopBytes = InetAddress.getByName(nextHop).getAddress();

            for (byte b : nextHopBytes) {
                arrayList.add(b);       //Next Hop
            }

            byte cost = r.cost;
            arrayList.add(zero);
            arrayList.add(zero);
            arrayList.add(zero);
            arrayList.add(cost);        // Metric
        }

        int size = arrayList.size();
        Byte[] bytes = arrayList.toArray(new Byte[size]);
        byte[] ripPacket = new byte[size];
        int i = 0;
        for (byte b : bytes) {
            ripPacket[i++] = b;
        }
        return ripPacket;
    }

    /**
     * Decodes a RIP Packet and makes RoutingTableEntries out of it.
     *
     * @param ripPacket The received RIP packet
     * @return An ArrayList of all possible RoutingTableEntries that can be
     * constructed out of this packet.
     */
    @SuppressWarnings("MismatchedReadAndWriteOfArray")
    private ArrayList<RoutingTableEntry> decodeRIPPacket(byte[] ripPacket) {
        ArrayList<RoutingTableEntry> arrayList = new ArrayList<>();
        int i = 0;
        i += 2; //Ignore command and version
//        byte command = ripPacket[i++];
//
//        byte version = ripPacket[i++];

        i += 2;
        while (i < ripPacket.length) {
            byte[] AFI = new byte[2];
            AFI[0] = ripPacket[i++];
            AFI[1] = ripPacket[i++];

            byte[] routeTag = new byte[2];
            routeTag[0] = ripPacket[i++];
            routeTag[1] = ripPacket[i++];

            int[] ipAddress = new int[4];
            fillIPAddress(ipAddress, ripPacket, i);
            i += 4;
            String ipAddressStringForm = getIPAddressInStringForm(ipAddress);

            i += 3;
            byte subnetMask = ripPacket[i++];

            int[] nextHop = new int[4];
            fillIPAddress(nextHop, ripPacket, i);
            i += 4;

            i += 3;
            String nextHopInStringForm = getIPAddressInStringForm(nextHop);


            byte cost = ripPacket[i];

            RoutingTableEntry r = new RoutingTableEntry(ipAddressStringForm,
                    subnetMask, nextHopInStringForm, cost);
            arrayList.add(r);
            i++;
        }
        return arrayList;
    }

    /**
     * Fills ipAddressp[] with the unsigned representation of the bytes from
     * the RIP Packet.
     *
     * @param ipAddress the array that needs to be filled
     * @param ripPacket the packet that contains the IP
     * @param i         denotes what value to start from
     */
    private void fillIPAddress(int[] ipAddress, byte[] ripPacket, int i) {
        for (int j = 0; j < 4; j++) {
            ipAddress[j] = Byte.toUnsignedInt(ripPacket[i++]);
        }
    }

    /**
     * Constructs a String representation of the ipAddress
     *
     * @param ipAddress ipv4 address (int[] of length 4)
     * @return the String representation of the address
     */
    private String getIPAddressInStringForm(int[] ipAddress) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ipAddress.length; i++) {
            sb.append(ipAddress[i]);
            if (i < ipAddress.length - 1) sb.append(".");
        }

        return sb.toString();
    }

    /**
     * Gets the RIP contents and retrieves an ArrayList of RoutingTableEntries
     * from decodeRIPPacket().
     *
     * @param datagramPacket the received RIP packet from another Rover.
     */
    private ArrayList<RoutingTableEntry> unpackRIPEntries(DatagramPacket datagramPacket) {
        byte[] receivedRipPacket = new byte[datagramPacket.getLength()];
        System.arraycopy(datagramPacket.getData(), 0, receivedRipPacket, 0,
                receivedRipPacket.length);
        ArrayList<RoutingTableEntry> entries = decodeRIPPacket(receivedRipPacket);

        if (verboseOutputs) {
            System.out.println("Received the following Table Entries from " + datagramPacket.getAddress().getHostAddress());
            System.out.println("Address\t\tNextHop\t\tCost");
            for (RoutingTableEntry r : entries) {
                System.out.println(r.IPAddress + "\t" + r.nextHop + "\t" + r.cost);
            }
        }

        return entries;
    }

    /**
     * Accepts an IP Address and adds it to the table with a hop count of 1
     * (or updates an existing IP with mask to a hop count of 1) since this
     * IP address is directly reachable.
     *
     * @param inetAddress represents an IP address that responded to a RIP
     *                    request. Since it responded, the hop count is 1,
     *                    and the next hop is itself.
     */
    private void addSingleRoutingEntry(InetAddress inetAddress) throws UnknownHostException {
        if (inetAddress.getHostAddress().equals(InetAddress.getLocalHost().getHostAddress())) {
            return;
        }
        String ipToAdd = inetAddress.getHostAddress();
        boolean presentInTable = false;
        boolean changed = false;
        for (RoutingTableEntry routingTableEntry : routingTable) {
            if (routingTableEntry.IPAddress.equals(ipToAdd)) {       //TODO No. Must support mask
                presentInTable = true;
                if (routingTableEntry.cost != 1) {
                    routingTableEntry.nextHop = routingTableEntry.IPAddress;
                    routingTableEntry.cost = 1;
                    changed = true;
                    break;
                }
            }
        }

        if (!presentInTable) {
            RoutingTableEntry r = new RoutingTableEntry(ipToAdd, DEFAULT_MASK
                    , ipToAdd, (byte) 1);
            routingTable.add(r);
            changed = true;
        }

        if (changed) {
            displayRoutingTable();
        }
    }

    /**
     * Displays the current state of the Routing Table.
     */
    private void displayRoutingTable() {
        System.out.println();
        System.out.println("============================");
        System.out.println("Updated Routing Table Entries");
        System.out.println("Address\t\tNextHop\t\tCost");
        for (RoutingTableEntry r : routingTable) {
            System.out.println(r.IPAddress + "\t" + r.nextHop + "\t" + r.cost);
        }
        System.out.println();
    }


    /**
     * This is where the actual RIP shortest distance calculation actually
     * happens. This method updates the Rover's routingTable with the entries
     * from receivedTable.
     *
     * @param receivedTable A RIP table that was received by this Rover.
     */
    private void updateRoutingTable(ArrayList<RoutingTableEntry> receivedTable, InetAddress inetAddress) throws UnknownHostException {
        String senderIp = inetAddress.getHostAddress();
        String selfIp = InetAddress.getLocalHost().getHostAddress();
        boolean updated = false;

        for (RoutingTableEntry r : receivedTable) {
            String ipAddress = r.IPAddress;
            RoutingTableEntry routingTableEntry =
                    findRoutingTableEntryForIp(ipAddress);
            if (!ipAddress.equals(selfIp)) {
                byte cost = (byte) (r.cost + 1);

                if (routingTableEntry == null) {
                    routingTableEntry = new RoutingTableEntry(ipAddress,
                            DEFAULT_MASK, senderIp, cost);
                    routingTable.add(routingTableEntry);
                    updated = true;
                    continue;
                }
                if (r.nextHop.equals(selfIp)) {
                    /*
                        Split Horizon with Poisoned Reverse. Basically, if
                        this Rover gets a packet that uses it as the next
                        hop, treat it as infinity.
                     */
                    continue;
                }
                if (cost < getCost(routingTableEntry)) {
                    routingTableEntry.nextHop = senderIp;
                    routingTableEntry.cost = cost;
                    updated = true;
                } else {
                    /*
                        Metric is higher than current. However, it must be
                        updated if the metric came from the router that we are
                        using as next hop.
                        The inner if condition is simply there for the
                        updated variable, which is set if the earlier cost
                        was different from the newer cost.
                    */
                    if (senderIp.equals(routingTableEntry.nextHop)) {
                        if (routingTableEntry.cost != cost) {
                            routingTableEntry.cost = cost;
                            updated = true;
                        }
                    }
                }
            }
        }

        if (verboseOutputs || updated) displayRoutingTable();
    }

    /**
     * Scans the current table and returns an entry for an IP address.
     *
     * @param ip an IP Address in consideration by updateRoutingTable().
     * @return if found: the matching RoutingTableEntry; else: null.
     */
    private RoutingTableEntry findRoutingTableEntryForIp(String ip) {
        for (RoutingTableEntry r : routingTable) {
            if (r.IPAddress.equals(ip)) {
                return r;
            }
        }
        return null;
    }

    /**
     * Finds a list of IPs that use this IP for their next hop.
     *
     * @param ip An IP address that needs to be searched for in nextHop
     * @return the list of IPs that use @param ip for next hop.
     */
    private ArrayList<RoutingTableEntry> getEntriesUsingIp(String ip) {
        ArrayList<RoutingTableEntry> arrayList = new ArrayList<>();
        for (RoutingTableEntry routingTableEntry : routingTable) {
            if (routingTableEntry.nextHop.equals(ip)) {
                arrayList.add(routingTableEntry);
            }
        }
        return arrayList;
    }

    /**
     * Accepts a RoutingTableEntry and returns the corresponding cost if it
     * is not null. Note that the routingTableEntry is an entry that has been
     * found by findRoutingTableEntryForIp(); it represents an entry that
     * corresponds to the ipAddress that is currently being considered by
     * updateRoutingTable().
     *
     * @param routingTableEntry an entry found for an ipAddress (by
     *                          findRoutingTableEntryForIp()
     * @return the matching cost if not null, otherwise infinity.
     */
    private int getCost(RoutingTableEntry routingTableEntry) {
        return routingTableEntry != null ? routingTableEntry.cost : INFINITY;
    }
}