import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 * @author Soham Dongargaonkar [sd4324] on 19/4/19
 */
class RipcomPacket {
    enum Type {
        SEQ,
        ACK,
        FIN
    }

    private String destinationIP;   //bytes 0 - 3
    private String sourceIP;        //bytes 4 - 7
    private Type packetType;        //bytes 8
    private String contents;        //bytes 9 - ..


    RipcomPacket(String destinationIP, String sourceIP, Type packetType,
                 String contents) {
        this.destinationIP = destinationIP;
        this.sourceIP = sourceIP;
        this.packetType = packetType;
        this.contents = contents;
    }


    String getDestinationIP() {
        return destinationIP;
    }

    String getSourceIP() {
        return sourceIP;
    }

    String getContents() {
        return contents;
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

        ipAddress = InetAddress.getByName(sourceIP).getAddress();
        for (byte b : ipAddress) {
            arrayList.add(b);                           //Source IP
        }

        if (packetType == Type.SEQ) {
            arrayList.add((byte) 1);
        } else if (packetType == Type.ACK) {          //Type
            arrayList.add((byte) 2);
        } else {
            arrayList.add((byte) 0);
        }

        byte[] contentsBytes = contents.getBytes();
        for (byte b : contentsBytes) {
            arrayList.add(b);                           //Message
        }

        byte[] buffer = new byte[arrayList.size()];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = arrayList.get(i);
        }
        return buffer;
    }
}
