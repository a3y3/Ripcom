import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

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
    private HashMap<Integer, RipcomPacket> window = new HashMap<>();
    private HashMap<Integer, Timer> packetTimer = new HashMap<>();

    private DatagramSocket datagramSocket;
    private int seqNumber = 0;
    private int ackNumber = 0;
    private DataInputStream dataInputStream;
    private FileOutputStream fileOutputStream;
    private long lengthCounter;

    //final variables
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds
    private final static byte DEFAULT_MASK = 24;
    private final static int INFINITY = 16;     //Max hop count in RIP is 15
    private final static int TIMEOUT = 10000;   // unreachable at 10 secs
    private final static int UDP_SEND_MAX_RETRIES = 10;
    private final static int BUFFER_CAPACITY = 5000;
    private final static int WINDOW_SIZE = 1;
    private final static int PACKET_TIMEOUT = 1000; //retry sending packet in 1 second
    private final static int RECEIVE_SIZE = 5056;

    private final String selfIP;


    //flags and args
    boolean verboseOutputs;
    int verboseLevel = 200;
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

    /**
     * Used to create a new DatagramSocket with a {@code ripPort}. This method exists
     * because {@code ripPort} is not known until after {@code ArgumentParser
     * .parseArguments()} is executed.
     */
    private void assignDatagramSocket() {
        try {
            datagramSocket = new DatagramSocket(ripPort);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

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
        byte[] buffer = getRIPPacket();
        InetAddress iGroup = null;

        try {
            iGroup = InetAddress.getByName(multicastIp);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length,
                iGroup, multicastPort);

        try {
            datagramSocket.send(datagramPacket);
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
    private byte[] getRIPPacket() throws UnknownHostException {
        ArrayList<Byte> arrayList = new ArrayList<>();

        byte command = (byte) (1);
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
    private void updateRoutingTable(ArrayList<RoutingTableEntry> receivedTable,
                                    InetAddress inetAddress) throws UnknownHostException {
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
     *                              {@code sendPacket()}
     */
    private void udpServer() throws IOException, InterruptedException {
        DatagramSocket server = new DatagramSocket(udpPort);
        byte[] buffer = new byte[RECEIVE_SIZE];
        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            server.receive(packet);
            RipcomPacketManager ripcomPacketManager =
                    new RipcomPacketManager();
            RipcomPacket ripcomPacket =
                    ripcomPacketManager.getRipcomPacket(packet.getData());

            if (verboseLevel <= 1) {
                System.out.println("Received a Ripcom packet.");
                System.out.println("Unpacking...");
                System.out.println(ripcomPacket);
            }
            String destinationIP = ripcomPacket.getDestinationIP();
            if (destinationIP.equals(getPrivateIP(roverID))) {
                acceptPacket(ripcomPacket);
            } else {
                if (verboseLevel <= 1) {
                    System.out.println("Forwarding packet");
                }
                sendPacket(ripcomPacket);
            }
        }
    }

    /**
     * Cancels a timer for a packet and removes it from {@code window}, and
     * {@code packetTimer}.
     *
     * @param number the SEQ or FIN number of the packet.
     */
    private void cancelTimerForPacket(int number) {
        window.remove(number);
        if (verboseLevel <= 1) {
            System.out.println("Number of elements in window: " + window.size());
            System.out.println("Removing timer for this packet: " + number);
        }
        Timer timer = packetTimer.get(number);
        timer.cancel();
        packetTimer.remove(number);
    }

    /**
     * Used to do various operations depending upon the packetType inside a Ripcom
     * packet. If the received message is:
     * 1. an ACK, this function adds the next expected packet to the window and sends
     * it.
     * 2. a SEQ, it sends an ACK for the next packet that is expected.
     * 3. a FIN, it sends a FIN_ACK.
     * 4. a FIN_ACK, it stops the file sending.
     *
     * @param ripcomPacket a ripcomPacket that was intended for this Rover. In other
     *                     words, this is a packet that has the destination address as
     *                     the address of this Rover, and should be opened and
     *                     inspected instead of forwarding.
     * @throws IOException          see {@code addToWindow()}
     * @throws InterruptedException see {@code sendPacket()}
     */
    private void acceptPacket(RipcomPacket ripcomPacket) throws IOException, InterruptedException {
        Type packetType = ripcomPacket.getPacketType();
        switch (packetType) {
            case SEQ:
                if (verboseLevel <= 1) {
                    System.out.println("Received SEQ " + ripcomPacket.getNumber());
                }
                boolean expectedPacket = true;
                if (ripcomPacket.getNumber() != ackNumber) {
                    expectedPacket = false;
                    if (verboseLevel <= 1) {
                        System.out.println("Received a wrong packet: " + ripcomPacket.getNumber());
                        System.out.println("Sending ACK again for packet: " + ackNumber);
                    }
                }
                if (expectedPacket) {
                    ackNumber++;
                    byte[] message = ripcomPacket.getContents();
                    if (fileOutputStream == null) {
                        fileOutputStream =
                                new FileOutputStream("output");
                    }
                    fileOutputStream.write(message);
                }
                String destinationIP = ripcomPacket.getSourceIP();
                RipcomPacket ackPacket = new RipcomPacket(
                        destinationIP,
                        getPrivateIP(roverID),
                        Type.ACK,
                        ackNumber,
                        0,
                        new byte[0]);
                window.remove(ackNumber - 1);
                window.put(ackNumber, ackPacket);
                sendPacket(ackPacket);
                break;
            case ACK:
                int number = ripcomPacket.getNumber();
                if (verboseLevel <= 1) {
                    System.out.println("Received ACK " + number);
                }
                cancelTimerForPacket(number - 1);
                addToWindow(ripcomPacket.getSourceIP());
                RipcomPacket nextPacket = window.get(number);
                sendPacket(nextPacket);
                startTimerForPacket(nextPacket.getNumber());
                break;
            case FIN:
                if (verboseLevel <= 1) {
                    System.out.println("Received FIN " + ripcomPacket.getNumber());
                }
                byte[] message = ripcomPacket.getContents();
                fileOutputStream.write(message);
                fileOutputStream.close();
                System.out.println("Received message successfully. See file output " +
                        "for the final output.");
                ackNumber++;
                destinationIP = ripcomPacket.getSourceIP();
                RipcomPacket finAckPacket = new RipcomPacket(
                        destinationIP,
                        getPrivateIP(roverID),
                        Type.FIN_ACK,
                        ackNumber,
                        0,
                        new byte[0]);
                window.remove(ackNumber - 1);
                if (verboseLevel <= 1) {
                    System.out.println("Sending FIN_ACK packet, ackNumber is " + ackNumber);
                }
                window.put(ackNumber, finAckPacket);
                sendPacket(finAckPacket);
                break;
            case FIN_ACK:
                if (verboseLevel <= 1) {
                    System.out.println("Received FIN_ACK " + ripcomPacket.getNumber());
                }
                cancelTimerForPacket(seqNumber - 1);
                System.out.println("Finished sending all data");
                if (verboseLevel <= 1) {
                    System.out.println("Completed sending. Window size: " + window.size());
                    System.out.println("Packet timer size: " + packetTimer.size());
                }
                break;
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
        if (verboseLevel <= 1) {
            System.out.println("Finding next hop for " + destinationIP);
        }
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

    /**
     * Sends a RipcomPacket.
     *
     * @param ripcomPacket the packet that needs to be sent
     * @throws IOException          see {@code datagramSocket.send(datagramPacket)}
     * @throws InterruptedException see {@code getEntryForDestinationIP()}
     */
    private void sendPacket(RipcomPacket ripcomPacket) throws IOException, InterruptedException {
        String destinationIP = ripcomPacket.getDestinationIP();
        RoutingTableEntry routingTableEntry = getEntryForDestinationIP(destinationIP);
        if (routingTableEntry != null) {
            if (verboseLevel <= 1) {
                System.out.println("Sending to: " + routingTableEntry.nextHop);
            }
            byte[] buffer = ripcomPacket.getBytes();
            DatagramSocket datagramSocket = new DatagramSocket();
            InetAddress inetAddress = InetAddress.getByName(routingTableEntry.nextHop);
            DatagramPacket datagramPacket = new DatagramPacket(buffer, buffer.length,
                    inetAddress, udpPort);
            datagramSocket.send(datagramPacket);
            if (verboseLevel <= 1) {
                System.out.println("Sent successfully.");
            }
        }
    }

    /**
     * Calculates what the size of contents[] should be, then strips down the extra
     * read characters and returns a new buffer.
     * <p>
     * This is only called when {@code BUFFER_SIZE} exceeds the number of bytes read
     * from the input stream.
     *
     * @param contents the contents that need to be stripped down.
     * @return the stripped down array.
     */
    private byte[] stripContents(byte[] contents) {
        int size = 0;
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] == 0) {
                size = i;
                break;
            }
        }

        byte[] buffer = new byte[size];
        System.arraycopy(contents, 0, buffer, 0, size);
        return buffer;
    }

    /**
     * Creates a new RipcomPacket, adds the packet to the window, and increments the
     * sequence number.
     *
     * @param destinationIP the IP to which the packet is to be transmitted. Of the
     *                      form 10.0.{@code roverID}.0
     * @throws IOException while reading from the input stream.
     */
    private void addToWindow(String destinationIP) throws IOException {
        Type type = Type.SEQ;
        byte[] contents = new byte[BUFFER_CAPACITY];
        if (dataInputStream.read(contents) == -1) {
            type = Type.FIN;
        }
        if (BUFFER_CAPACITY > lengthCounter) {
            contents = stripContents(contents);
        } else {
            lengthCounter -= BUFFER_CAPACITY;
        }

        RipcomPacket ripcomPacket = new RipcomPacket(destinationIP,
                getPrivateIP(roverID), type, seqNumber, contents.length, contents);
        window.put(seqNumber, ripcomPacket);
        seqNumber++;
    }

    /**
     * Starts a new Timer for each sent packet. When the timer runs out, a new packet
     * is sent again. Note that the newly sent packet will also have a timer attached
     * to it.
     *
     * @param number the SEQ (or FIN) number of the packet, used for looking up the
     *               packet itself in the window.
     */
    private void startTimerForPacket(int number) {
        Timer timer = new Timer();
        packetTimer.put(number, timer);

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (verboseLevel <= 1) {
                    System.out.println("Packet number " + number + " timed out!");
                }
                RipcomPacket ripcomPacket = window.get(number);
                try {
                    if (ripcomPacket != null) {
                        sendPacket(ripcomPacket);
                        startTimerForPacket(ripcomPacket.getNumber());
                    }
                } catch (IOException | InterruptedException e) {
                    System.err.println();
                }

            }
        }, PACKET_TIMEOUT);
    }

    /**
     * If the flags -f and -d are set, this method starts sending packets according to
     * {@code WINDOW_SIZE} to the destination address.
     *
     * @throws IOException          see {@code sendPacket}
     * @throws InterruptedException see {@code sendPacket}
     */
    private void startSendingIfFlag() throws IOException, InterruptedException {
        if (destinationIP != null) {
            File file = new File(fileName);
            lengthCounter = file.length();
            System.out.println();
            dataInputStream = new DataInputStream(new FileInputStream(file));
            for (int i = 0; i < WINDOW_SIZE; i++) {
                addToWindow(destinationIP);
            }
            for (int i = 0; i < WINDOW_SIZE; i++) {
                RipcomPacket ripcomPacket = window.get(i);
                sendPacket(ripcomPacket);
                startTimerForPacket(ripcomPacket.getNumber());
            }
        }
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
        rover.assignDatagramSocket();
        rover.startThreads();
        rover.startSendingIfFlag();
    }
}