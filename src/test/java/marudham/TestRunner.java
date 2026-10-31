package marudham;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * Dependency-free test runner (no JUnit needed). Each scenario is a method; each check prints PASS/FAIL.
 * Exit code is 0 only if every check passed.
 */
public class TestRunner {
    private static int passed = 0;
    private static int failed = 0;
    private static String current = "";

    public static void main(String[] args) {
        int failedCount = runAllTests();
        System.exit(failedCount == 0 ? 0 : 1);
    }

    public static int runAllTests() {
        passed = 0;
        failed = 0;
        run("T1  Normal operation (wave 1 + wave 2, 17 requests)", TestRunner::testNormalOperation);
        run("T2  Full vehicle -> depot unloading, capacity checks", TestRunner::testFullVehicle);
        run("T3  Blocked road -> dynamic route recalculation", TestRunner::testBlockedRoad);
        run("T4  Equal priorities -> arrival-order tie-break", TestRunner::testEqualPriorities);
        run("T5  Vehicle breakdown -> reassignment", TestRunner::testBreakdown);
        run("T6  Deferred requests (no capable vehicle / unreachable) and retry", TestRunner::testDeferred);
        run("T7  Dijkstra vs Floyd-Warshall on sample + 40 random graphs", TestRunner::testDijkstraAgainstFloydWarshall);
        run("T8  MinHeap ordering", TestRunner::testMinHeap);
        run("T9  Input validation", TestRunner::testValidation);
        run("T10 OOP: abstraction, inheritance, polymorphism, encapsulation", TestRunner::testOop);

        System.out.println();
        System.out.println("=== TEST SUMMARY: " + passed + " checks passed, " + failed + " failed ===");
        return failed;
    }

    // ------------------------------------------------------------ infrastructure

