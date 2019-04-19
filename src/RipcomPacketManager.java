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

    RipcomPacketManager() {
    }


//    void displayPacketContents() {
//        int length = Byte.toUnsignedInt(packet[LENGTH_OFFSET]);
//        for (int i = CONTENTS_OFFSET; i < CONTENTS_OFFSET + length; i++){
//            System.out.print((char)packet[i]);
//        }
//    }

    RipcomPacket constructRipcomPacket(byte[] packet){
        StringBuilder ipAddress = new StringBuilder();
        for (int i = IP_OFFSET; i < IP_OFFSET + 4; i++) {
            int ipPart = Byte.toUnsignedInt(packet[i]);
            ipAddress.append(ipPart);
            if(i != IP_OFFSET + 3) {
                ipAddress.append('.');
            }
        }

        int length = Byte.toUnsignedInt(packet[LENGTH_OFFSET]);
        StringBuilder contents = new StringBuilder();
        for (int i = CONTENTS_OFFSET; i < CONTENTS_OFFSET + length; i++){
            contents.append((char) packet[i]);
        }

        return new RipcomPacket(ipAddress.toString(), contents.toString());
    }
}
