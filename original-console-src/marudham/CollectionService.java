package marudham;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Application service that coordinates the whole system (composition: it owns the
 * road network, planner, collection points, vehicles, request queue and history).
 *
 * Scheduling policy (greedy, NOT globally optimal):
 *   1. Requests leave a PriorityQueue by priority (HIGH, MEDIUM, LOW), then arrival order.
 *   2. The head request is given to the available vehicle with the lowest estimated trip
 *      cost (distance x cost-per-km), including a depot-unloading detour when the vehicle
 *      does not have enough free capacity. Dijkstra supplies the distances.
 */
public class CollectionService {

    public enum Kind { ASSIGNED, WAITING, DEFERRED, EMPTY }

    /** Outcome of one attempt to assign the head request. */
    public static class StepResult {
        private final Kind kind;
        private final String message;

        StepResult(Kind kind, String message) {
            this.kind = kind;
            this.message = message;
        }

        public Kind getKind() { return kind; }
        public String getMessage() { return message; }
    }

    private static final int START_MINUTE = 8 * 60;   // simulation starts Day 1, 08:00
    private static final int COLLECTION_MINUTES = 5;
    private static final int UNLOAD_MINUTES = 10;

    private final RoadNetwork network;
    private final RoutePlanner planner;
    private final String depotId;

    private final Map<String, CollectionPoint> points = new HashMap<>();
    private final List<CollectionVehicle> vehicles = new ArrayList<>();
    private final PriorityQueue<CollectionRequest> pending = new PriorityQueue<>();
    private final List<CollectionRequest> allRequests = new ArrayList<>();
    private final List<CollectionRequest> deferred = new ArrayList<>();
    private final Map<String, Assignment> active = new HashMap<>();
    private final List<CollectionRecord> history = new ArrayList<>();

    private long arrivalCounter = 0;
    private int requestCounter = 0;
    private int recordCounter = 0;
    private int clock = START_MINUTE;
    private double depotTotalKg = 0.0;
    private int rerouteCount = 0;
    private int breakdownCount = 0;

    public CollectionService(RoadNetwork network, RoutePlanner planner, String depotId) {
        if (!network.hasLocation(depotId)) {
            throw new IllegalArgumentException("Depot location does not exist: " + depotId);
        }
        this.network = network;
        this.planner = planner;
        this.depotId = depotId;
    }

    // ------------------------------------------------------------------ setup

    public void addPoint(CollectionPoint point) {
        if (!network.hasLocation(point.getLocationId())) {
            throw new IllegalArgumentException("Unknown location: " + point.getLocationId());
        }
        if (point.getLocationId().equals(depotId)) {
            throw new IllegalArgumentException("The depot cannot be a collection point.");
        }
        if (points.containsKey(point.getLocationId())) {
            throw new IllegalArgumentException("Collection point already registered: " + point.getLocationId());
        }
        points.put(point.getLocationId(), point);
    }

    public void addVehicle(CollectionVehicle vehicle) {
        if (findVehicle(vehicle.getVehicleId()) != null) {
            throw new IllegalArgumentException("Vehicle id already used: " + vehicle.getVehicleId());
        }
        if (!network.hasLocation(vehicle.getLocation())) {
            throw new IllegalArgumentException("Unknown vehicle location: " + vehicle.getLocation());
        }
        vehicles.add(vehicle);
    }

    // --------------------------------------------------------------- accessors

    public RoadNetwork getNetwork() { return network; }
    public RoutePlanner getPlanner() { return planner; }
    public String getDepotId() { return depotId; }
    public double getDepotTotalKg() { return depotTotalKg; }
    public int getRerouteCount() { return rerouteCount; }
    public int getBreakdownCount() { return breakdownCount; }
    public int getClockMinutes() { return clock; }

    public List<CollectionVehicle> getVehicles() {
        return Collections.unmodifiableList(vehicles);
    }

    public List<CollectionPoint> getPoints() {
        List<CollectionPoint> list = new ArrayList<>(points.values());
        list.sort(Comparator.comparing(CollectionPoint::getLocationId));
        return list;
    }

    public boolean hasPoint(String id) { return points.containsKey(id); }
    public CollectionPoint getPoint(String id) { return points.get(id); }

