import java.io.IOException;
import java.net.*;
import java.util.*;

/**
 * Represents a single Rover. To run this, start a new Rover with
 * {@code java Rover -r <ID>}. See {@code java Rover --help} for more options.
 * <p>
 * A Rover has a roverID associated with it, which is used to build the
 * Rover's local IP address. This IP will be of the form "10.0.<roverID>.0/24".
 * <p>
 * As these Rovers move around (this can be simulated using firewalls), they
 * will calculate the shortest distance between them using RIP v2. Each time
 * the distance changes, the routing table is displayed.
 *
 * @author Soham Dongargaonkar
 */
public class Rover extends Thread {
    private ArrayList<RoutingTableEntry> routingTable;
    private HashMap<String, Timer> timers = new HashMap<>();

    //final variables
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds
    private final static byte DEFAULT_MASK = 24;
    private final static int INFINITY = 16;     //Max hop count in RIP is 15
    private final static int TIMEOUT = 10000;   // unreachable at 10 secs
    private final static int UDP_SEND_MAX_RETRIES = 10;
    private final String selfIP;

    //flags and args
    boolean verboseOutputs;
    int multicastPort = 0;
    int ripPort = 0;
    String multicastIp;
    int roverID;
    String destinationIP;
    int udpPort;
    String fileName;


    /**
     * This constructor retrieves the IP address of this Rover using a socket
     * that connects to 8.8.8.8 (Rovers can't connect to Google on Mars, but
     * this is a simulation after all). Hence, make sure you have an internet
     * connection up and running before using this.
     *
     * @throws SocketException      see {@code getSelfIp()}
     * @throws UnknownHostException if internet connection fails, see {@code
     *                              getSelfIp()}
     */
    private Rover() throws SocketException, UnknownHostException {
        routingTable = new ArrayList<>();
        selfIP = getSelfIP();
    }

    /**
     * Connects to Google and returns the System's IP address
     *
     * @return System IP Address
     * @throws SocketException      if datagramSocket failed to initialize
     * @throws UnknownHostException if Google was not found or the computer
     *                              was not connected to the internet.
     */
    private String getSelfIP() throws SocketException, UnknownHostException {
        DatagramSocket datagramSocket = new DatagramSocket();
        datagramSocket.connect(InetAddress.getByName("8.8.8.8"), 25252);
        return datagramSocket.getLocalAddress().getHostAddress();
    }


//    private void parseArguments(String[] args) throws ArgumentException {
//        boolean missingArgument = false;
//        boolean missingMulticastIP = true;
//        boolean missingRoverID = true;
//
//        try {
//            for (int i = 0; i < args.length; i++) {
//                String argument = args[i];
//                if (argument.equals("-v") || argument.equals("--verbose")) {
//                    verboseOutputs = true;
//                }
//                if (argument.equals("-m") || argument.equals("--multicast-port")) {
//                    multicastPort = Integer.parseInt(args[i + 1]);
//                }
//                if (argument.equals("-p") || argument.equals("--port")) {
//                    ripPort = Integer.parseInt(args[i + 1]);
//                }
//                if (argument.equals("-i") || argument.equals("--ip")) {
//                    multicastIp = args[i + 1];
//                    missingMulticastIP = false;
//                }
//                if (argument.equals("-r") || argument.equals("--rover-id")) {
//                    roverID = Integer.parseInt(args[i + 1]);
//                    missingRoverID = false;
//                }
//                if (argument.equals("-d") || argument.equals("--destination" +
//                        "-ip")) {
//                    destinationIP = args[i + 1];
//                }
//            }
//        } catch (Exception e) {
//            throw new ArgumentException("Your arguments are incorrect. Please" +
//                    " see --help for help and usage.");
//        }
//        if (missingRoverID) {
//            System.err.println("Error: Missing Rover ID. Exiting...");
//        }
//        if (multicastPort == 0) {
//            System.out.println("Warning: Assuming Multicast port " + 20001);
//            multicastPort = 20001;
//            missingArgument = true;
//        }
//        if (ripPort == 0) {
//            System.out.println("Warning: Port not specified, using port " + 32768);
//            ripPort = 32768;
//            missingArgument = true;
//        }
//        if (missingMulticastIP) {
//            System.out.println("Warning: Multicast IP not specified, using " +
//                    "default IP 233.33.33.33");
//            multicastIp = "233.33.33.33";
//            missingArgument = true;
//        }
//        if (missingArgument) {
//            System.out.println("See --help for options");
//        }
//    }
    //TODO remove this redundant parser if the newer one works correctly.

