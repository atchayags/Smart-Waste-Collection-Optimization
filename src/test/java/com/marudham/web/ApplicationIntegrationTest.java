package com.marudham.web;

import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import com.marudham.web.service.RouteOptimizationService;
import marudham.PathResult;
import marudham.RoadNetwork;
import marudham.RoutePlanner;
import marudham.TestRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ApplicationIntegrationTest {

    @Autowired
    private DepotRepository depotRepository;

    @Autowired
    private CollectionPointRepository pointRepository;

    @Autowired
    private TruckRepository truckRepository;

    @Autowired
    private RoutePlanRepository routePlanRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private CollectionRequestRepository requestRepository;

    @Autowired
    private RouteOptimizationService optimizationService;

    @Test
    @DisplayName("1. Spring Context Boots Successfully")
    void contextLoads() {
        assertNotNull(depotRepository, "DepotRepository bean should be injected");
        assertNotNull(pointRepository, "CollectionPointRepository bean should be injected");
        assertNotNull(truckRepository, "TruckRepository bean should be injected");
        assertNotNull(optimizationService, "RouteOptimizationService bean should be injected");
    }

    @Test
    @DisplayName("2. Depot Entity Persistence and Location Verification")
    void testDepotPersistence() {
        List<Depot> depots = depotRepository.findAll();
        assertFalse(depots.isEmpty(), "Depot should be seeded in MySQL");
        Depot d = depots.get(0);
        assertNotNull(d.getName(), "Depot name should not be null");
        assertTrue(d.getLatitude() > 8.0 && d.getLatitude() < 9.0, "Depot latitude should be within Tirunelveli municipality bounds");
        assertTrue(d.getLongitude() > 77.0 && d.getLongitude() < 79.0, "Depot longitude should be within Tirunelveli municipality bounds");
    }

    @Test
    @DisplayName("3. Municipal Fleet and Collection Points in Database")
    void testFleetAndPoints() {
        List<CollectionPoint> points = pointRepository.findAll();
        assertFalse(points.isEmpty(), "Collection points should exist");
        assertTrue(points.size() >= 4, "Expected at least 4 municipal collection points");

        List<Truck> trucks = truckRepository.findAll();
        assertFalse(trucks.isEmpty(), "Fleet vehicles should exist");
        Truck truck = trucks.get(0);
        assertTrue(truck.getCapacityKg() > 0, "Truck capacity must be strictly positive");
    }

    @Test
    @DisplayName("4. Capacity-Aware Route Optimizer Engine Generates Valid Plans")
    void testRouteOptimizationService() {
        List<CollectionRequest> pendings = requestRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        CollectionRequest req;
        if (pendings.isEmpty()) {
            CollectionPoint pt = pointRepository.findAll().get(0);
            req = requestRepository.save(new CollectionRequest(pt.getId(), pt.getName(), "HIGH", 120.0, "GENERAL", "admin"));
        } else {
            req = pendings.get(0);
        }

        RouteOptimizationService.OptimizationRequest optReq = new RouteOptimizationService.OptimizationRequest();
        optReq.requestIds = List.of(req.getId());
        optReq.startType = "DEPOT";

        RouteOptimizationService.OptimizationResult res = optimizationService.planAndDispatch(optReq, "admin");
        assertNotNull(res, "Optimization result must not be null");
        assertFalse(res.createdRoutes.isEmpty(), "At least one route should be generated for the pending request");
        RoutePlan plan = res.createdRoutes.get(0);
        assertNotNull(plan.getId(), "Generated RoutePlan must have a persistent ID");
        assertTrue(plan.getTotalDistanceKm() >= 0.0, "Total distance must be non-negative");
        assertTrue(plan.getEstimatedDurationMin() >= 0.0, "Total duration must be non-negative");
        assertFalse(plan.getStops().isEmpty(), "RoutePlan must contain route stops");
    }

    @Test
    @DisplayName("5. Core Domain Algorithms (Dijkstra, Floyd-Warshall, MinHeap, Vehicle Reallocation, OOP)")
    void testCoreDomainAlgorithms() {
        int failures = TestRunner.runAllTests();
        assertEquals(0, failures, "All 118 checks in TestRunner must pass with 0 failures");
    }

    @Test
    @DisplayName("6. Dijkstra Shortest-Path Algorithm on Weighted Road Graph")
    void testDijkstraShortestPathAlgorithm() {
        RoadNetwork net = new RoadNetwork();
        net.addLocation("A", "Node A");
        net.addLocation("B", "Node B");
        net.addLocation("C", "Node C");
        net.addLocation("D", "Node D");
        net.addLocation("ISOLATED", "Isolated Node");

        // Roads with non-negative weights
        net.addRoad("A", "B", 4.0);
        net.addRoad("A", "C", 2.0);
        net.addRoad("C", "B", 1.0); // Path A->C->B = 3.0 km (shorter than direct A->B 4.0 km)
        net.addRoad("B", "D", 5.0);

        RoutePlanner planner = new RoutePlanner();

        // 1. Same source and destination
        PathResult sameNode = planner.shortestPath(net, "A", "A");
        assertTrue(sameNode.isReachable(), "Same node must be reachable");
        assertEquals(0.0, sameNode.getDistanceKm(), 1e-4, "Distance from node to itself must be 0");

        // 2. Multi-path optimal choice: A -> C -> B (3.0 km) beats direct A -> B (4.0 km)
        PathResult toB = planner.shortestPath(net, "A", "B");
        assertTrue(toB.isReachable());
        assertEquals(3.0, toB.getDistanceKm(), 1e-4);
        assertEquals(List.of("A", "C", "B"), toB.getNodes());

        // 3. Multi-hop: A -> C -> B -> D (8.0 km)
        PathResult toD = planner.shortestPath(net, "A", "D");
        assertTrue(toD.isReachable());
        assertEquals(8.0, toD.getDistanceKm(), 1e-4);
        assertEquals(List.of("A", "C", "B", "D"), toD.getNodes());

        // 4. Disconnected / unreachable node
        PathResult toIso = planner.shortestPath(net, "A", "ISOLATED");
        assertFalse(toIso.isReachable(), "Isolated node must be unreachable");
        assertEquals(Double.POSITIVE_INFINITY, toIso.getDistanceKm());

        // 5. Blocked roads dynamic routing
        net.blockRoad("C", "B"); // Block shortcut C-B
        PathResult toBDelayed = planner.shortestPath(net, "A", "B");
        assertTrue(toBDelayed.isReachable());
        assertEquals(4.0, toBDelayed.getDistanceKm(), 1e-4, "Must route via open direct road A-B (4.0 km) when C-B is blocked");
        assertEquals(List.of("A", "B"), toBDelayed.getNodes());

        // Reopening road restores shortcut
        net.unblockRoad("C", "B");
        PathResult restored = planner.shortestPath(net, "A", "B");
        assertEquals(3.0, restored.getDistanceKm(), 1e-4);

        // 6. Validation: invalid edge weights rejected
        assertThrows(IllegalArgumentException.class, () -> net.addRoad("A", "ISOLATED", -2.5));
        assertThrows(IllegalArgumentException.class, () -> net.addRoad("A", "ISOLATED", 0.0));
        assertThrows(IllegalArgumentException.class, () -> net.addRoad("A", "A", 1.0));
    }

    @Test
    @DisplayName("7. Vehicle Capacity & Payload Constraints Verification")
    void testVehicleCapacityConstraints() {
        Truck truck = truckRepository.save(new Truck("TEST_TRUCK_500", "Small Test Truck", 500.0));
        CollectionPoint cp1 = pointRepository.findAll().get(0);
        CollectionPoint cp2 = pointRepository.findAll().get(1);

        // Scenario: Requests total 650 kg > 500 kg capacity
        CollectionRequest reqA = requestRepository.save(new CollectionRequest(cp1.getId(), cp1.getName(), "HIGH", 300.0, "GENERAL", "testUser"));
        CollectionRequest reqB = requestRepository.save(new CollectionRequest(cp2.getId(), cp2.getName(), "MEDIUM", 350.0, "GENERAL", "testUser"));

        RouteOptimizationService.OptimizationRequest optReq = new RouteOptimizationService.OptimizationRequest();
        optReq.requestIds = List.of(reqA.getId(), reqB.getId());
        optReq.truckIds = List.of(truck.getId());
        optReq.startType = "DEPOT";

        // Preview should show reqA assigned (300 kg) and reqB unassigned (exceeds capacity)
        RouteOptimizationService.OptimizationPreviewResult preview = optimizationService.previewRoute(optReq);
        assertNotNull(preview);
        assertEquals(1, preview.plannedStopsCount, "Only 1 request fitting within 500 kg should be planned");
        assertEquals(300.0, preview.totalWasteKg, 1e-2);
        assertTrue(preview.capacityUtilizationPct <= 100.0);
        assertFalse(preview.unassignedRequests.isEmpty(), "Overcapacity request should be reported as unassigned");
        assertTrue(preview.unassignedRequests.get(0).reason.contains("Exceeds vehicle remaining capacity"));

        // Clean up test truck & requests
        requestRepository.delete(reqA);
        requestRepository.delete(reqB);
        truckRepository.delete(truck);
    }

    @Test
    @DisplayName("8. Two-Phase Route Planning: Preview vs Dispatched Persistence")
    void testPreviewVsDispatchWorkflow() {
        CollectionPoint pt = pointRepository.findById("CP02").orElseGet(() -> pointRepository.findAll().get(0));
        CollectionRequest req = requestRepository.save(new CollectionRequest(pt.getId(), pt.getName(), "HIGH", 150.0, "GENERAL", "testUser"));

        RouteOptimizationService.OptimizationRequest optReq = new RouteOptimizationService.OptimizationRequest();
        optReq.requestIds = List.of(req.getId());
        optReq.startType = "DEPOT";

        // Step 1: Preview must NOT alter request status or persist RoutePlan
        RouteOptimizationService.OptimizationPreviewResult preview = optimizationService.previewRoute(optReq);
        assertNotNull(preview);
        assertEquals(1, preview.plannedStopsCount);
        assertTrue(preview.totalDistanceKm > 0);
        assertNotNull(preview.routeGeometryJson);

        CollectionRequest afterPreview = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals("PENDING", afterPreview.getStatus(), "Request must remain PENDING after preview");
        assertNull(afterPreview.getAssignedRouteId(), "Route ID must be null after preview");

        // Step 2: Confirmed Dispatch must persist RoutePlan and update request to ASSIGNED
        RouteOptimizationService.OptimizationResult dispatch = optimizationService.planAndDispatch(optReq, "testAdmin");
        assertNotNull(dispatch);
        assertFalse(dispatch.createdRoutes.isEmpty());
        RoutePlan savedPlan = dispatch.createdRoutes.get(0);
        assertNotNull(savedPlan.getId());

        CollectionRequest afterDispatch = requestRepository.findById(req.getId()).orElseThrow();
        assertEquals("ASSIGNED", afterDispatch.getStatus(), "Request must transition to ASSIGNED after dispatch");
        assertEquals(savedPlan.getId(), afterDispatch.getAssignedRouteId());

        // Verify Authoritative Shared Route attributes
        assertEquals(savedPlan.getDepotLat(), savedPlan.getDepotLatitude(), 1e-6);
        assertEquals(savedPlan.getDepotLng(), savedPlan.getDepotLongitude(), 1e-6);
        assertEquals(savedPlan.getPlannedStartLat(), savedPlan.getPlannedStartLatitude(), 1e-6);
        assertNotNull(savedPlan.getRouteGeometryJson());
        assertFalse(savedPlan.getStops().isEmpty());

        // Clean up
        routePlanRepository.delete(savedPlan);
        requestRepository.delete(afterDispatch);
    }
}
