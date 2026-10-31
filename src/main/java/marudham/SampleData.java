package marudham;

import java.util.ArrayList;
import java.util.List;

/** Builds the fictional Marudham municipality: 13 locations, 9 collection points, 1 depot, 3 vehicles. */
public final class SampleData {
    public static final String DEPOT = "D0";

    private SampleData() { }

    public static RoadNetwork buildNetwork() {
        RoadNetwork n = new RoadNetwork();
        n.addLocation("D0", "Marudham Central Depot");
        n.addLocation("J1", "Gandhi Circle");
        n.addLocation("J2", "Railway Junction");
        n.addLocation("J3", "Market Cross");
        n.addLocation("CP1", "Bus Stand");
        n.addLocation("CP2", "Municipal Market");
        n.addLocation("CP3", "Government Hospital");
        n.addLocation("CP4", "Textile Park");
        n.addLocation("CP5", "College Road");
        n.addLocation("CP6", "Temple Street");
        n.addLocation("CP7", "Riverside Colony");
        n.addLocation("CP8", "Industrial Estate");
        n.addLocation("CP9", "Lakeview Apartments");

        n.addRoad("D0", "J1", 2.0);
        n.addRoad("D0", "J2", 3.5);
        n.addRoad("D0", "CP8", 5.0);
        n.addRoad("J1", "CP1", 1.5);
        n.addRoad("J1", "J3", 2.5);
        n.addRoad("J1", "CP5", 3.0);
        n.addRoad("J2", "CP4", 2.5);
        n.addRoad("J2", "CP7", 4.0);
        n.addRoad("J2", "J3", 3.0);
        n.addRoad("J3", "CP2", 1.2);
        n.addRoad("J3", "CP3", 2.0);
        n.addRoad("J3", "CP6", 1.8);
        n.addRoad("CP1", "CP2", 2.2);
        n.addRoad("CP2", "CP3", 1.5);
        n.addRoad("CP3", "CP9", 2.8);
        n.addRoad("CP5", "CP6", 2.4);
        n.addRoad("CP4", "CP8", 3.0);
        n.addRoad("CP7", "CP9", 3.5);
        n.addRoad("CP6", "CP9", 3.2);
        n.addRoad("CP8", "CP7", 4.5);
        return n;
    }

    /** Registers the nine collection points with their initial waste levels. */
    public static void addPoints(CollectionService s) {
        s.addPoint(new CollectionPoint("CP1", "Bus Stand", "Central", 180));
        s.addPoint(new CollectionPoint("CP2", "Municipal Market", "Central", 420));
        s.addPoint(new CollectionPoint("CP3", "Government Hospital", "Central", 260));
        s.addPoint(new CollectionPoint("CP4", "Textile Park", "East", 380));
        s.addPoint(new CollectionPoint("CP5", "College Road", "West", 150));
        s.addPoint(new CollectionPoint("CP6", "Temple Street", "West", 210));
        s.addPoint(new CollectionPoint("CP7", "Riverside Colony", "South", 300));
        s.addPoint(new CollectionPoint("CP8", "Industrial Estate", "East", 650));
        s.addPoint(new CollectionPoint("CP9", "Lakeview Apartments", "South", 240));
    }

    /** The default three-vehicle fleet: one small and two large trucks, all starting at the depot. */
    public static void addFleet(CollectionService s) {
        s.addVehicle(new SmallTruck("ST-1", 500, DEPOT));
        s.addVehicle(new LargeTruck("LT-1", 1200, DEPOT));
        s.addVehicle(new LargeTruck("LT-2", 1000, DEPOT));
    }

    public static CollectionService createService() {
        CollectionService s = new CollectionService(buildNetwork(), new RoutePlanner(), DEPOT);
        addPoints(s);
        addFleet(s);
        return s;
    }

    private static final String[][] WAVE1 = {
        {"CP3", "H"}, {"CP2", "M"}, {"CP8", "L"}, {"CP4", "M"}, {"CP7", "L"},
        {"CP1", "H"}, {"CP9", "M"}, {"CP5", "L"}, {"CP6", "H"}
    };

    private static final String[][] WAVE2 = {
        {"CP2", "H"}, {"CP6", "M"}, {"CP1", "M"}, {"CP3", "H"},
        {"CP8", "H"}, {"CP4", "L"}, {"CP9", "L"}, {"CP7", "M"}
    };

    /** Waste levels used to refill the points before wave 2 (simulating the next collection cycle). */
    private static final Object[][] REFILL = {
        {"CP1", 200.0}, {"CP2", 380.0}, {"CP3", 300.0}, {"CP4", 250.0},
        {"CP6", 190.0}, {"CP7", 280.0}, {"CP8", 700.0}, {"CP9", 220.0}
    };

    /** 9 requests, one per point, with a deliberate mix of priorities and several ties. */
    public static List<String> loadWave1(CollectionService s) {
        return submit(s, WAVE1);
    }

    /** Refills waste at 8 points, then submits 8 more requests (17 in total with wave 1). */
    public static List<String> loadWave2(CollectionService s) {
        List<String> notes = new ArrayList<>();
        for (Object[] r : REFILL) {
            try {
                s.setWasteLevel((String) r[0], (Double) r[1]);
            } catch (RuntimeException e) {
                notes.add("Could not refill " + r[0] + ": " + e.getMessage());
            }
        }
        notes.addAll(submit(s, WAVE2));
        return notes;
    }

    private static List<String> submit(CollectionService s, String[][] wave) {
        List<String> notes = new ArrayList<>();
        for (String[] row : wave) {
            try {
                CollectionRequest r = s.submitRequest(row[0], Priority.parse(row[1]));
                notes.add("Accepted " + r);
            } catch (RuntimeException e) {
                notes.add("Rejected " + row[0] + ": " + e.getMessage());
            }
        }
        return notes;
    }
}
