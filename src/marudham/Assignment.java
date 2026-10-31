package marudham;

/**
 * Tracks one vehicle that is currently serving one request:
 * which leg it is on and the remaining planned path.
 * path.getNodes().get(0) is always the vehicle's current location.
 */
public class Assignment {

    public enum Stage { TO_DEPOT_UNLOAD, TO_POINT, ARRIVED }

    private final CollectionVehicle vehicle;
    private final CollectionRequest request;
    private Stage stage;
    private PathResult path;
    private int reroutes = 0;

    public Assignment(CollectionVehicle vehicle, CollectionRequest request, Stage stage, PathResult path) {
        this.vehicle = vehicle;
        this.request = request;
        this.stage = stage;
        this.path = path;
    }

    public CollectionVehicle getVehicle() { return vehicle; }
    public CollectionRequest getRequest() { return request; }
    public Stage getStage() { return stage; }
    public PathResult getPath() { return path; }
    public int getReroutes() { return reroutes; }
    public int getRemainingHops() { return path.getHops(); }

    void setStage(Stage stage) { this.stage = stage; }
    void setPath(PathResult path) { this.path = path; }
    void countReroute() { reroutes++; }

    @Override
    public String toString() {
        return String.format("%s -> %s [%s] remaining: %s",
                vehicle.getVehicleId(), request.getLabel(), stage, path);
    }
}
