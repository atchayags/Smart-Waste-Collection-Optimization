package marudham;

/** Compact truck: cheap to run and quick in narrow streets, but small capacity. */
public class SmallTruck extends CollectionVehicle {
    private static final double COST_PER_KM = 6.0;
    private static final double SPEED_KMPH = 30.0;

    public SmallTruck(String vehicleId, double capacityKg, String startLocation) {
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
        return "Small Truck";
    }
}
