package marudham;

/**
 * A waste collection point located at a node of the road network.
 * Encapsulation: the waste level can only change through validated methods.
 */
public class CollectionPoint {
    public static final double MAX_WASTE_KG = 5000.0;

    private final String locationId;
    private final String name;
    private final String zone;
    private double wasteKg;

    public CollectionPoint(String locationId, String name, String zone, double wasteKg) {
        if (locationId == null || locationId.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection point needs a location id.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Collection point needs a name.");
        }
        this.locationId = locationId;
        this.name = name;
        this.zone = zone == null ? "General" : zone;
        validateLevel(wasteKg);
        this.wasteKg = wasteKg;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public String getZone() {
        return zone;
    }

    public double getWasteKg() {
        return wasteKg;
    }

    public void setWasteKg(double kg) {
        validateLevel(kg);
        this.wasteKg = kg;
    }

    private static void validateLevel(double kg) {
        if (Double.isNaN(kg) || kg < 0 || kg > MAX_WASTE_KG) {
            throw new IllegalArgumentException(
                    "Waste level must be between 0 and " + (int) MAX_WASTE_KG + " kg.");
        }
    }

    /** Removes collected waste. The amount must be positive and not exceed the waste present. */
    public void removeWaste(double kg) {
        if (kg <= 0 || kg > wasteKg + 1e-9) {
            throw new IllegalArgumentException(
                    String.format("Cannot remove %.1f kg from %s (holds %.1f kg).", kg, name, wasteKg));
        }
        wasteKg = Math.max(0.0, wasteKg - kg);
    }

    @Override
    public String toString() {
        return String.format("%-4s %-22s zone=%-10s waste=%7.1f kg", locationId, name, zone, wasteKg);
    }
}
