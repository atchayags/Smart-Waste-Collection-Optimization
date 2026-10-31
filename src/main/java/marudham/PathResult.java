package marudham;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable result of a shortest-path query: node sequence and total distance. */
public class PathResult {
    private final List<String> nodes;
    private final double distanceKm;
    private final boolean reachable;

    public PathResult(List<String> nodes, double distanceKm) {
        this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
        this.distanceKm = distanceKm;
        this.reachable = true;
    }

    private PathResult() {
        this.nodes = Collections.emptyList();
        this.distanceKm = Double.POSITIVE_INFINITY;
        this.reachable = false;
    }

    public static PathResult unreachable() {
        return new PathResult();
    }

    public boolean isReachable() {
        return reachable;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    /** Number of road segments (hops) in the path. */
    public int getHops() {
        return nodes.isEmpty() ? 0 : nodes.size() - 1;
    }

    /** The path after travelling the first hop (whose length is hopKm). */
    public PathResult afterFirstHop(double hopKm) {
        if (getHops() == 0) {
            return this;
        }
        double remaining = Math.max(0.0, distanceKm - hopKm);
        return new PathResult(nodes.subList(1, nodes.size()), remaining);
    }

    /** True if the path contains the road a-b in either direction. */
    public boolean usesRoad(String a, String b) {
        for (int i = 0; i + 1 < nodes.size(); i++) {
            String x = nodes.get(i);
            String y = nodes.get(i + 1);
            if ((x.equals(a) && y.equals(b)) || (x.equals(b) && y.equals(a))) {
                return true;
            }
        }
        return false;
    }

    public String routeString() {
        if (!reachable) {
            return "(no route)";
        }
        return String.join(" -> ", nodes);
    }

    @Override
    public String toString() {
        if (!reachable) {
            return "(no route)";
        }
        return String.format("%s (%.1f km)", routeString(), distanceKm);
    }
}
