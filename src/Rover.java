import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Rover extends Thread {
    private ArrayList<RoutingTableEntry> routingTable;

    private final static int MULTICAST_PORT = 20001;
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds
    private final static byte DEFAULT_MASK = 32;

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

        new Rover();
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
                updateRoutingTable(receivedEntries);
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
        System.out.println("Received the following Table Entries:");
        System.out.println("Address\t\tNextHop\t\tCost");
        for (RoutingTableEntry r : entries) {
            System.out.println(r.IPAddress + " " + r.nextHop + " " + r.cost);
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
    private void addSingleRoutingEntry(InetAddress inetAddress) {
        String ipToAdd = inetAddress.getHostAddress();
        boolean presentInTable = false;
        boolean changed = false;
        for (RoutingTableEntry r : routingTable) {
            if (r.IPAddress.equals(ipToAdd)) {       //TODO No. Must support mask
                if (r.cost != 1) {
                    r.nextHop = r.IPAddress;
                    r.cost = 1;
                    presentInTable = true;
                    changed = true;
                    break;
                }
                else presentInTable = true;
            }
        }

        if (!presentInTable) {
            RoutingTableEntry r = new RoutingTableEntry(ipToAdd, DEFAULT_MASK
                    , ipToAdd, (byte) 1);
            routingTable.add(r);
            changed = true;
        }

        if(changed) {
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
        System.out.println("Address\t\t\t\tNextHop\t\tCost");
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
    private void updateRoutingTable(ArrayList<RoutingTableEntry> receivedTable) {

    }
}