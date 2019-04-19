import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * @author Soham Dongargaonkar [sd4324] on 19/4/19
 */
class RipcomPacket {
    private String destinationIP;

    private int length;
    private String contents;

    public RipcomPacket(String destinationIP, String contents) {
        this.destinationIP = destinationIP;
        this.contents = contents;
        this.length = contents.length();
    }

    String getDestinationIP() {
        return destinationIP;
    }

    String getContents() {
        return contents;
    }

    int getLength(){
        return length;
    }

    /**
     * Constructs a Ripcom packet and returns it packed and ready to be sent, in a byte
     * array.
     *
     * @throws UnknownHostException if the destination IP form is incorrect.
     */
    byte[] getBytes() throws UnknownHostException {
        ArrayList<Byte> arrayList = new ArrayList<>();
        byte[] ipAddress = InetAddress.getByName(destinationIP).getAddress();
        for (byte b : ipAddress) {
            arrayList.add(b);                           //Destination IP
        }

        arrayList.add((byte) contents.length());           //Length
        byte[] helloBytes = contents.getBytes();
        for (byte b : helloBytes) {
            arrayList.add(b);                           //Message
        }

        byte[] buffer = new byte[arrayList.size()];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = arrayList.get(i);
        }
        return buffer;
    }
}
