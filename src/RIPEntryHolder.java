import java.util.ArrayList;

/**
 * The purpose of this class is to hold an entry for an
 * {@code ArrayList<RoutingTableEntry>} and a rover ID. This is used to
 * "return" 2 values from a function, specifically {@code Rover
 * .unpackRIPEntries}.
 *
 * @author Soham Dongargaonkar
 */
class RIPEntryHolder {
    private ArrayList<RoutingTableEntry> arrayList;
    private int roverID;

    RIPEntryHolder(ArrayList<RoutingTableEntry> arrayList, int roverID) {
        this.arrayList = arrayList;
        this.roverID = roverID;
    }

    ArrayList<RoutingTableEntry> getArrayList() {
        return arrayList;
    }

    int getRoverID() {
        return roverID;
    }
}
