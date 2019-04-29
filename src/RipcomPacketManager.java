import java.nio.ByteBuffer;

/**
 * For now, the purpose of this class is only to build a new RipcomPacket from a byte
 * array.
 * <p>
 * The function {@code getRipcomPacket(byte[] packet)} should not be in RipcomPacket as
 * a new RipcomPacket MUST be instantiated with a constructor containing all the
 * different values. This is  to prevent accidental calls to {@code getBytes()} on a
 * RipcomPacket that was created with a default constructor.
 *
 * @author Soham Dongargaonkar [sd4324] on 19/4/19
 */

class RipcomPacketManager {
    private static final int DESTINATION_IP_OFFSET = 0;
    private static final int SOURCE_IP_OFFSET = 4;
    private static final int PACKET_TYPE_OFFSET = 8;
    private static final int NUMBER_OFFSET = 9;
    private static final int LENGTH_OFFSET = 13;
    private static final int CONTENTS_OFFSET = 17;

    /**
     * Given a byte representation of a ripcom packet, creates a new instance of
     * RipcomPacket and returns it.
     *
     * @param packet byte representation of a RipcomPacket
     * @return instance of RipcomPacket
     */
    RipcomPacket getRipcomPacket(byte[] packet) {
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
            case 3:
                packetType = RipcomPacket.Type.FIN_ACK;
                break;
            default:
                packetType = RipcomPacket.Type.FIN;
        }

        byte[] numsArray = new byte[4];
        System.arraycopy(packet, NUMBER_OFFSET, numsArray, 0, 4);
        int number = ByteBuffer.wrap(numsArray).getInt();

        byte[] lengthArray = new byte[4];
        System.arraycopy(packet, LENGTH_OFFSET, lengthArray, 0, 4);
        int length = ByteBuffer.wrap(lengthArray).getInt();

        byte[] contents = new byte[length];
        System.arraycopy(packet, CONTENTS_OFFSET, contents, 0, length);

        return new RipcomPacket(destinationIP.toString(), sourceIP.toString(), packetType,
                number, length, contents);
    }

    /**
     * Used for getting a String IP address from the destination and source offsets (to
     * avoid duplicate code!)
     *
     * @param offset either the source IP or the destination IP offset.
     * @param packet representation of a RipcomPacket in bytes
     * @return the corresponding IP address (depending on {@code offset} in
     * StringBuilder form.
     */
    private StringBuilder getIP(int offset, byte[] packet) {
        StringBuilder ip = new StringBuilder();
        for (int i = offset; i < offset + 4; i++) {
            int ipPart = Byte.toUnsignedInt(packet[i]);
            ip.append(ipPart);
            if (i != offset + 3) {
                ip.append('.');
            }
        }
        return ip;
    }
}