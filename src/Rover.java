import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class Rover extends Thread {
    private int roverID; //can't use id, class Thread has an int id too.
    private String MULTICAST_IP;
    private ArrayList<RoutingTableEntry> routingTable;

    private final static int MULTICAST_PORT = 20001;
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds

    private Rover(int roverID) {
        this.roverID = roverID;
        routingTable = new ArrayList<>();

//        Thread listenerThread = new Thread(new Runnable() {
//            @Override
//            public void run() {
//                startListening();
//            }
//        });
//        listenerThread.start();
//
//        new Timer().scheduleAtFixedRate(new TimerTask() {
//            @Override
//            public void run() {
//                try {
//                    sendRIPMessage();
//                } catch (UnknownHostException e) {
//                    e.printStackTrace();
//                }
//            }
//        }, 0, UPDATE_FREQUENCY);
    }

    public static void main(String[] args) throws ArgumentException, UnknownHostException {
        String usage = "Usage: java Rover <rover_id>";
        int roverID;

        try {
            roverID = Integer.parseInt(args[0]);
        } catch (Exception n) {
            throw new ArgumentException(usage); //Prevents further execution
        }

        Rover rover = new Rover(roverID);
        RoutingTableEntry r = new RoutingTableEntry();
        r.IPAddress = "255.255.255.255";
        r.cost = 5;
        r.mask = 24;
        r.nextHop = "238.238.238.238";
        rover.routingTable.add(r);
        byte[] packet = rover.getRIPPacket(false);
        ArrayList<RoutingTableEntry> arr = rover.decodeRIPPacket(packet);
        System.out.println("Address\tNextHop\tCost");

        for (RoutingTableEntry routingTableEntry : arr){
            System.out.println(routingTableEntry.IPAddress + "\t" + routingTableEntry.nextHop + "\t" + routingTableEntry.cost);
        }
//        for (int i = 0; i < packet.length; i++) {
//            System.out.printf("%02X " + ((i + 1) % 4 == 0 ? "\n" : ""),
//                    packet[i]);
//        }
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
                String received = new String(
                        datagramPacket.getData(), 0, datagramPacket.getLength());

                if (received.equals("exit")) {
                    System.out.println("Exiting...");
                    break;
                } else {
                    System.out.println("Received message: " + received);
                    System.out.println("Received from: " + datagramPacket.getAddress());
                }
            }

            socket.leaveGroup(iGroup);
            socket.close();
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
    private ArrayList<RoutingTableEntry> decodeRIPPacket(byte[] ripPacket) {
        ArrayList<RoutingTableEntry> arrayList = new ArrayList<>();
        int i = 0;
        while (i < ripPacket.length) {
            byte command = ripPacket[i++];

            byte version = ripPacket[i++];

            i += 2;

            byte AFI[] = new byte[2];
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
}