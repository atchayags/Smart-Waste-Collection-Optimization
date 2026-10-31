package marudham;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Route computation.
 *
 * shortestPath(): Dijkstra's algorithm with a custom MinHeap. It is exact for
 * ONE origin-destination pair on the current set of open roads (all edge weights
 * are positive), and it ignores blocked roads.
 *
 * nearestNeighbourOrder(): a greedy heuristic for visiting several stops. It
 * repeatedly picks the closest unvisited stop. It is NOT guaranteed to give the
 * shortest overall tour (that would be a travelling-salesman problem).
 */
public class RoutePlanner {

    public PathResult shortestPath(RoadNetwork network, String source, String target) {
        if (!network.hasLocation(source)) {
            throw new IllegalArgumentException("Unknown location: " + source);
        }
        if (!network.hasLocation(target)) {
            throw new IllegalArgumentException("Unknown location: " + target);
        }

        Map<String, Double> dist = new HashMap<>();
        Map<String, String> previous = new HashMap<>();
        MinHeap heap = new MinHeap();

        dist.put(source, 0.0);
        heap.insert(source, 0.0);

        while (!heap.isEmpty()) {
            MinHeap.Entry current = heap.extractMin();
            if (current.distance > dist.get(current.node)) {
                continue; // stale heap entry (a shorter route was found later)
            }
            if (current.node.equals(target)) {
                break;    // target settled: its distance is final
            }
            for (RoadNetwork.Road road : network.getNeighbours(current.node)) {
                if (road.isBlocked()) {
                    continue;
                }
                double candidate = current.distance + road.getDistanceKm();
                Double known = dist.get(road.getDestination());
                if (known == null || candidate < known) {
                    dist.put(road.getDestination(), candidate);
                    previous.put(road.getDestination(), current.node);
                    heap.insert(road.getDestination(), candidate);
                }
            }
        }

        if (!dist.containsKey(target)) {
            return PathResult.unreachable();
        }
        LinkedList<String> path = new LinkedList<>();
        String step = target;
        path.addFirst(step);
        while (!step.equals(source)) {
            step = previous.get(step);
            path.addFirst(step);
        }
        return new PathResult(path, dist.get(target));
    }

    /** Greedy visiting order starting at 'start'. Unreachable stops are left out. */
    public List<String> nearestNeighbourOrder(RoadNetwork network, String start, List<String> stops) {
        List<String> remaining = new ArrayList<>(stops);
        List<String> order = new ArrayList<>();
        String here = start;
        while (!remaining.isEmpty()) {
            String bestStop = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (String stop : remaining) {
                PathResult p = shortestPath(network, here, stop);
                if (p.isReachable() && p.getDistanceKm() < bestDistance) {
                    bestDistance = p.getDistanceKm();
                    bestStop = stop;
                }
            }
            if (bestStop == null) {
                break;
            }
            order.add(bestStop);
            remaining.remove(bestStop);
            here = bestStop;
        }
        return order;
    }

    /** Total distance of visiting the stops in the given order (Infinity if any leg is cut off). */
    public double tourDistance(RoadNetwork network, String start, List<String> orderedStops) {
        double total = 0.0;
        String here = start;
        for (String stop : orderedStops) {
            PathResult p = shortestPath(network, here, stop);
            if (!p.isReachable()) {
                return Double.POSITIVE_INFINITY;
            }
            total += p.getDistanceKm();
            here = stop;
        }
        return total;
    }

    /** Convenience: drops stops that cannot currently be reached from 'start'. */
    public List<String> reachableStops(RoadNetwork network, String start, List<String> stops) {
        List<String> ok = new ArrayList<>();
        for (String s : stops) {
            if (shortestPath(network, start, s).isReachable()) {
                ok.add(s);
            }
        }
        return Collections.unmodifiableList(ok);
    }
}