    /**
     * This method creates two threads; a listener thread that listens on
     * the multicast channel for RIP packets, and a Timer thread that calls
     * sendRIPMessage every UPDATE_FREQUENCY intervals.
     * <p>
     * This method is called after parsing user arguments, ensuring that
     * before the threads are created, the variables are set according to the
     * flags.
     */
    private void startThreads() {
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


        Thread udpServerThread = new Thread(() -> {
            try {
                udpServer();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        });
        udpServerThread.start();
    }

    /**
     * Listens on the multicast ip for RIP packets.
     */
    private void startListening() {
        try {
            MulticastSocket socket = new MulticastSocket(multicastPort);
            byte[] buffer = new byte[256];
            InetAddress iGroup = InetAddress.getByName(multicastIp);
            socket.joinGroup(iGroup);

            while (true) {
                DatagramPacket datagramPacket = new DatagramPacket(buffer,
                        buffer.length);
                socket.receive(datagramPacket);
                RIPEntryHolder ripEntryHolder = unpackRIPEntries(datagramPacket);
                int receivedRoverID = ripEntryHolder.getRoverID();
                if (receivedRoverID == roverID) {   //Ignore self packets
                    continue;
                }
                ArrayList<RoutingTableEntry> receivedEntries =
                        ripEntryHolder.getArrayList();
                addSingleRoutingEntry(receivedRoverID, datagramPacket.getAddress());
                startTimerFor(datagramPacket.getAddress().getHostAddress(),
                        receivedRoverID);
                updateRoutingTable(receivedEntries, datagramPacket.getAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Called by a Timer thread every {@code UPDATE_INTERVAL} seconds. This
     * method calls getRIPPacket(), and sends the RIP Packet on the multicast
     * network.
     *
     * @throws UnknownHostException if a connection cannot be made by the
     *                              datagram packet.
     */
    private void sendRIPMessage() throws UnknownHostException {
        byte[] buffer = getRIPPacket(true);
        InetAddress iGroup = null;

        try {
            iGroup = InetAddress.getByName(multicastIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length,
                iGroup, multicastPort);

        DatagramSocket datagramSocket;
        try {
            datagramSocket = new DatagramSocket(ripPort);
            datagramSocket.send(datagramPacket);
            datagramSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Maintains a HashMap of timers.
     * <p>
     * When this function is called, it first searches if the timer for the
     * given IP exists. If it does, it is cancelled, initialized to a new
     * Timer, and started for {@code TIMEOUT} seconds.
     * <p>
     * If any timer reaches {@code TIMEOUT} successfully, it means that a
     * Rover timed out. The function then sets the distance of that Rover to
     * INFINITY, and all next hops that were this IP to INFINITY.
     *
     * @param ipAddress an IP Address of a Rover that sent this Rover a RIP
     *                  packet directly. Hence, that Rover is the neighbour
     *                  of this Rover, and a timer must be maintained for it.
     * @param roverID   the ID of the rover that send this Rover a RIP message.
     *                  This is NOT the ID of this rover!
     */
    private void startTimerFor(String ipAddress, int roverID) {
        Timer timer;
        if (timers.containsKey(ipAddress)) {
            timer = timers.get(ipAddress);
            timer.cancel();
        }
        timer = new Timer();
        timers.put(ipAddress, timer);
        String localIP = getPrivateIP(roverID);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println(localIP + " timed out!");
                RoutingTableEntry r = findRoutingTableEntryForIp(localIP);
                r.cost = INFINITY;
                ArrayList<RoutingTableEntry> arrayList =
                        getEntriesUsingIp(ipAddress);
                for (RoutingTableEntry routingTableEntry : arrayList) {
                    routingTableEntry.cost = INFINITY;
                }
                displayRoutingTable();
                try {
                    sendRIPMessage();       //Triggered update.
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }, TIMEOUT);
    }

    /**
     * Constructs a RIP packet and returns the byte array formed.
     *
     * @return the RIP packet in a byte array
     */
    private byte[] getRIPPacket(boolean isRequest) throws UnknownHostException {
        ArrayList<Byte> arrayList = new ArrayList<>();

        byte command = (byte) (isRequest ? 1 : 2);
        byte zero = 0;

        arrayList.add(command);     // Command

        byte version = 2;
        arrayList.add(version);     // Version

        /*
            The next field is supposed to be unused zeros, but this program
            modifies the RIP packet by including the rover ID here.
         */
        arrayList.add(zero);
        arrayList.add((byte) roverID);

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
    private RIPEntryHolder decodeRIPPacket(byte[] ripPacket) {
        ArrayList<RoutingTableEntry> arrayList = new ArrayList<>();
        int i = 0;
        i += 2; //Ignore command and version
//        byte command = ripPacket[i++];
//
//        byte version = ripPacket[i++];

        i++;
        int roverID = ripPacket[i++];
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
        return new RIPEntryHolder(arrayList, roverID);
    }

    /**
     * Fills ipAddress[] with the unsigned representation of the bytes from
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
    private RIPEntryHolder unpackRIPEntries(DatagramPacket datagramPacket) {
        byte[] receivedRipPacket = new byte[datagramPacket.getLength()];
        System.arraycopy(datagramPacket.getData(), 0, receivedRipPacket, 0,
                receivedRipPacket.length);
        RIPEntryHolder ripEntryHolder = decodeRIPPacket(receivedRipPacket);
        ArrayList<RoutingTableEntry> entries = ripEntryHolder.getArrayList();

        if (verboseOutputs) {
            System.out.println("Received the following Table Entries from " +
                    datagramPacket.getAddress().getHostAddress());
            System.out.println("Address\t\tNextHop\t\tCost");
            for (RoutingTableEntry r : entries) {
                System.out.println(r.IPAddress + "\t" + r.nextHop + "\t" + r.cost);
            }
        }

        return ripEntryHolder;
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
    private void addSingleRoutingEntry(int receivedRoverId,
                                       InetAddress inetAddress) {
        if (receivedRoverId == roverID) {
            return;
        }

        String ipToAdd = getPrivateIP(receivedRoverId);
        String nextHop = inetAddress.getHostAddress();
        boolean presentInTable = false;
        boolean changed = false;
        for (RoutingTableEntry routingTableEntry : routingTable) {
            if (routingTableEntry.IPAddress.equals(ipToAdd)) {
                presentInTable = true;
                if (routingTableEntry.cost != 1) {
                    routingTableEntry.nextHop = nextHop;
                    routingTableEntry.cost = 1;
                    changed = true;
                    break;
                }
            }
        }

        if (!presentInTable) {
            RoutingTableEntry r = new RoutingTableEntry(ipToAdd, DEFAULT_MASK
                    , nextHop, (byte) 1);
            routingTable.add(r);
            changed = true;
        }

        if (changed) {
            displayRoutingTable();
        }
    }

    /**
     * Generates an IP address of the form "10.0.{@code roverID}.0"
     *
     * @param roverID the id of this rover.
     * @return the generated IP
     */
    private String getPrivateIP(int roverID) {
        return "10.0." + roverID + ".0";
    }

    /**
     * Displays the current state of the Routing Table.
     */
    private void displayRoutingTable() {
        System.out.println();
        System.out.println("============================");
        System.out.println("Routing Table Entries");
        System.out.println("Address\t\tNextHop\t\tCost");
        for (RoutingTableEntry r : routingTable) {
            System.out.println(r.IPAddress + "/" + DEFAULT_MASK + "\t" + r.nextHop + "\t" + r.cost);
        }
        System.out.println();
    }


    /**
     * This is where the actual RIP shortest distance calculation actually
     * happens. This method updates the Rover's routingTable with the entries
     * from receivedTable.
     * <p>
     * The method also checks if the table was updated or not by using the
     * variable {@code updated}. If this variable is set to true by the end of
     * the method, the updated routing table is displayed to STDOUT. More
     * importantly, a RIP message is also multicasted on the network to
     * advertise the new found routes. This feature thus implements triggered
     * updates.
     *
     * @param receivedTable A RIP table that was received by this Rover.
     */
    private void updateRoutingTable(ArrayList<RoutingTableEntry> receivedTable, InetAddress inetAddress) throws UnknownHostException {
        String senderIp = inetAddress.getHostAddress();
        boolean updated = false;

        for (RoutingTableEntry r : receivedTable) {
            String ipAddress = r.IPAddress;
            int roverID = Integer.parseInt(ipAddress.split("\\.")[2]);
            RoutingTableEntry routingTableEntry =
                    findRoutingTableEntryForIp(ipAddress);
            if (roverID != this.roverID) {
                byte cost = (byte) (r.cost + 1);
                if (cost > INFINITY) {
                    cost = INFINITY;
                }

                if (routingTableEntry == null) {
                    String localIP = getPrivateIP(roverID);
                    routingTableEntry = new RoutingTableEntry(localIP,
                            DEFAULT_MASK, senderIp, cost);
                    routingTable.add(routingTableEntry);
                    updated = true;
                    continue;
                }
                if (r.nextHop.equals(selfIP)) {
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

        if (verboseOutputs || updated) {
            displayRoutingTable();
        }
        if (updated)
            sendRIPMessage();       //Triggered Updates for fast recovery
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

    /**
     * Listens for incoming Ripcom packets and, depending on whether the destination IP
     * included inside is the Rover's own IP, sends it to the next hop or displays the
     * message contents.
     *
     * @throws IOException          if either:
     *                              the datagram socket fails to initialize
     *                              (SocketException), or
     *                              the {@code server.receive(packet)} method throws an
     *                              IOException.
     * @throws InterruptedException if the thread is interrupted while executing
     *                              {@code sendRipcomPacket()}
     */
    private void udpServer() throws IOException, InterruptedException {
        DatagramSocket server = new DatagramSocket(udpPort);
        byte[] buffer = new byte[256];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            server.receive(packet);
            RipcomPacketManager ripcomPacketManager =
                    new RipcomPacketManager();
            RipcomPacket ripcomPacket =
                    ripcomPacketManager.constructRipcomPacket(packet.getData());

            System.out.println("Received a Ripcom packet.");
            System.out.println("Unpacking...");
            String destinationIP = ripcomPacket.getDestinationIP();
            if (destinationIP.equals(getPrivateIP(roverID))) {
                System.out.println(ripcomPacket.getContents());
            } else {
                System.out.println("Forwarding packet");
                sendRipcomPacket(destinationIP);
            }
        }
    }

    /**
     * Finds a RoutingTableEntry for a destination IP address. However, unlike {@code
     * findRoutingTableEntryForIp(destinationIP)}, this method will keep trying to find
     * the entry for a maximum of {@code UDP_SEND_MAX_RETRIES}. The method will also
     * sleep for {@code TIMEOUT} ms between two consecutive retries.
     *
     * @param destinationIP represents what IP to send a packet to. Will be of the form
     *                      10.0.{rover_id}.0
     * @return the found RoutingTableEntry, else null if {@code UDP_SEND_MAX_RETRIES}
     * is exceeded.
     * @throws InterruptedException if the thread is interrupted while sleeping (unlikely)
     */
    private RoutingTableEntry getEntryForDestinationIP(String destinationIP) throws InterruptedException {
        System.out.println("Finding next hop for " + destinationIP);
        RoutingTableEntry routingTableEntry = findRoutingTableEntryForIp(destinationIP);
        int retryCounter = 0;
        while (routingTableEntry == null || routingTableEntry.cost == INFINITY) {
            if (routingTableEntry == null) {
                System.out.println("Could not find an entry for " + destinationIP + ". " +
                        "Retrying in " + UPDATE_FREQUENCY + "ms ...");
            } else {
                System.out.println("Cannot send packet to " + destinationIP + " as the " +
                        "cost is " + INFINITY + ". Will retry again in " + UPDATE_FREQUENCY + " ms ...");
            }
            Thread.sleep(UPDATE_FREQUENCY);
            routingTableEntry = findRoutingTableEntryForIp(destinationIP);
            retryCounter++;
            if (retryCounter >= UDP_SEND_MAX_RETRIES) {
                System.out.println("Max retry limit reached, giving up on sending to " + destinationIP);
                return null;
            }
        }
        return routingTableEntry;
    }

    private void sendRipcomPacket(String destinationIP) throws IOException,
            InterruptedException {
        RoutingTableEntry routingTableEntry = getEntryForDestinationIP(destinationIP);
        if (routingTableEntry != null) {
            System.out.println("Sending to: " + routingTableEntry.nextHop);
            RipcomPacket ripcomPacket = new RipcomPacket(destinationIP, "A better way!");
            byte[] buffer = ripcomPacket.getBytes();

            DatagramSocket datagramSocket = new DatagramSocket();
            InetAddress inetAddress = InetAddress.getByName(routingTableEntry.nextHop);
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length,
                    inetAddress, udpPort);
            datagramSocket.send(datagramPacket);
            System.out.println("Sent successfully.");
        }
    }

    private void fragmentAndSend(String destinationIP){

    }
    /**
     * Starts a new Rover, and initialises threads.
     *
     * @param args STDIN. Passed to {@code ArgumentParser.parseArguments()}
     * @throws ArgumentException    if arguments were passed incorrectly.
     * @throws SocketException      see constructor
     * @throws UnknownHostException see constructor
     */
    public static void main(String[] args) throws ArgumentException, IOException, InterruptedException {
        Rover rover = new Rover();
        new ArgumentParser().parseArguments(args, rover);
        rover.startThreads();
        if (rover.destinationIP != null) {
            rover.sendRipcomPacket(rover.destinationIP);
        }
    }
}