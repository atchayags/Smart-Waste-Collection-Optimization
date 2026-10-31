package com.marudham.web;

import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import com.marudham.web.service.RoadIssueService;
import com.marudham.web.service.RouteOptimizationService;
import com.marudham.web.service.RouteOptimizationService.OptimizationPreviewResult;
import com.marudham.web.service.RouteOptimizationService.OptimizationRequest;
import com.marudham.web.service.RouteOptimizationService.OptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class RoadIssueAndRouteRecalculationTest {

    @Autowired
    private RoadIssueService roadIssueService;

    @Autowired
    private RouteOptimizationService optimizationService;

    @Autowired
    private RoadIssueRepository roadIssueRepository;

    @Autowired
    private RoadRestrictionRepository restrictionRepository;

    @Autowired
    private RouteRevisionRepository revisionRepository;

    @Autowired
    private RoutePlanRepository routePlanRepository;

    @Autowired
    private RouteStopRepository routeStopRepository;

    @Autowired
    private TruckRepository truckRepository;

    @Autowired
    private CollectionRequestRepository requestRepository;

    @Autowired
    private CollectionPointRepository pointRepository;

    @BeforeEach
    void resetTestTruck() {
        truckRepository.findById("TRK-01").ifPresent(t -> {
            t.setStatus("ACTIVE");
            t.setCurrentRouteId(null);
            t.setAssignedLoadKg(0.0);
            truckRepository.save(t);
        });
    }

    @Test
    @DisplayName("1. Driver reports road blockage and verifies persistence in MySQL")
    void testDriverReportRoadIssue() {
        Truck truck = truckRepository.findAll().get(0);
        RoadIssue issue = roadIssueService.reportIssue(
                "ROAD_BLOCKED",
                "Fallen tree blocking the municipal corridor",
                8.7940,
                78.1345,
                "Gandhi Circle Sector",
                "driver1",
                truck.getId(),
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        );

        assertNotNull(issue.getId(), "Issue should have a generated ID");
        assertEquals("driver1", issue.getReportingDriverUsername());
        assertEquals(truck.getId(), issue.getTruckId());
        assertEquals("ROAD_BLOCKED", issue.getIssueType());
        assertEquals("REPORTED", issue.getStatus());
        assertEquals(8.7940, issue.getLatitude(), 0.0001);
        assertEquals(78.1345, issue.getLongitude(), 0.0001);
        assertNotNull(issue.getFromNode(), "Should snap to a road network edge source node");
        assertNotNull(issue.getToNode(), "Should snap to a road network edge target node");
    }

    @Test
    @DisplayName("2. Route Plan preview and dispatch generates shared metrics and segment JSON")
    void testRoutePreviewAndDispatch() {
        Truck truck = truckRepository.findAll().get(0);
        truck.setStatus("ACTIVE");
        truck.setCurrentRouteId(null);
        truck.setAssignedLoadKg(0.0);
        truckRepository.save(truck);

        CollectionPoint cp = pointRepository.findAll().get(0);
        CollectionRequest r = new CollectionRequest(cp.getId(), cp.getName(), "HIGH", 120.0);
        r = requestRepository.save(r);

        OptimizationRequest optReq = new OptimizationRequest();
        optReq.requestIds = List.of(r.getId());
        optReq.truckIds = List.of(truck.getId());
        optReq.startType = "DEPOT";

        OptimizationPreviewResult preview = optimizationService.previewRoute(optReq);

        assertNotNull(preview, "Preview should be generated");
        assertTrue(preview.totalDistanceKm > 0, "Total distance should be positive");
        assertNotNull(preview.routeGeometryJson, "Route geometry should not be null");
        assertFalse(preview.segments.isEmpty(), "Segments should not be empty");
        assertTrue(preview.drivingTimeMin > 0, "Driving time should be positive");

        // Dispatch route
        OptimizationResult dispatchResult = optimizationService.planAndDispatch(optReq, "admin");

        assertFalse(dispatchResult.createdRoutes.isEmpty(), "Dispatched routes should not be empty");
        RoutePlan plan = dispatchResult.createdRoutes.get(0);
        assertNotNull(plan.getId(), "Persisted route must have MySQL ID");
        assertEquals(truck.getId(), plan.getTruckId());
        assertEquals(1, plan.getRevisionNumber());
        assertNotNull(plan.getSegmentDetailsJson());
        assertNotNull(plan.getStops());
        assertFalse(plan.getStops().isEmpty());
    }

    @Test
    @DisplayName("3. Verify road issue, activate restriction and trigger Dijkstra route recalculation")
    void testVerifyAndRecalculateRoute() {
        Truck truck = truckRepository.findAll().get(0);
        truck.setStatus("ACTIVE");
        truck.setCurrentRouteId(null);
        truck.setAssignedLoadKg(0.0);
        truckRepository.save(truck);

        // Step A: Ensure we have an active route for vehicle
        CollectionPoint cp = pointRepository.findAll().get(0);
        CollectionRequest r = new CollectionRequest(cp.getId(), cp.getName(), "HIGH", 140.0);
        r = requestRepository.save(r);

        OptimizationRequest optReq = new OptimizationRequest();
        optReq.requestIds = List.of(r.getId());
        optReq.truckIds = List.of(truck.getId());
        optReq.startType = "DEPOT";

        OptimizationResult dispatchResult = optimizationService.planAndDispatch(optReq, "admin");
        RoutePlan initialPlan = dispatchResult.createdRoutes.get(0);
        Long routeId = initialPlan.getId();
        int initialRev = initialPlan.getRevisionNumber();

        // Step B: Driver reports a blockage between DEPOT and J1
        RoadIssue issue = roadIssueService.reportIssue(
                "ROAD_BLOCKED",
                "Major construction pipe burst",
                8.7945,
                78.1345,
                "North Ring Road",
                "driver1",
                truck.getId(),
                null
        );

        // Step C: Admin verifies and activates restriction
        Map<String, Object> verifyRes = roadIssueService.verifyAndActivateRestriction(issue.getId(), "admin", "Road blockage verified by municipal inspector");
        assertNotNull(verifyRes);

        RoadIssue verified = roadIssueRepository.findById(issue.getId()).orElseThrow();
        assertEquals("VERIFIED", verified.getStatus());

        // Step D: Verify that a RoadRestriction was created and active
        List<RoadRestriction> activeRestrictions = restrictionRepository.findByActiveTrue();
        assertFalse(activeRestrictions.isEmpty(), "Active road restrictions must exist");

        // Step E: Verify RoutePlan was recalculated to a higher revision
        RoutePlan updatedPlan = routePlanRepository.findById(routeId).orElseThrow();
        assertTrue(updatedPlan.getRevisionNumber() > initialRev, "Route revision number must advance");
        assertNotNull(updatedPlan.getLastRecalculatedAt(), "Recalculation timestamp must be set");
        assertNotNull(updatedPlan.getRecalculationReason(), "Recalculation reason must be set");

        // Step F: Verify revision history record was created
        List<RouteRevision> history = revisionRepository.findByRoutePlanIdOrderByRevisionNumberDesc(routeId);
        assertFalse(history.isEmpty(), "Route revision history record must be created");
        assertEquals(initialRev, history.get(0).getRevisionNumber(), "Archived revision should match prior revision");

        // Step G: Admin resolves issue and reopens road
        Map<String, Object> resolveRes = roadIssueService.resolveIssue(issue.getId(), "admin", "Road obstruction cleared");
        assertNotNull(resolveRes);

        RoadIssue resolved = roadIssueRepository.findById(issue.getId()).orElseThrow();
        assertEquals("RESOLVED", resolved.getStatus());

        List<RoadRestriction> remainingRestrictions = restrictionRepository.findByActiveTrue();
        boolean stillRestricted = remainingRestrictions.stream().anyMatch(res -> issue.getId().equals(res.getRoadIssueId()));
        assertFalse(stillRestricted, "Restriction for resolved issue must be inactive");
    }
}
