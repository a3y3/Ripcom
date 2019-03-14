import java.io.IOException;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

public class Rover extends Thread {
    private int roverID; //can't use id, class Thread has an int id too.
    private final static int UPDATE_FREQUENCY = 5000; //5 seconds

    private Rover(int roverID) {
        this.roverID = roverID;
    }

    public static void main(String[] args) {
        String usage = "Usage: java Rover <rover_id>";
        int roverID;

        try {
            roverID = Integer.parseInt(args[0]);
        } catch (NumberFormatException n) {
            System.err.println(usage);
            // Exception is rethrown to avoid further execution
            throw new NumberFormatException();
        } catch (ArrayIndexOutOfBoundsException a){
            System.err.println(usage);
            throw new ArrayIndexOutOfBoundsException();
        }

        Rover rover = new Rover(roverID);
        rover.start(); //start listening for multi casted messages

        MulticastSender multicastSender = new MulticastSender(rover.roverID);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(multicastSender, 0, UPDATE_FREQUENCY);
    }

    @Override
    public void run() {
        try {
            MulticastSocket socket = new MulticastSocket(MulticastSender.MULTICAST_PORT);
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
}

/**
 * The only purpose of this class is to send a multicast message. This class
 * will be instantiated by a Rover through the Timer thread and told what to
 * send.
 */
class MulticastSender extends TimerTask {
    final static int MULTICAST_PORT = 20001;
    private int roverID;

    MulticastSender(int roverID) {
        this.roverID = roverID;
    }

    @Override
    public void run() {
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
}