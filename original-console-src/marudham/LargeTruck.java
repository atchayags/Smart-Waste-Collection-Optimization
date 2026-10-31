package marudham;

/** Heavy truck: large capacity, but higher fuel cost and lower average speed. */
public class LargeTruck extends CollectionVehicle {
    private static final double COST_PER_KM = 14.0;
    private static final double SPEED_KMPH = 25.0;

    public LargeTruck(String vehicleId, double capacityKg, String startLocation) {
        super(vehicleId, capacityKg, startLocation);
    }

    @Override
    public double getCostPerKm() {
        return COST_PER_KM;
    }

    @Override
    public double getSpeedKmph() {
        return SPEED_KMPH;
    }

    @Override
    public String getTypeName() {
        return "Large Truck";
    }
}