    public CollectionVehicle findVehicle(String id) {
        for (CollectionVehicle v : vehicles) {
            if (v.getVehicleId().equalsIgnoreCase(id)) {
                return v;
            }
        }
        return null;
    }

    public List<CollectionRecord> getHistory() { return Collections.unmodifiableList(history); }
    public List<CollectionRequest> getAllRequests() { return Collections.unmodifiableList(allRequests); }
    public List<CollectionRequest> getDeferred() { return Collections.unmodifiableList(deferred); }
    public Assignment getActiveAssignment(String vehicleId) { return active.get(vehicleId); }
    public int getActiveCount() { return active.size(); }
    public int getPendingCount() { return pending.size(); }

    /** Pending requests in the exact order they will be served (queue itself is untouched). */
    public List<CollectionRequest> getPendingSnapshot() {
        PriorityQueue<CollectionRequest> copy = new PriorityQueue<>(pending);
        List<CollectionRequest> ordered = new ArrayList<>();
        while (!copy.isEmpty()) {
            ordered.add(copy.poll());
        }
        return ordered;
    }

    // ---------------------------------------------------------------- requests

    public CollectionRequest submitRequest(String pointId, Priority priority) {
        if (pointId == null || !points.containsKey(pointId)) {
            throw new IllegalArgumentException("Unknown collection point: " + pointId);
        }
        if (priority == null) {
            throw new IllegalArgumentException("Priority must be High, Medium or Low.");
        }
        CollectionPoint point = points.get(pointId);
        if (point.getWasteKg() <= 0) {
            throw new IllegalArgumentException(point.getName() + " has no waste to collect.");
        }
        if (hasOpenRequest(pointId)) {
            throw new IllegalStateException("Collection point " + pointId + " already has an open request.");
        }
        double biggest = 0;
        for (CollectionVehicle v : vehicles) {
            biggest = Math.max(biggest, v.getCapacityKg());
        }
        if (point.getWasteKg() > biggest) {
            throw new IllegalArgumentException(String.format(
                    "%s holds %.0f kg, more than the largest vehicle capacity (%.0f kg).",
                    point.getName(), point.getWasteKg(), biggest));
        }
        CollectionRequest request = new CollectionRequest(++requestCounter, pointId, priority,
                ++arrivalCounter, point.getWasteKg());
        pending.add(request);
        allRequests.add(request);
        return request;
    }

    public boolean hasOpenRequest(String pointId) {
        for (CollectionRequest r : allRequests) {
            if (r.getPointId().equals(pointId) && r.getStatus() != RequestStatus.COMPLETED) {
                return true;
            }
        }
        return false;
    }

    public void setWasteLevel(String pointId, double kg) {
        if (!points.containsKey(pointId)) {
            throw new IllegalArgumentException("Unknown collection point: " + pointId);
        }
        if (hasOpenRequest(pointId)) {
            throw new IllegalStateException("Cannot change waste level while a request is open for " + pointId + ".");
        }
        points.get(pointId).setWasteKg(kg);
    }

    /** Moves every deferred request back into the priority queue (original arrival order kept). */
    public int retryDeferred() {
        int n = deferred.size();
        for (CollectionRequest r : deferred) {
            r.markPendingAgain();
            pending.add(r);
        }
        deferred.clear();
        return n;
    }

    // -------------------------------------------------------------- assignment

    private static class Candidate {
        final CollectionVehicle vehicle;
        final boolean unloadFirst;
        final PathResult toDepot;
        final PathResult toPoint;
        final double km;
        final double cost;

        Candidate(CollectionVehicle vehicle, boolean unloadFirst, PathResult toDepot,
                  PathResult toPoint, double km) {
            this.vehicle = vehicle;
            this.unloadFirst = unloadFirst;
            this.toDepot = toDepot;
            this.toPoint = toPoint;
            this.km = km;
            this.cost = km * vehicle.getCostPerKm();
        }

        boolean betterThan(Candidate o) {
            if (Math.abs(cost - o.cost) > 1e-9) {
                return cost < o.cost;
            }
            if (Math.abs(km - o.km) > 1e-9) {
                return km < o.km;
            }
            return vehicle.getVehicleId().compareTo(o.vehicle.getVehicleId()) < 0;
        }
    }

