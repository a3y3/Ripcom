import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimerTask;

/**
 * The only purpose of this class is to send a multicast message.
 * This class will be instantiated by a Rover through the Timer thread and
 * told what to send.
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