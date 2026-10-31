package marudham;

/** One immutable entry of the collection history / audit log. */
public class CollectionRecord {

    public enum Type {
        ASSIGNMENT, COLLECTION, UNLOAD, REROUTE, ROAD_BLOCKED, ROAD_REOPENED,
        BREAKDOWN, REASSIGNMENT, REPAIR, DEFERRED
    }

    private final int sequence;
    private final int minute;          // simulated clock (minutes since Day 1 00:00)
    private final Type type;
    private final String vehicleId;    // may be "-"
    private final int requestId;       // 0 when not tied to a request
    private final String locationId;
    private final double kg;
    private final double km;
    private final String details;

    public CollectionRecord(int sequence, int minute, Type type, String vehicleId, int requestId,
                            String locationId, double kg, double km, String details) {
        this.sequence = sequence;
        this.minute = minute;
        this.type = type;
        this.vehicleId = vehicleId == null ? "-" : vehicleId;
        this.requestId = requestId;
        this.locationId = locationId == null ? "-" : locationId;
        this.kg = kg;
        this.km = km;
        this.details = details == null ? "" : details;
    }

    public int getSequence() { return sequence; }
    public int getMinute() { return minute; }
    public Type getType() { return type; }
    public String getVehicleId() { return vehicleId; }
    public int getRequestId() { return requestId; }
    public String getLocationId() { return locationId; }
    public double getKg() { return kg; }
    public double getKm() { return km; }
    public String getDetails() { return details; }

    public static String formatClock(int minute) {
        int day = minute / 1440 + 1;
        int inDay = minute % 1440;
        return String.format("D%d %02d:%02d", day, inDay / 60, inDay % 60);
    }

    @Override
    public String toString() {
        return String.format("#%03d [%s] %-13s veh=%-5s req=%-4s loc=%-4s kg=%7.1f km=%5.1f | %s",
                sequence, formatClock(minute), type, vehicleId,
                requestId == 0 ? "-" : String.format("R%02d", requestId),
                locationId, kg, km, details);
    }
}