    /** Tries to assign the head of the priority queue to the cheapest suitable available vehicle. */
    public StepResult assignNext() {
        CollectionRequest head = pending.peek();
        if (head == null) {
            return new StepResult(Kind.EMPTY, "No pending requests.");
        }
        CollectionPoint point = points.get(head.getPointId());
        double need = point.getWasteKg();

        Candidate best = null;
        boolean busyCapable = false;
        boolean operationalCapable = false;

        for (CollectionVehicle v : vehicles) {
            if (v.getStatus() == CollectionVehicle.Status.BROKEN_DOWN || v.getCapacityKg() < need) {
                continue;
            }
            operationalCapable = true;
            if (v.getStatus() == CollectionVehicle.Status.EN_ROUTE) {
                busyCapable = true;
                continue;
            }
            boolean unloadFirst = !v.canCarry(need);
            Candidate c;
            if (unloadFirst) {
                PathResult toDepot = planner.shortestPath(network, v.getLocation(), depotId);
                PathResult fromDepot = planner.shortestPath(network, depotId, point.getLocationId());
                if (!toDepot.isReachable() || !fromDepot.isReachable()) {
                    continue;
                }
                c = new Candidate(v, true, toDepot, fromDepot, toDepot.getDistanceKm() + fromDepot.getDistanceKm());
            } else {
                PathResult direct = planner.shortestPath(network, v.getLocation(), point.getLocationId());
                if (!direct.isReachable()) {
                    continue;
                }
                c = new Candidate(v, false, null, direct, direct.getDistanceKm());
            }
            if (best == null || c.betterThan(best)) {
                best = c;
            }
        }

        if (best == null) {
            if (busyCapable) {
                return new StepResult(Kind.WAITING, String.format(
                        "%s (%s, %s) is waiting: every suitable vehicle is busy.",
                        head.getLabel(), head.getPointId(), head.getPriority()));
            }
            String reason = operationalCapable
                    ? "no open road route to the point (blocked roads)"
                    : String.format("no operational vehicle can carry %.0f kg", need);
            pending.poll();
            head.markDeferred(reason);
            deferred.add(head);
            log(CollectionRecord.Type.DEFERRED, null, head.getId(), head.getPointId(), 0, 0, "Deferred: " + reason);
            return new StepResult(Kind.DEFERRED, String.format("%s (%s) deferred: %s.",
                    head.getLabel(), head.getPointId(), reason));
        }

        pending.poll();
        CollectionVehicle v = best.vehicle;
        head.markAssigned(v.getVehicleId());
        v.setStatus(CollectionVehicle.Status.EN_ROUTE);

        Assignment a = best.unloadFirst
                ? new Assignment(v, head, Assignment.Stage.TO_DEPOT_UNLOAD, best.toDepot)
                : new Assignment(v, head, Assignment.Stage.TO_POINT, best.toPoint);
        active.put(v.getVehicleId(), a);

        String plan = best.unloadFirst
                ? String.format("depot first (%s), then %s (%s)", best.toDepot, point.getLocationId(), best.toPoint)
                : best.toPoint.toString();
        log(CollectionRecord.Type.ASSIGNMENT, v.getVehicleId(), head.getId(), point.getLocationId(), need, best.km,
                String.format("%s priority; est. cost %.1f; route %s", head.getPriority(), best.cost, plan));

        settle(a);
        return new StepResult(Kind.ASSIGNED, String.format(
                "Assigned %s (%s %s, %.0f kg) to %s [%s]. Plan: %s. Estimated trip %.1f km, cost %.1f.",
                head.getLabel(), head.getPointId(), head.getPriority(), need, v.getVehicleId(), v.getTypeName(),
                plan, best.km, best.cost));
    }

    // ------------------------------------------------------------ vehicle moves

