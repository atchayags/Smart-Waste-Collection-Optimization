package marudham;

/**
 * A request to collect waste at one collection point.
 * Natural ordering = scheduling order: higher priority first, then earlier arrival.
 */
public class CollectionRequest implements Comparable<CollectionRequest> {
    private final int id;
    private final String pointId;
    private final Priority priority;
    private final long arrivalOrder;
    private final double estimatedKg;

    private RequestStatus status = RequestStatus.PENDING;
    private String assignedVehicleId = null;
    private String completedByVehicleId = null;
    private String deferReason = null;
    private int reassignCount = 0;
    private double collectedKg = 0.0;

    public CollectionRequest(int id, String pointId, Priority priority, long arrivalOrder, double estimatedKg) {
        this.id = id;
        this.pointId = pointId;
        this.priority = priority;
        this.arrivalOrder = arrivalOrder;
        this.estimatedKg = estimatedKg;
    }

    @Override
    public int compareTo(CollectionRequest other) {
        int byPriority = Integer.compare(other.priority.getWeight(), this.priority.getWeight());
        if (byPriority != 0) {
            return byPriority;
        }
        return Long.compare(this.arrivalOrder, other.arrivalOrder);
    }

    public int getId() { return id; }
    public String getLabel() { return String.format("R%02d", id); }
    public String getPointId() { return pointId; }
    public Priority getPriority() { return priority; }
    public long getArrivalOrder() { return arrivalOrder; }
    public double getEstimatedKg() { return estimatedKg; }
    public RequestStatus getStatus() { return status; }
    public String getAssignedVehicleId() { return assignedVehicleId; }
    public String getCompletedByVehicleId() { return completedByVehicleId; }
    public String getDeferReason() { return deferReason; }
    public int getReassignCount() { return reassignCount; }
    public double getCollectedKg() { return collectedKg; }

    void markAssigned(String vehicleId) {
        this.status = RequestStatus.ASSIGNED;
        this.assignedVehicleId = vehicleId;
        this.deferReason = null;
    }

    /** Returns the request to the pending state (breakdown / lost route). Arrival order is kept. */
    void returnToPending() {
        this.status = RequestStatus.PENDING;
        this.assignedVehicleId = null;
        this.reassignCount++;
    }

    void markDeferred(String reason) {
        this.status = RequestStatus.DEFERRED;
        this.assignedVehicleId = null;
        this.deferReason = reason;
    }

    void markPendingAgain() {
        this.status = RequestStatus.PENDING;
        this.deferReason = null;
    }

    void markCompleted(String vehicleId, double kg) {
        this.status = RequestStatus.COMPLETED;
        this.completedByVehicleId = vehicleId;
        this.collectedKg = kg;
    }

    @Override
    public String toString() {
        return String.format("%s  %-4s %-6s arrival#%-3d est=%6.1f kg  %-9s%s",
                getLabel(), pointId, priority, arrivalOrder, estimatedKg, status,
                assignedVehicleId == null ? "" : " veh=" + assignedVehicleId);
    }
}
