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

        Thread listenerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                startListening();
            }
        });
        listenerThread.start();

        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                sendRIPMessage();
            }
        }, 0, UPDATE_FREQUENCY);
    }

    public static void main(String[] args) throws ArgumentException {
        String usage = "Usage: java Rover <rover_id>";
        int roverID;

        try {
            roverID = Integer.parseInt(args[0]);
        } catch (Exception n) {
            throw new ArgumentException(usage); //Prevents further execution
        }

        Rover rover = new Rover(roverID);
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

    private void sendRIPMessage() {
        String message = "ID:" + roverID;
        byte[] buffer = message.getBytes();
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
     * Constructs a RIP packet and
     *
     * @return
     */
    private byte[] getRIPBytes() {
        int size = routingTable.size();
        int numberOfBytes = 4 + 4 * 5 * size;

        byte[] RIPPacket = new byte[numberOfBytes];
        return RIPPacket;
    }
}