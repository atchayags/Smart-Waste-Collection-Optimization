package marudham;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted, undirected road graph stored as an adjacency list:
 * HashMap&lt;locationId, ArrayList&lt;Road&gt;&gt;. Each physical road is stored as two
 * directed Road objects (one per direction) which are blocked/unblocked together.
 */
public class RoadNetwork {

    /** One directed edge of the adjacency list. */
    public static class Road {
        private final String destination;
        private final double distanceKm;
        private boolean blocked;

        Road(String destination, double distanceKm) {
            this.destination = destination;
            this.distanceKm = distanceKm;
        }

        public String getDestination() { return destination; }
        public double getDistanceKm() { return distanceKm; }
        public boolean isBlocked() { return blocked; }
        void setBlocked(boolean blocked) { this.blocked = blocked; }
    }

    private final Map<String, List<Road>> adjacency = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private int roadCount = 0;

    public void addLocation(String id, String name) {
        if (id == null || id.trim().isEmpty() || name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Location id and name must not be empty.");
        }
        if (adjacency.containsKey(id)) {
            throw new IllegalArgumentException("Location already exists: " + id);
        }
        adjacency.put(id, new ArrayList<>());
        names.put(id, name);
    }

    public void addRoad(String a, String b, double km) {
        requireLocation(a);
        requireLocation(b);
        if (a.equals(b)) {
            throw new IllegalArgumentException("A road cannot connect a location to itself.");
        }
        if (Double.isNaN(km) || km <= 0) {
            throw new IllegalArgumentException("Road length must be positive.");
        }
        if (getRoad(a, b) != null) {
            throw new IllegalArgumentException("Road already exists: " + a + "-" + b);
        }
        adjacency.get(a).add(new Road(b, km));
        adjacency.get(b).add(new Road(a, km));
        roadCount++;
    }

    public void blockRoad(String a, String b) {
        Road forward = getRoad(a, b);
        Road backward = getRoad(b, a);
        if (forward == null || backward == null) {
            throw new IllegalArgumentException("No road exists between " + a + " and " + b + ".");
        }
        if (forward.isBlocked()) {
            throw new IllegalStateException("Road " + a + "-" + b + " is already blocked.");
        }
        forward.setBlocked(true);
        backward.setBlocked(true);
    }

    public void unblockRoad(String a, String b) {
        Road forward = getRoad(a, b);
        Road backward = getRoad(b, a);
        if (forward == null || backward == null) {
            throw new IllegalArgumentException("No road exists between " + a + " and " + b + ".");
        }
        if (!forward.isBlocked()) {
            throw new IllegalStateException("Road " + a + "-" + b + " is not blocked.");
        }
        forward.setBlocked(false);
        backward.setBlocked(false);
    }

    /** Returns the directed road a-&gt;b or null if absent. */
    public Road getRoad(String a, String b) {
        List<Road> list = adjacency.get(a);
        if (list == null) {
            return null;
        }
        for (Road r : list) {
            if (r.getDestination().equals(b)) {
                return r;
            }
        }
        return null;
    }

    public boolean isBlocked(String a, String b) {
        Road r = getRoad(a, b);
        return r != null && r.isBlocked();
    }

    public boolean hasLocation(String id) {
        return adjacency.containsKey(id);
    }

    public String getName(String id) {
        requireLocation(id);
        return names.get(id);
    }

    public List<Road> getNeighbours(String id) {
        requireLocation(id);
        return Collections.unmodifiableList(adjacency.get(id));
    }

    public List<String> getLocationIds() {
        List<String> ids = new ArrayList<>(adjacency.keySet());
        Collections.sort(ids);
        return ids;
    }

    public int getLocationCount() { return adjacency.size(); }
    public int getRoadCount() { return roadCount; }

    /** Blocked roads as strings "A-B" (A &lt; B alphabetically). */
    public List<String> getBlockedRoads() {
        List<String> out = new ArrayList<>();
        for (String a : getLocationIds()) {
            for (Road r : adjacency.get(a)) {
                if (r.isBlocked() && a.compareTo(r.getDestination()) < 0) {
                    out.add(a + "-" + r.getDestination());
                }
            }
        }
        return out;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Road network: %d locations, %d roads, %d blocked%n",
                getLocationCount(), getRoadCount(), getBlockedRoads().size()));
        for (String id : getLocationIds()) {
            sb.append(String.format("  %-4s %-26s ->", id, names.get(id)));
            List<Road> sorted = new ArrayList<>(adjacency.get(id));
            sorted.sort((x, y) -> x.getDestination().compareTo(y.getDestination()));
            for (Road r : sorted) {
                sb.append(String.format(" %s(%.1f%s)", r.getDestination(), r.getDistanceKm(),
                        r.isBlocked() ? " BLOCKED" : ""));
            }
            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }

    private void requireLocation(String id) {
        if (!adjacency.containsKey(id)) {
            throw new IllegalArgumentException("Unknown location: " + id);
        }
    }
}
