import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * @author Soham Dongargaonkar [sd4324] on 19/4/19
 */
class RipcomPacketManager {
    private static final int IP_OFFSET = 0;
    private static final int LENGTH_OFFSET = 4;
    private static final int CONTENTS_OFFSET = 5;
    private byte[] packet;

    RipcomPacketManager() {
    }

    RipcomPacketManager(byte[] packet) {
        this.packet = packet;
    }

    /**
     * Constructs a Ripcom packet and returns it packed and ready to be sent, in a byte
     * array.
     *
     * @param destinationIP the local IP of the Rover to which the packet is supposed
     *                      to be sent to. This destination IP represents the IP of the
     *                      final Rover to which the packet is intended to be sent.
     * @throws UnknownHostException if the destination IP form is incorrect.
     */
    byte[] getRipcomPacket(String destinationIP) throws UnknownHostException {
        ArrayList<Byte> arrayList = new ArrayList<>();

        byte[] ipAddress = InetAddress.getByName(destinationIP).getAddress();
        for (byte b : ipAddress) {
            arrayList.add(b);                           //Destination IP
        }

        String hello = "Hello World";
        arrayList.add((byte) hello.length());           //Length
        byte[] helloBytes = hello.getBytes();
        for (byte b : helloBytes) {
            arrayList.add(b);                           //Message
        }
        byte[] buffer = new byte[arrayList.size()];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = arrayList.get(i);
        }

        return buffer;
    }

    /**
     * A Ripcom packet will contain a destination ip address. This function returns
     * a String representation of that address.
     *
     * @return a String representing the 4 byte destination that is contained in the
     * Ripcom {@code packet}.
     */
    String getDestination() {
        StringBuilder ipAddress = new StringBuilder();
        for (int i = IP_OFFSET; i < IP_OFFSET + 4; i++) {
            int ipPart = Byte.toUnsignedInt(packet[i]);
            ipAddress.append(ipPart);
            if(i != IP_OFFSET + 3) {
                ipAddress.append('.');
            }
        }
        return ipAddress.toString();
    }

    void displayPacketContents() {
        int length = Byte.toUnsignedInt(packet[LENGTH_OFFSET]);
        for (int i = CONTENTS_OFFSET; i < CONTENTS_OFFSET + length; i++){
            System.out.print((char)packet[i]);
        }
    }
}
