package marudham;

/**
 * Abstract base class for all collection vehicles (abstraction + encapsulation).
 * Subclasses supply vehicle-specific cost and speed figures (polymorphism).
 */
public abstract class CollectionVehicle {

    public enum Status { AVAILABLE, EN_ROUTE, BROKEN_DOWN }

    private final String vehicleId;
    private final double capacityKg;
    private double loadKg;
    private String location;
    private Status status = Status.AVAILABLE;

    private double odometerKm;
    private double fuelCost;
    private double totalCollectedKg;
    private int collections;
    private int unloads;

    protected CollectionVehicle(String vehicleId, double capacityKg, String startLocation) {
        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            throw new IllegalArgumentException("Vehicle needs an id.");
        }
        if (capacityKg <= 0) {
            throw new IllegalArgumentException("Vehicle capacity must be positive.");
        }
        if (startLocation == null || startLocation.trim().isEmpty()) {
            throw new IllegalArgumentException("Vehicle needs a start location.");
        }
        this.vehicleId = vehicleId;
        this.capacityKg = capacityKg;
        this.location = startLocation;
    }

    // ----- polymorphic behaviour -----
    public abstract double getCostPerKm();

    public abstract double getSpeedKmph();

    public abstract String getTypeName();

    // ----- shared behaviour -----
    public double travelMinutes(double km) {
        return km / getSpeedKmph() * 60.0;
    }

    public double getRemainingCapacityKg() {
        return capacityKg - loadKg;
    }

    public boolean canCarry(double kg) {
        return kg > 0 && loadKg + kg <= capacityKg + 1e-9;
    }

    public void load(double kg) {
        if (!canCarry(kg)) {
            throw new IllegalStateException(String.format(
                    "%s cannot take %.1f kg (load %.1f / %.1f kg).", vehicleId, kg, loadKg, capacityKg));
        }
        loadKg += kg;
        totalCollectedKg += kg;
        collections++;
    }

    /** Empties the vehicle and returns the kilograms removed. */
    public double unloadAll() {
        double removed = loadKg;
        loadKg = 0.0;
        if (removed > 0) {
            unloads++;
        }
        return removed;
    }

    public void moveTo(String newLocation, double km) {
        this.location = newLocation;
        this.odometerKm += km;
        this.fuelCost += km * getCostPerKm();
    }

    /** Used after a tow/repair: places the vehicle without counting distance. */
    public void relocate(String newLocation) {
        this.location = newLocation;
    }

    public String getVehicleId() { return vehicleId; }
    public double getCapacityKg() { return capacityKg; }
    public double getLoadKg() { return loadKg; }
    public String getLocation() { return location; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public double getOdometerKm() { return odometerKm; }
    public double getFuelCost() { return fuelCost; }
    public double getTotalCollectedKg() { return totalCollectedKg; }
    public int getCollections() { return collections; }
    public int getUnloads() { return unloads; }

    @Override
    public String toString() {
        return String.format("%-5s %-11s cap=%6.0f kg load=%6.1f kg at=%-4s %-11s cost/km=%4.1f speed=%2.0f km/h",
                vehicleId, getTypeName(), capacityKg, loadKg, location, status, getCostPerKm(), getSpeedKmph());
    }
}