    /** Moves a vehicle along its planned route by up to 'hops' road segments. */
    public String advance(String vehicleId, int hops) {
        if (hops < 1) {
            throw new IllegalArgumentException("Hops must be at least 1.");
        }
        CollectionVehicle v = requireVehicle(vehicleId);
        Assignment a = active.get(v.getVehicleId());
        if (a == null) {
            throw new IllegalStateException(v.getVehicleId() + " is not serving a request.");
        }
        if (a.getStage() == Assignment.Stage.ARRIVED) {
            return v.getVehicleId() + " has already arrived at " + a.getRequest().getPointId()
                    + "; confirm the collection.";
        }
        int moved = 0;
        while (moved < hops && active.get(v.getVehicleId()) == a && a.getStage() != Assignment.Stage.ARRIVED) {
            if (!moveOneHop(a)) {
                break;
            }
            moved++;
            settle(a);   // reached the end of a leg? unload at depot / mark arrival
        }
        if (active.get(v.getVehicleId()) != a) {
            return v.getVehicleId() + " lost its route; request " + a.getRequest().getLabel()
                    + " went back to the queue.";
        }
        String state = a.getStage() == Assignment.Stage.ARRIVED
                ? "arrived at " + a.getRequest().getPointId() + " - awaiting collection confirmation"
                : "remaining route " + a.getPath();
        return String.format("%s moved %d hop(s) to %s; %s.", v.getVehicleId(), moved, v.getLocation(), state);
    }

    /** Drives the vehicle until it arrives at the collection point (unloading at the depot on the way if planned). */
    public String advanceToArrival(String vehicleId) {
        return advance(vehicleId, Integer.MAX_VALUE);
    }

    /** Confirms the collection at the point. actualKg == null means "take everything that is there". */
    public String confirmCollection(String vehicleId, Double actualKg) {
        CollectionVehicle v = requireVehicle(vehicleId);
        Assignment a = active.get(v.getVehicleId());
        if (a == null) {
            throw new IllegalStateException(v.getVehicleId() + " is not serving a request.");
        }
        if (a.getStage() != Assignment.Stage.ARRIVED) {
            throw new IllegalStateException(v.getVehicleId() + " has not arrived yet (" + a.getRemainingHops()
                    + " hop(s) left).");
        }
        CollectionRequest r = a.getRequest();
        CollectionPoint p = points.get(r.getPointId());
        double max = Math.min(p.getWasteKg(), v.getRemainingCapacityKg());
        double kg = actualKg == null ? max : actualKg;
        if (kg <= 0 || kg > max + 1e-9) {
            throw new IllegalArgumentException(String.format(
                    "Collected amount must be in (0, %.1f] kg (waste present %.1f, free capacity %.1f).",
                    max, p.getWasteKg(), v.getRemainingCapacityKg()));
        }
        p.removeWaste(kg);
        v.load(kg);
        r.markCompleted(v.getVehicleId(), kg);
        v.setStatus(CollectionVehicle.Status.AVAILABLE);
        active.remove(v.getVehicleId());
        clock += COLLECTION_MINUTES;
        double left = p.getWasteKg();
        log(CollectionRecord.Type.COLLECTION, v.getVehicleId(), r.getId(), p.getLocationId(), kg, 0,
                String.format("Confirmed %.1f kg at %s; %.1f kg left at point; vehicle load %.1f/%.0f kg",
                        kg, p.getName(), left, v.getLoadKg(), v.getCapacityKg()));
        return String.format("%s collected %.1f kg at %s (%s). Point now holds %.1f kg. Vehicle load %.1f/%.0f kg.",
                v.getVehicleId(), kg, p.getLocationId(), p.getName(), left, v.getLoadKg(), v.getCapacityKg());
    }

    /** Sends an idle vehicle to the depot and unloads it there. */
    public String returnToDepot(String vehicleId) {
        CollectionVehicle v = requireVehicle(vehicleId);
        if (v.getStatus() == CollectionVehicle.Status.BROKEN_DOWN) {
            throw new IllegalStateException(v.getVehicleId() + " is broken down. Repair it first.");
        }
        if (v.getStatus() == CollectionVehicle.Status.EN_ROUTE) {
            throw new IllegalStateException(v.getVehicleId() + " is busy with a request.");
        }
        PathResult path = planner.shortestPath(network, v.getLocation(), depotId);
        if (!path.isReachable()) {
            throw new IllegalStateException("No open road route from " + v.getLocation() + " to the depot.");
        }
        double km = path.getDistanceKm();
        travelAlong(v, path);
        double kg = unloadAtDepot(v);
        return String.format("%s returned to depot via %s (%.1f km) and unloaded %.1f kg.",
                v.getVehicleId(), path.routeString(), km, kg);
    }

