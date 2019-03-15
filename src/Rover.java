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
//                sendRIPMessage();
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
        for (int i = 0; i < packet.length; i++) {
            System.out.printf("%02X " + ((i + 1) % 4 == 0 ? "\n" : ""),
                    packet[i]);
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
        String message = "ID:" + roverID;
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
}