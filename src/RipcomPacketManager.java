/**
 * @author Soham Dongargaonkar [sd4324] on 19/4/19
 */

class RipcomPacketManager {
    private static final int DESTINATION_IP_OFFSET = 0;
    private static final int SOURCE_IP_OFFSET = 4;
    private static final int PACKET_TYPE_OFFSET = 8;
    private static final int CONTENTS_OFFSET = 9;

    RipcomPacketManager() {
    }

    /**
     * Given a byte representation of a ripcom packet, creates a new instance of
     * RipcomPacket and returns it.
     *
     * @param packet byte representation of a RipcomPacket
     * @return instance of RipcomPacket
     */
    RipcomPacket constructRipcomPacket(byte[] packet) {
        StringBuilder destinationIP = getIP(DESTINATION_IP_OFFSET, packet);

        StringBuilder sourceIP = getIP(SOURCE_IP_OFFSET, packet);

        RipcomPacket.Type packetType;
        int type = packet[PACKET_TYPE_OFFSET];
        switch (type) {
            case 1:
                packetType = RipcomPacket.Type.SEQ;
                break;
            case 2:
                packetType = RipcomPacket.Type.ACK;
                break;
            default:
                packetType = RipcomPacket.Type.FIN;
        }

        StringBuilder contents = new StringBuilder();
        for (int i = CONTENTS_OFFSET; i < packet.length; i++) {
            contents.append((char) packet[i]);
        }

        return new RipcomPacket(destinationIP.toString(), sourceIP.toString(), packetType,
                contents.toString());
    }

    private StringBuilder getIP(int offset, byte[] packet){
        StringBuilder ip = new StringBuilder();
        for (int i = offset; i < offset+ 4; i++) {
            int ipPart = Byte.toUnsignedInt(packet[i]);
            ip.append(ipPart);
            if (i != offset + 3) {
                ip.append('.');
            }
        }
        return ip;
    }
}