    /** End of shift: every idle vehicle drives to the depot and unloads. */
    public List<String> endShift() {
        List<String> out = new ArrayList<>();
        List<CollectionVehicle> sorted = new ArrayList<>(vehicles);
        sorted.sort(Comparator.comparing(CollectionVehicle::getVehicleId));
        for (CollectionVehicle v : sorted) {
            if (v.getStatus() == CollectionVehicle.Status.BROKEN_DOWN) {
                out.add(v.getVehicleId() + ": broken down, left for repair.");
            } else if (v.getStatus() == CollectionVehicle.Status.EN_ROUTE) {
                out.add(v.getVehicleId() + ": still serving a request, not recalled.");
            } else if (!v.getLocation().equals(depotId) || v.getLoadKg() > 0) {
                try {
                    out.add(returnToDepot(v.getVehicleId()));
                } catch (IllegalStateException e) {
                    out.add(v.getVehicleId() + ": " + e.getMessage());
                }
            } else {
                out.add(v.getVehicleId() + ": already at depot and empty.");
            }
        }
        return out;
    }

    // ------------------------------------------------ roads, breakdown, repair

    /** Blocks a road and immediately re-plans every active route that used it. */
    public List<String> blockRoad(String a, String b) {
        network.blockRoad(a, b);
        List<String> out = new ArrayList<>();
        log(CollectionRecord.Type.ROAD_BLOCKED, null, 0, a + "-" + b, 0, 0, "Road " + a + "-" + b + " blocked");
        out.add("Road " + a + "-" + b + " is now blocked.");
        List<Assignment> affected = new ArrayList<>(active.values());
        affected.sort(Comparator.comparing(x -> x.getVehicle().getVehicleId()));
        for (Assignment asg : affected) {
            if (active.get(asg.getVehicle().getVehicleId()) == asg
                    && asg.getStage() != Assignment.Stage.ARRIVED
                    && asg.getPath().usesRoad(a, b)) {
                String before = asg.getPath().toString();
                boolean ok = reroute(asg, "road " + a + "-" + b + " blocked");
                if (ok) {
                    out.add(String.format("Re-planned %s: %s  =>  %s", asg.getVehicle().getVehicleId(),
                            before, asg.getPath()));
                } else {
                    out.add(String.format("%s has no route left; %s returned to the queue.",
                            asg.getVehicle().getVehicleId(), asg.getRequest().getLabel()));
                }
            }
        }
        return out;
    }

    public String unblockRoad(String a, String b) {
        network.unblockRoad(a, b);
        log(CollectionRecord.Type.ROAD_REOPENED, null, 0, a + "-" + b, 0, 0, "Road " + a + "-" + b + " reopened");
        return "Road " + a + "-" + b + " reopened. Routes already under way keep their current plan; "
                + "new assignments will use it.";
    }

    /**
     * Marks a vehicle as broken down. Its onboard cargo is moved to the depot by the recovery crew,
     * its current request goes back to the priority queue (original arrival order kept) and the
     * system immediately tries to assign the head of the queue.
     */
    public List<String> reportBreakdown(String vehicleId) {
        CollectionVehicle v = requireVehicle(vehicleId);
        if (v.getStatus() == CollectionVehicle.Status.BROKEN_DOWN) {
            throw new IllegalStateException(v.getVehicleId() + " is already broken down.");
        }
        List<String> out = new ArrayList<>();
        Assignment a = active.remove(v.getVehicleId());
        v.setStatus(CollectionVehicle.Status.BROKEN_DOWN);
        breakdownCount++;
        String where = v.getLocation();
        log(CollectionRecord.Type.BREAKDOWN, v.getVehicleId(), a == null ? 0 : a.getRequest().getId(), where, 0, 0,
                "Vehicle broke down" + (a == null ? " while idle" : " while serving " + a.getRequest().getLabel()));
        out.add(String.format("%s broke down at %s.", v.getVehicleId(), where));

        if (v.getLoadKg() > 0) {
            double kg = v.unloadAll();
            depotTotalKg += kg;
            log(CollectionRecord.Type.UNLOAD, v.getVehicleId(), 0, depotId, kg, 0,
                    "Recovery crew moved onboard cargo to the depot");
            out.add(String.format("Recovery crew moved %.1f kg of onboard cargo to the depot.", kg));
        }
        if (a != null) {
            CollectionRequest r = a.getRequest();
            r.returnToPending();
            pending.add(r);
            log(CollectionRecord.Type.REASSIGNMENT, v.getVehicleId(), r.getId(), r.getPointId(), 0, 0,
                    "Request returned to queue for reassignment");
            out.add(String.format("%s returned to the queue with its original priority (%s) and arrival order.",
                    r.getLabel(), r.getPriority()));
            out.add(assignNext().getMessage());
        }
        return out;
    }

