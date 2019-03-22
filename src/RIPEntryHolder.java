import java.util.ArrayList;

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