    private static void run(String title, Runnable test) {
        current = title;
        System.out.println();
        System.out.println("--- " + title);
        try {
            test.run();
        } catch (Throwable t) {
            failed++;
            System.out.println("  FAIL  unexpected exception: " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void check(String what, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + what);
        } else {
            failed++;
            System.out.println("  FAIL  " + what);
        }
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-6;
    }

    private static void expectThrows(String what, Class<? extends Throwable> type, Runnable r) {
        try {
            r.run();
            check(what + " (expected " + type.getSimpleName() + ", nothing thrown)", false);
        } catch (Throwable t) {
            check(what + " -> " + t.getClass().getSimpleName(), type.isInstance(t));
        }
    }

    private static CollectionService fleetOf(CollectionVehicle... vs) {
        CollectionService s = new CollectionService(SampleData.buildNetwork(), new RoutePlanner(), SampleData.DEPOT);
        SampleData.addPoints(s);
        for (CollectionVehicle v : vs) {
            s.addVehicle(v);
        }
        return s;
    }

    private static int count(CollectionService s, CollectionRecord.Type t) {
        int n = 0;
        for (CollectionRecord r : s.getHistory()) {
            if (r.getType() == t) {
                n++;
            }
        }
        return n;
    }

    private static double totalWaste(CollectionService s) {
        double t = 0;
        for (CollectionPoint p : s.getPoints()) {
            t += p.getWasteKg();
        }
        return t;
    }

    // ------------------------------------------------------------------- tests

    private static void testNormalOperation() {
        CollectionService s = SampleData.createService();
        check("network has 13 locations", s.getNetwork().getLocationCount() == 13);
        check("9 collection points registered", s.getPoints().size() == 9);
        check("3 vehicles registered", s.getVehicles().size() == 3);

        double wave1Waste = totalWaste(s);
        SampleData.loadWave1(s);
        check("9 wave-1 requests accepted", s.getPendingCount() == 9);

        List<CollectionRequest> order = s.getPendingSnapshot();
        check("queue order starts with the three HIGH requests R01,R06,R09",
                order.get(0).getId() == 1 && order.get(1).getId() == 6 && order.get(2).getId() == 9);

        s.processAll();
        s.endShift();

        boolean allDone = true;
        for (CollectionRequest r : s.getAllRequests()) {
            allDone &= r.getStatus() == RequestStatus.COMPLETED;
        }
        check("all 9 wave-1 requests COMPLETED", allDone && s.getAllRequests().size() == 9);
        check("every collection point emptied", near(totalWaste(s), 0));
        check("kg delivered to depot equals kg that was at the points (" + wave1Waste + ")",
                near(s.getDepotTotalKg(), wave1Waste));
        boolean home = true;
        for (CollectionVehicle v : s.getVehicles()) {
            home &= v.getLocation().equals("D0") && near(v.getLoadKg(), 0)
                    && v.getStatus() == CollectionVehicle.Status.AVAILABLE;
        }
        check("after end of shift all vehicles are at the depot, empty and available", home);

        List<Integer> firstAssigned = new ArrayList<>();
        for (CollectionRecord r : s.getHistory()) {
            if (r.getType() == CollectionRecord.Type.ASSIGNMENT && firstAssigned.size() < 4) {
                firstAssigned.add(r.getRequestId());
            }
        }
        check("first four assignments were R01,R06,R09 (HIGH) then R02 (first MEDIUM): " + firstAssigned,
                firstAssigned.equals(Arrays.asList(1, 6, 9, 2)));
        check("9 COLLECTION records written", count(s, CollectionRecord.Type.COLLECTION) == 9);

        double sumVehicles = 0;
        for (CollectionVehicle v : s.getVehicles()) {
            sumVehicles += v.getTotalCollectedKg();
        }
        check("sum of per-vehicle collected kg equals depot total", near(sumVehicles, s.getDepotTotalKg()));

        double wave2Waste = 200 + 380 + 300 + 250 + 190 + 280 + 700 + 220;
        List<String> notes = SampleData.loadWave2(s);
        boolean noRejects = true;
        for (String n : notes) {
            noRejects &= !n.startsWith("Rejected") && !n.startsWith("Could not");
        }
        check("wave 2: refills and 8 requests accepted without rejection", noRejects);
        check("17 requests in total", s.getAllRequests().size() == 17);
        s.processAll();
        s.endShift();
        allDone = true;
        for (CollectionRequest r : s.getAllRequests()) {
            allDone &= r.getStatus() == RequestStatus.COMPLETED;
        }
        check("all 17 requests COMPLETED", allDone);
        check("depot total = wave1 + wave2 waste", near(s.getDepotTotalKg(), wave1Waste + wave2Waste));
        check("summary report mentions 17 total", s.summaryReport().contains("17 total"));
        check("history report is non-empty", s.getHistory().size() > 30);
    }

    private static void testFullVehicle() {
        CollectionService s = fleetOf(new SmallTruck("ST-1", 500, "D0"));
        CollectionVehicle st = s.findVehicle("ST-1");

        expectThrows("request for CP8 (650 kg) rejected: larger than any vehicle (500 kg)",
                IllegalArgumentException.class, () -> s.submitRequest("CP8", Priority.HIGH));

        CollectionRequest r1 = s.submitRequest("CP4", Priority.MEDIUM);   // 380 kg
        CollectionRequest r2 = s.submitRequest("CP2", Priority.MEDIUM);   // 420 kg

        CollectionService.StepResult a1 = s.assignNext();
        check("R01 assigned directly (no unloading needed)",
                a1.getKind() == CollectionService.Kind.ASSIGNED && !a1.getMessage().contains("depot first"));
        s.advanceToArrival("ST-1");
        s.confirmCollection("ST-1", null);
        check("ST-1 now carries 380 kg", near(st.getLoadKg(), 380));
        check("380 + 420 kg would exceed the 500 kg capacity", !st.canCarry(420));

        CollectionService.StepResult a2 = s.assignNext();
        check("R02 assignment plans a depot unloading stop first", a2.getMessage().contains("depot first"));
        check("assignment stage is TO_DEPOT_UNLOAD",
                s.getActiveAssignment("ST-1").getStage() == Assignment.Stage.TO_DEPOT_UNLOAD);
        check("depot has received nothing yet", near(s.getDepotTotalKg(), 0));
        s.advanceToArrival("ST-1");
        check("380 kg unloaded at the depot on the way", near(s.getDepotTotalKg(), 380));
        check("vehicle arrived at CP2 empty", st.getLocation().equals("CP2") && near(st.getLoadKg(), 0));
        s.confirmCollection("ST-1", null);
        check("ST-1 now carries 420 kg and is available", near(st.getLoadKg(), 420)
                && st.getStatus() == CollectionVehicle.Status.AVAILABLE);

        int unloadSeq = -1, secondCollectionSeq = -1, collections = 0;
        for (CollectionRecord r : s.getHistory()) {
            if (r.getType() == CollectionRecord.Type.UNLOAD && near(r.getKg(), 380)) {
                unloadSeq = r.getSequence();
            }
            if (r.getType() == CollectionRecord.Type.COLLECTION && ++collections == 2) {
                secondCollectionSeq = r.getSequence();
            }
        }
        check("history shows the 380 kg UNLOAD before the second COLLECTION",
                unloadSeq > 0 && secondCollectionSeq > unloadSeq);
        check("both requests COMPLETED", r1.getStatus() == RequestStatus.COMPLETED
                && r2.getStatus() == RequestStatus.COMPLETED);

        // confirmed-collection validation, partial collection
        s.submitRequest("CP1", Priority.LOW);                              // 180 kg
        s.assignNext();
        s.advanceToArrival("ST-1");                                        // unloads 420 kg first
        expectThrows("confirming 9999 kg is refused", IllegalArgumentException.class,
                () -> s.confirmCollection("ST-1", 9999.0));
        expectThrows("confirming 0 kg is refused", IllegalArgumentException.class,
                () -> s.confirmCollection("ST-1", 0.0));
        s.confirmCollection("ST-1", 100.0);
        check("partial confirmation of 100 kg leaves 80 kg at CP1", near(s.getPoint("CP1").getWasteKg(), 80));

        String back = s.returnToDepot("ST-1");
        check("return-to-depot unloads the remaining cargo (" + back + ")",
                near(st.getLoadKg(), 0) && st.getLocation().equals("D0")
                        && near(s.getDepotTotalKg(), 380 + 420 + 100));
    }

    private static void testBlockedRoad() {
        CollectionService s = SampleData.createService();
        RoadNetwork n = s.getNetwork();
        RoutePlanner p = s.getPlanner();

        PathResult before = p.shortestPath(n, "D0", "CP1");
        check("D0 -> CP1 shortest = 3.5 km via J1", near(before.getDistanceKm(), 3.5)
                && before.routeString().equals("D0 -> J1 -> CP1"));
        n.blockRoad("J1", "CP1");
        PathResult after = p.shortestPath(n, "D0", "CP1");
        check("with J1-CP1 blocked the shortest route is 7.9 km via J3 and CP2",
                near(after.getDistanceKm(), 7.9) && after.routeString().equals("D0 -> J1 -> J3 -> CP2 -> CP1"));
        check("blocked road is not used", !after.usesRoad("J1", "CP1"));
        n.unblockRoad("J1", "CP1");
        check("reopening restores 3.5 km", near(p.shortestPath(n, "D0", "CP1").getDistanceKm(), 3.5));

        n.blockRoad("J1", "CP5");
        n.blockRoad("CP5", "CP6");
        check("CP5 unreachable when both of its roads are blocked", !p.shortestPath(n, "D0", "CP5").isReachable());
        n.unblockRoad("J1", "CP5");
        n.unblockRoad("CP5", "CP6");

        // service level: dynamic re-planning of a vehicle already on the road
        s.blockRoad("D0", "CP8");
        check("blocking a road that no active route uses causes no reroute", s.getRerouteCount() == 0);
        s.unblockRoad("D0", "CP8");

        s.submitRequest("CP1", Priority.HIGH);
        CollectionService.StepResult r = s.assignNext();
        check("ST-1 (cheapest) assigned: " + r.getMessage(), s.getActiveAssignment("ST-1") != null);
        s.advance("ST-1", 1);
        check("ST-1 is at J1 with 1.5 km left", s.findVehicle("ST-1").getLocation().equals("J1")
                && near(s.getActiveAssignment("ST-1").getPath().getDistanceKm(), 1.5));
        List<String> msgs = s.blockRoad("J1", "CP1");
        check("block triggers a re-plan message: " + msgs.get(msgs.size() - 1),
                msgs.size() == 2 && msgs.get(1).startsWith("Re-planned ST-1"));
        PathResult now = s.getActiveAssignment("ST-1").getPath();
        check("new route J1 -> J3 -> CP2 -> CP1 = 5.9 km", now.routeString().equals("J1 -> J3 -> CP2 -> CP1")
                && near(now.getDistanceKm(), 5.9));
        s.advanceToArrival("ST-1");
        s.confirmCollection("ST-1", null);
        check("total distance driven = 2.0 + 5.9 = 7.9 km", near(s.findVehicle("ST-1").getOdometerKm(), 7.9));
        check("one REROUTE record and rerouteCount == 1",
                count(s, CollectionRecord.Type.REROUTE) == 1 && s.getRerouteCount() == 1);
        check("request completed after the detour",
                s.getAllRequests().get(0).getStatus() == RequestStatus.COMPLETED);
    }

    private static void testEqualPriorities() {
        CollectionService s = SampleData.createService();
        s.submitRequest("CP7", Priority.LOW);      // R1
        s.submitRequest("CP2", Priority.MEDIUM);   // R2
        s.submitRequest("CP4", Priority.HIGH);     // R3
        s.submitRequest("CP6", Priority.MEDIUM);   // R4
        s.submitRequest("CP1", Priority.HIGH);     // R5
        s.submitRequest("CP9", Priority.LOW);      // R6
        s.submitRequest("CP3", Priority.MEDIUM);   // R7
        List<Integer> ids = new ArrayList<>();
        for (CollectionRequest r : s.getPendingSnapshot()) {
            ids.add(r.getId());
        }
        check("queue order is H(R3,R5) M(R2,R4,R7) L(R1,R6): " + ids,
                ids.equals(Arrays.asList(3, 5, 2, 4, 7, 1, 6)));
        check("snapshot does not consume the queue", s.getPendingCount() == 7);

        CollectionRequest a = new CollectionRequest(1, "CP1", Priority.MEDIUM, 5, 10);
        CollectionRequest b = new CollectionRequest(2, "CP2", Priority.MEDIUM, 6, 10);
        check("compareTo: equal priority, earlier arrival sorts first", a.compareTo(b) < 0 && b.compareTo(a) > 0);
        check("compareTo: higher priority beats earlier arrival",
                new CollectionRequest(3, "CP3", Priority.HIGH, 99, 1).compareTo(a) < 0);

        // real execution with ONE vehicle proves the order is honoured end to end
        CollectionService one = fleetOf(new LargeTruck("LT-1", 1200, "D0"));
        one.submitRequest("CP7", Priority.LOW);
        one.submitRequest("CP2", Priority.MEDIUM);
        one.submitRequest("CP4", Priority.HIGH);
        one.submitRequest("CP6", Priority.MEDIUM);
        one.submitRequest("CP1", Priority.HIGH);
        one.submitRequest("CP9", Priority.LOW);
        one.submitRequest("CP3", Priority.MEDIUM);
        one.processAll();
        List<Integer> served = new ArrayList<>();
        for (CollectionRecord r : one.getHistory()) {
            if (r.getType() == CollectionRecord.Type.COLLECTION) {
                served.add(r.getRequestId());
            }
        }
        check("single-vehicle collection order is " + served, served.equals(Arrays.asList(3, 5, 2, 4, 7, 1, 6)));

        // three MEDIUMs that arrive one after another stay in that order
        CollectionService m = SampleData.createService();
        m.submitRequest("CP6", Priority.MEDIUM);
        m.submitRequest("CP1", Priority.MEDIUM);
        m.submitRequest("CP5", Priority.MEDIUM);
        check("three equal MEDIUMs: CP6, CP1, CP5 (arrival order)",
                m.getPendingSnapshot().get(0).getPointId().equals("CP6")
                        && m.getPendingSnapshot().get(1).getPointId().equals("CP1")
                        && m.getPendingSnapshot().get(2).getPointId().equals("CP5"));
    }

    private static void testBreakdown() {
        CollectionService s = SampleData.createService();
        CollectionVehicle st = s.findVehicle("ST-1");

        s.submitRequest("CP4", Priority.MEDIUM);                 // R1, 380 kg
        s.assignNext();
        s.advanceToArrival("ST-1");
        s.confirmCollection("ST-1", null);
        check("ST-1 carries 380 kg after R1", near(st.getLoadKg(), 380));

        CollectionRequest r2 = s.submitRequest("CP3", Priority.HIGH);   // R2, 260 kg
        s.assignNext();
        check("R2 first goes to ST-1 (cheapest, needs depot unload)",
                "ST-1".equals(r2.getAssignedVehicleId()));
        s.advance("ST-1", 1);
        check("ST-1 is at J2 still carrying 380 kg", st.getLocation().equals("J2") && near(st.getLoadKg(), 380));

        List<String> msgs = s.reportBreakdown("ST-1");
        System.out.println("        " + String.join("\n        ", msgs));
        check("ST-1 is BROKEN_DOWN", st.getStatus() == CollectionVehicle.Status.BROKEN_DOWN);
        check("onboard cargo recovered to depot (380 kg), vehicle empty",
                near(st.getLoadKg(), 0) && near(s.getDepotTotalKg(), 380));
        check("ST-1 no longer has an active assignment", s.getActiveAssignment("ST-1") == null);
        check("R2 reassigned to a different vehicle: " + r2.getAssignedVehicleId(),
                r2.getStatus() == RequestStatus.ASSIGNED && "LT-1".equals(r2.getAssignedVehicleId()));
        check("R2 keeps HIGH priority and arrival order #2, reassignCount == 1",
                r2.getPriority() == Priority.HIGH && r2.getArrivalOrder() == 2 && r2.getReassignCount() == 1);
        check("BREAKDOWN and REASSIGNMENT records exist",
                count(s, CollectionRecord.Type.BREAKDOWN) == 1 && count(s, CollectionRecord.Type.REASSIGNMENT) == 1);

        s.advanceToArrival("LT-1");
        s.confirmCollection("LT-1", null);
        check("R2 completed by LT-1", r2.getStatus() == RequestStatus.COMPLETED
                && "LT-1".equals(r2.getCompletedByVehicleId()));

        expectThrows("second breakdown report for same vehicle refused", IllegalStateException.class,
                () -> s.reportBreakdown("ST-1"));
        expectThrows("recalling a broken vehicle is refused", IllegalStateException.class,
                () -> s.returnToDepot("ST-1"));
        check("breakdown of an idle vehicle works and requeues nothing",
                s.reportBreakdown("LT-2").size() == 1 && s.getPendingCount() == 0);
        s.repairVehicle("ST-1");
        check("repaired ST-1 is at the depot and AVAILABLE", st.getLocation().equals("D0")
                && st.getStatus() == CollectionVehicle.Status.AVAILABLE);
        expectThrows("repairing a healthy vehicle is refused", IllegalStateException.class,
                () -> s.repairVehicle("ST-1"));
    }

    private static void testDeferred() {
        // (a) no operational vehicle large enough
        CollectionService s = SampleData.createService();
        s.reportBreakdown("LT-1");
        s.reportBreakdown("LT-2");
        CollectionRequest big = s.submitRequest("CP8", Priority.HIGH);   // 650 kg; fleet max 1200 so accepted
        CollectionService.StepResult r = s.assignNext();
        check("650 kg request deferred while only the 500 kg truck is working",
                r.getKind() == CollectionService.Kind.DEFERRED && big.getStatus() == RequestStatus.DEFERRED);
        check("deferred report lists it", s.deferredReport().contains("CP8"));
        s.repairVehicle("LT-1");
        check("retryDeferred re-queues 1 request", s.retryDeferred() == 1 && s.getPendingCount() == 1);
        check("now assignable", s.assignNext().getKind() == CollectionService.Kind.ASSIGNED
                && "LT-1".equals(big.getAssignedVehicleId()));

        // (b) unreachable because of blocked roads
        CollectionService u = SampleData.createService();
        u.blockRoad("J1", "CP5");
        u.blockRoad("CP5", "CP6");
        CollectionRequest cp5 = u.submitRequest("CP5", Priority.LOW);
        check("CP5 request deferred (blocked roads)", u.assignNext().getKind() == CollectionService.Kind.DEFERRED
                && cp5.getDeferReason().contains("blocked"));
        u.unblockRoad("J1", "CP5");
        u.retryDeferred();
        check("after reopening, request is assigned", u.assignNext().getKind() == CollectionService.Kind.ASSIGNED);

        // (c) road blocked mid-route that leaves the vehicle with no way forward
        CollectionService m = fleetOf(new SmallTruck("ST-1", 500, "D0"));
        m.submitRequest("CP5", Priority.HIGH);
        m.assignNext();
        m.advance("ST-1", 1);                       // at J1, heading J1 -> CP5
        m.blockRoad("J1", "CP5");                   // detour via J3-CP6-CP5 exists
        check("detour found (reroute counted)", m.getRerouteCount() == 1);
        m.blockRoad("CP6", "CP5");                  // now CP5 is cut off entirely
        check("vehicle with no route left releases the request back to the queue",
                m.getActiveAssignment("ST-1") == null && m.getPendingCount() == 1
                        && m.getAllRequests().get(0).getReassignCount() == 1);
    }

    private static void testDijkstraAgainstFloydWarshall() {
        CollectionService s = SampleData.createService();
        check("sample network: all-pairs match", allPairsMatch(s.getNetwork()));
        s.getNetwork().blockRoad("J1", "J3");
        s.getNetwork().blockRoad("D0", "CP8");
        s.getNetwork().blockRoad("CP2", "CP3");
        check("sample network with 3 blocked roads: all-pairs match", allPairsMatch(s.getNetwork()));

        Random rnd = new Random(42);
        boolean allOk = true;
        for (int g = 0; g < 40; g++) {
            RoadNetwork n = new RoadNetwork();
            int nodes = 12;
            for (int i = 0; i < nodes; i++) {
                n.addLocation("N" + i, "Node " + i);
            }
            for (int i = 0; i < nodes; i++) {
                for (int j = i + 1; j < nodes; j++) {
                    if (rnd.nextDouble() < 0.25) {
                        n.addRoad("N" + i, "N" + j, 1 + rnd.nextInt(90) / 10.0);
                    }
                }
            }
            for (int i = 0; i < nodes; i++) {
                for (int j = i + 1; j < nodes; j++) {
                    if (n.getRoad("N" + i, "N" + j) != null && rnd.nextDouble() < 0.15) {
                        n.blockRoad("N" + i, "N" + j);
                    }
                }
            }
            allOk &= allPairsMatch(n);
        }
        check("40 random graphs (12 nodes, random blocks, some disconnected): all pairs match", allOk);
    }

    private static boolean allPairsMatch(RoadNetwork n) {
        List<String> ids = n.getLocationIds();
        int k = ids.size();
        double[][] d = new double[k][k];
        for (int i = 0; i < k; i++) {
            Arrays.fill(d[i], Double.POSITIVE_INFINITY);
            d[i][i] = 0;
            for (RoadNetwork.Road r : n.getNeighbours(ids.get(i))) {
                if (!r.isBlocked()) {
                    d[i][ids.indexOf(r.getDestination())] = Math.min(d[i][ids.indexOf(r.getDestination())], r.getDistanceKm());
                }
            }
        }
        for (int m = 0; m < k; m++) {
            for (int i = 0; i < k; i++) {
                for (int j = 0; j < k; j++) {
                    if (d[i][m] + d[m][j] < d[i][j]) {
                        d[i][j] = d[i][m] + d[m][j];
                    }
                }
            }
        }
        RoutePlanner p = new RoutePlanner();
        for (int i = 0; i < k; i++) {
            for (int j = 0; j < k; j++) {
                PathResult r = p.shortestPath(n, ids.get(i), ids.get(j));
                if (Double.isInfinite(d[i][j])) {
                    if (r.isReachable()) {
                        return false;
                    }
                } else {
                    if (!r.isReachable() || Math.abs(r.getDistanceKm() - d[i][j]) > 1e-6) {
                        return false;
                    }
                    // the returned node list must itself sum to that distance
                    double sum = 0;
                    List<String> nodes = r.getNodes();
                    for (int x = 0; x + 1 < nodes.size(); x++) {
                        RoadNetwork.Road road = n.getRoad(nodes.get(x), nodes.get(x + 1));
                        if (road == null || road.isBlocked()) {
                            return false;
                        }
                        sum += road.getDistanceKm();
                    }
                    if (Math.abs(sum - d[i][j]) > 1e-6) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static void testMinHeap() {
        MinHeap h = new MinHeap();
        check("new heap is empty", h.isEmpty());
        expectThrows("extractMin on empty heap", NoSuchElementException.class, h::extractMin);
        Random rnd = new Random(7);
        double[] values = new double[1000];
        for (int i = 0; i < values.length; i++) {
            values[i] = rnd.nextInt(500) / 10.0;   // many duplicates
            h.insert("n" + i, values[i]);
        }
        check("size is 1000 after 1000 inserts (grows past initial capacity)", h.size() == 1000);
        Arrays.sort(values);
        boolean sorted = true;
        for (double v : values) {
            sorted &= h.extractMin().distance == v;
        }
        check("1000 extractions come out in non-decreasing order", sorted && h.isEmpty());
    }

    private static void testValidation() {
        CollectionService s = SampleData.createService();
        expectThrows("unknown collection point", IllegalArgumentException.class, () -> s.submitRequest("CP99", Priority.HIGH));
        expectThrows("depot is not a collection point", IllegalArgumentException.class, () -> s.submitRequest("D0", Priority.HIGH));
        expectThrows("null priority", IllegalArgumentException.class, () -> s.submitRequest("CP1", null));
        s.submitRequest("CP1", Priority.HIGH);
        expectThrows("duplicate open request for CP1", IllegalStateException.class, () -> s.submitRequest("CP1", Priority.LOW));
        expectThrows("changing waste while a request is open", IllegalStateException.class, () -> s.setWasteLevel("CP1", 50));
        expectThrows("negative waste level", IllegalArgumentException.class, () -> s.setWasteLevel("CP5", -1));
        expectThrows("waste level above 5000 kg", IllegalArgumentException.class, () -> s.setWasteLevel("CP5", 6000));
        s.setWasteLevel("CP5", 0);
        expectThrows("request for a point with no waste", IllegalArgumentException.class, () -> s.submitRequest("CP5", Priority.LOW));
        expectThrows("advance a vehicle that has no job", IllegalStateException.class, () -> s.advance("LT-1", 1));
        expectThrows("advance by 0 hops", IllegalArgumentException.class, () -> s.advance("LT-1", 0));
        expectThrows("unknown vehicle", IllegalArgumentException.class, () -> s.advance("XX-9", 1));
        expectThrows("confirm with no job", IllegalStateException.class, () -> s.confirmCollection("LT-1", null));
        s.assignNext();
        String busy = s.findVehicle("ST-1").getStatus() == CollectionVehicle.Status.EN_ROUTE ? "ST-1" : "LT-1";
        expectThrows("confirm before arrival", IllegalStateException.class, () -> s.confirmCollection(busy, null));
        expectThrows("recall a vehicle that is busy", IllegalStateException.class, () -> s.returnToDepot(busy));
        expectThrows("block a road that does not exist", IllegalArgumentException.class, () -> s.blockRoad("CP1", "CP9"));
        expectThrows("block a road at an unknown location", IllegalArgumentException.class, () -> s.blockRoad("ZZ", "J1"));
        s.blockRoad("D0", "J2");
        expectThrows("block an already blocked road", IllegalStateException.class, () -> s.blockRoad("J2", "D0"));
        expectThrows("reopen a road that is open", IllegalStateException.class, () -> s.unblockRoad("D0", "J1"));
        expectThrows("duplicate road", IllegalArgumentException.class, () -> s.getNetwork().addRoad("D0", "J1", 1));
        expectThrows("zero-length road", IllegalArgumentException.class, () -> s.getNetwork().addRoad("D0", "CP9", 0));
        expectThrows("duplicate vehicle id", IllegalArgumentException.class, () -> s.addVehicle(new SmallTruck("ST-1", 300, "D0")));
        expectThrows("shortest path to unknown node", IllegalArgumentException.class,
                () -> s.getPlanner().shortestPath(s.getNetwork(), "D0", "NOPE"));
        check("Priority.parse accepts H/m/Low/1", Priority.parse("H") == Priority.HIGH && Priority.parse("m") == Priority.MEDIUM
                && Priority.parse("Low") == Priority.LOW && Priority.parse("1") == Priority.HIGH);
        check("Priority.parse rejects junk", Priority.parse("urgent") == null && Priority.parse(null) == null);
    }

    private static void testOop() {
        check("CollectionVehicle is abstract", Modifier.isAbstract(CollectionVehicle.class.getModifiers()));
        List<CollectionVehicle> fleet = new ArrayList<>();
        fleet.add(new SmallTruck("A", 500, "D0"));
        fleet.add(new LargeTruck("B", 1200, "D0"));
        check("inheritance: both trucks are CollectionVehicles",
                fleet.get(0) instanceof CollectionVehicle && fleet.get(1) instanceof CollectionVehicle);
        check("polymorphism: same call, different cost/km (6 vs 14)",
                near(fleet.get(0).getCostPerKm(), 6) && near(fleet.get(1).getCostPerKm(), 14));
        check("polymorphism: same call, different travel time for 10 km (20 vs 24 min)",
                near(fleet.get(0).travelMinutes(10), 20) && near(fleet.get(1).travelMinutes(10), 24));
        check("polymorphism: type names differ",
                !fleet.get(0).getTypeName().equals(fleet.get(1).getTypeName()));
        expectThrows("encapsulation: overloading a vehicle is refused", IllegalStateException.class,
                () -> fleet.get(0).load(600));
        fleet.get(0).load(300);
        expectThrows("encapsulation: second load past capacity refused", IllegalStateException.class,
                () -> fleet.get(0).load(300));
        check("load unchanged after refused load", near(fleet.get(0).getLoadKg(), 300));
        CollectionPoint p = new CollectionPoint("CP1", "Test", "Z", 100);
        expectThrows("encapsulation: removing more waste than present", IllegalArgumentException.class, () -> p.removeWaste(150));
        check("composition: service owns network, planner, vehicles and points",
                SampleData.createService().getNetwork() != null && SampleData.createService().getVehicles().size() == 3);
        expectThrows("unmodifiable vehicle list", UnsupportedOperationException.class,
                () -> SampleData.createService().getVehicles().clear());
    }
}