    /** Repairs a vehicle: it is towed to the depot and becomes available again. */
    public String repairVehicle(String vehicleId) {
        CollectionVehicle v = requireVehicle(vehicleId);
        if (v.getStatus() != CollectionVehicle.Status.BROKEN_DOWN) {
            throw new IllegalStateException(v.getVehicleId() + " is not broken down.");
        }
        v.relocate(depotId);
        v.setStatus(CollectionVehicle.Status.AVAILABLE);
        log(CollectionRecord.Type.REPAIR, v.getVehicleId(), 0, depotId, 0, 0, "Repaired and returned to depot");
        return v.getVehicleId() + " repaired and back at the depot, available again.";
    }

    // --------------------------------------------------------- automatic mode

    /** Assigns, drives and confirms requests until nothing more can be done. */
    public List<String> processAll() {
        List<String> out = new ArrayList<>();
        int guard = 0;
        while ((!pending.isEmpty() || !active.isEmpty()) && guard++ < 1000) {
            boolean progressed = false;
            while (!pending.isEmpty()) {
                StepResult r = assignNext();
                if (r.getKind() == Kind.ASSIGNED || r.getKind() == Kind.DEFERRED) {
                    out.add(r.getMessage());
                    progressed = true;
                } else {
                    break;
                }
            }
            List<String> ids = new ArrayList<>(active.keySet());
            Collections.sort(ids);
            for (String id : ids) {
                Assignment a = active.get(id);
                if (a == null) {
                    continue;
                }
                advanceToArrival(id);
                Assignment now = active.get(id);
                if (now != null && now.getStage() == Assignment.Stage.ARRIVED) {
                    out.add(confirmCollection(id, null));
                    progressed = true;
                } else {
                    out.add(id + " lost its route; request re-queued.");
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return out;
    }

    // --------------------------------------------------------------- analysis

    /**
     * Compares visiting the pending points in strict priority order with a greedy
     * nearest-neighbour order, measured from the vehicle's current location.
     * Capacity and unloading are ignored; this is an illustration only.
     */
    public String compareOrderings(String vehicleId) {
        CollectionVehicle v = requireVehicle(vehicleId);
        List<String> stops = new ArrayList<>();
        for (CollectionRequest r : getPendingSnapshot()) {
            stops.add(r.getPointId());
        }
        if (stops.isEmpty()) {
            return "No pending requests to compare.";
        }
        List<String> reachable = planner.reachableStops(network, v.getLocation(), stops);
        double priorityKm = planner.tourDistance(network, v.getLocation(), reachable);
        List<String> nn = planner.nearestNeighbourOrder(network, v.getLocation(), reachable);
        double nnKm = planner.tourDistance(network, v.getLocation(), nn);
        StringBuilder sb = new StringBuilder();
        sb.append("Start: ").append(v.getLocation()).append(", stops: ").append(reachable.size()).append('\n');
        sb.append(String.format("  Priority order        : %s  = %.1f km%n", String.join(" > ", reachable), priorityKm));
        sb.append(String.format("  Nearest-neighbour     : %s  = %.1f km%n", String.join(" > ", nn), nnKm));
        sb.append("Nearest-neighbour ignores priority and is only a heuristic; neither order is claimed optimal.");
        return sb.toString();
    }

    // ---------------------------------------------------------------- reports

    public String pointsReport() {
        StringBuilder sb = new StringBuilder("Collection points:\n");
        for (CollectionPoint p : getPoints()) {
            sb.append("  ").append(p).append(hasOpenRequest(p.getLocationId()) ? "  [open request]" : "").append('\n');
        }
        return sb.toString();
    }

    public String vehiclesReport() {
        StringBuilder sb = new StringBuilder("Vehicles:\n");
        for (CollectionVehicle v : vehicles) {
            sb.append("  ").append(v).append('\n');
            Assignment a = active.get(v.getVehicleId());
            if (a != null) {
                sb.append("        serving ").append(a).append('\n');
            }
        }
        return sb.toString();
    }

    public String pendingReport() {
        List<CollectionRequest> list = getPendingSnapshot();
        StringBuilder sb = new StringBuilder("Pending requests (service order):\n");
        if (list.isEmpty()) {
            sb.append("  (none)\n");
        }
        int pos = 1;
        for (CollectionRequest r : list) {
            sb.append(String.format("  %2d. %s%n", pos++, r));
        }
        return sb.toString();
    }

    public String deferredReport() {
        StringBuilder sb = new StringBuilder("Deferred requests:\n");
        if (deferred.isEmpty()) {
            sb.append("  (none)\n");
        }
        for (CollectionRequest r : deferred) {
            sb.append("  ").append(r).append("  reason: ").append(r.getDeferReason()).append('\n');
        }
        return sb.toString();
    }

    public String historyReport() {
        StringBuilder sb = new StringBuilder("Collection history (" + history.size() + " records):\n");
        if (history.isEmpty()) {
            sb.append("  (empty)\n");
        }
        for (CollectionRecord r : history) {
            sb.append("  ").append(r).append('\n');
        }
        return sb.toString();
    }

    public String summaryReport() {
        EnumMap<RequestStatus, Integer> byStatus = new EnumMap<>(RequestStatus.class);
        for (RequestStatus s : RequestStatus.values()) {
            byStatus.put(s, 0);
        }
        EnumMap<Priority, int[]> byPriority = new EnumMap<>(Priority.class);
        for (Priority p : Priority.values()) {
            byPriority.put(p, new int[2]);
        }
        int reassignments = 0;
        double collected = 0;
        for (CollectionRequest r : allRequests) {
            byStatus.put(r.getStatus(), byStatus.get(r.getStatus()) + 1);
            byPriority.get(r.getPriority())[1]++;
            if (r.getStatus() == RequestStatus.COMPLETED) {
                byPriority.get(r.getPriority())[0]++;
                collected += r.getCollectedKg();
            }
            reassignments += r.getReassignCount();
        }
        double onBoard = 0, km = 0, cost = 0;
        for (CollectionVehicle v : vehicles) {
            onBoard += v.getLoadKg();
            km += v.getOdometerKm();
            cost += v.getFuelCost();
        }
        double remaining = 0;
        for (CollectionPoint p : points.values()) {
            remaining += p.getWasteKg();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("================ SUMMARY REPORT ================\n");
        sb.append(String.format("Simulated time        : %s%n", CollectionRecord.formatClock(clock)));
        sb.append(String.format("Requests              : %d total | %d completed | %d pending | %d assigned | %d deferred%n",
                allRequests.size(), byStatus.get(RequestStatus.COMPLETED), byStatus.get(RequestStatus.PENDING),
                byStatus.get(RequestStatus.ASSIGNED), byStatus.get(RequestStatus.DEFERRED)));
        sb.append("Completed by priority : ");
        for (Priority p : Priority.values()) {
            sb.append(String.format("%s %d/%d  ", p, byPriority.get(p)[0], byPriority.get(p)[1]));
        }
        sb.append('\n');
        sb.append(String.format("Waste collected       : %.1f kg (confirmed)%n", collected));
        sb.append(String.format("Delivered to depot    : %.1f kg | still on vehicles: %.1f kg%n", depotTotalKg, onBoard));
        sb.append(String.format("Waste left at points  : %.1f kg%n", remaining));
        sb.append(String.format("Fleet distance / fuel : %.1f km / %.1f cost units%n", km, cost));
        sb.append(String.format("Reroutes: %d | Breakdowns: %d | Request reassignments: %d%n",
                rerouteCount, breakdownCount, reassignments));
        sb.append("Per vehicle:\n");
        for (CollectionVehicle v : vehicles) {
            sb.append(String.format("  %-5s %-11s collections=%2d unloads=%2d collected=%7.1f kg dist=%6.1f km cost=%7.1f %s%n",
                    v.getVehicleId(), v.getTypeName(), v.getCollections(), v.getUnloads(),
                    v.getTotalCollectedKg(), v.getOdometerKm(), v.getFuelCost(), v.getStatus()));
        }
        sb.append("================================================\n");
        return sb.toString();
    }

    // ---------------------------------------------------------------- private

    private CollectionVehicle requireVehicle(String id) {
        CollectionVehicle v = id == null ? null : findVehicle(id);
        if (v == null) {
            throw new IllegalArgumentException("Unknown vehicle: " + id);
        }
        return v;
    }

    private void log(CollectionRecord.Type type, String vehicleId, int requestId, String loc,
                     double kg, double km, String details) {
        history.add(new CollectionRecord(++recordCounter, clock, type, vehicleId, requestId, loc, kg, km, details));
    }

    /** Resolves zero-length legs: unload on reaching the depot, or mark arrival at the point. */
    private void settle(Assignment a) {
        CollectionVehicle v = a.getVehicle();
        while (a.getRemainingHops() == 0) {
            if (a.getStage() == Assignment.Stage.TO_DEPOT_UNLOAD) {
                unloadAtDepot(v);
                PathResult next = planner.shortestPath(network, v.getLocation(), a.getRequest().getPointId());
                if (!next.isReachable()) {
                    strand(a, "no route from depot to " + a.getRequest().getPointId());
                    return;
                }
                a.setStage(Assignment.Stage.TO_POINT);
                a.setPath(next);
            } else {
                a.setStage(Assignment.Stage.ARRIVED);
                return;
            }
        }
    }

    private boolean moveOneHop(Assignment a) {
        CollectionVehicle v = a.getVehicle();
        List<String> nodes = a.getPath().getNodes();
        String from = nodes.get(0);
        String to = nodes.get(1);
        RoadNetwork.Road road = network.getRoad(from, to);
        if (road.isBlocked()) {
            if (!reroute(a, "next road " + from + "-" + to + " is blocked")) {
                return false;
            }
            nodes = a.getPath().getNodes();
            if (nodes.size() < 2) {
                return true;
            }
            from = nodes.get(0);
            to = nodes.get(1);
            road = network.getRoad(from, to);
        }
        double km = road.getDistanceKm();
        v.moveTo(to, km);
        clock += (int) Math.round(v.travelMinutes(km));
        a.setPath(a.getPath().afterFirstHop(km));
        return true;
    }

    /** Dynamic re-planning from the vehicle's CURRENT location using only open roads. */
    private boolean reroute(Assignment a, String reason) {
        CollectionVehicle v = a.getVehicle();
        String target = a.getStage() == Assignment.Stage.TO_DEPOT_UNLOAD ? depotId : a.getRequest().getPointId();
        PathResult fresh = planner.shortestPath(network, v.getLocation(), target);
        if (!fresh.isReachable()) {
            strand(a, "no open route to " + target + " (" + reason + ")");
            return false;
        }
        double oldKm = a.getPath().getDistanceKm();
        a.setPath(fresh);
        a.countReroute();
        rerouteCount++;
        log(CollectionRecord.Type.REROUTE, v.getVehicleId(), a.getRequest().getId(), v.getLocation(), 0,
                fresh.getDistanceKm(),
                String.format("%s; remaining %.1f km -> %.1f km via %s", reason, oldKm, fresh.getDistanceKm(),
                        fresh.routeString()));
        return true;
    }

    /** The vehicle cannot continue: it stays where it is and the request goes back to the queue. */
    private void strand(Assignment a, String reason) {
        CollectionVehicle v = a.getVehicle();
        CollectionRequest r = a.getRequest();
        active.remove(v.getVehicleId());
        v.setStatus(CollectionVehicle.Status.AVAILABLE);
        r.returnToPending();
        pending.add(r);
        log(CollectionRecord.Type.REASSIGNMENT, v.getVehicleId(), r.getId(), v.getLocation(), 0, 0,
                "Route lost: " + reason + "; request returned to queue");
    }

    private void travelAlong(CollectionVehicle v, PathResult path) {
        List<String> nodes = path.getNodes();
        for (int i = 0; i + 1 < nodes.size(); i++) {
            double km = network.getRoad(nodes.get(i), nodes.get(i + 1)).getDistanceKm();
            v.moveTo(nodes.get(i + 1), km);
            clock += (int) Math.round(v.travelMinutes(km));
        }
    }

    private double unloadAtDepot(CollectionVehicle v) {
        double kg = v.unloadAll();
        if (kg > 0) {
            depotTotalKg += kg;
            clock += UNLOAD_MINUTES;
            log(CollectionRecord.Type.UNLOAD, v.getVehicleId(), 0, depotId, kg, 0,
                    String.format("Unloaded %.1f kg at depot", kg));
        }
        return kg;
    }
}
