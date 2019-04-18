/**
 * Has methods that unpack a Ripcom packet.
 *
 * @author Soham Dongargaonkar [sd4324] on 17/4/19
 */
public class RipcomPacketUnpacker {
    private byte[] packet;

    private static final int IP_OFFSET = 0;

    RipcomPacketUnpacker(byte[] packet) {
        this.packet = packet;
    }

    void unpack() {
        String destination = getDestination();
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
}
