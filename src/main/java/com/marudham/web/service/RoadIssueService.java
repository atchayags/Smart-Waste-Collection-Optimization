package com.marudham.web.service;

import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class RoadIssueService {

    private final RoadIssueRepository roadIssueRepo;
    private final RoadRestrictionRepository roadRestrictionRepo;
    private final RouteRevisionRepository routeRevisionRepo;
    private final RoutePlanRepository routePlanRepo;
    private final TruckRepository truckRepo;
    private final AuditService auditService;
    private final RouteOptimizationService routeOptimizer;

    public RoadIssueService(RoadIssueRepository roadIssueRepo,
                            RoadRestrictionRepository roadRestrictionRepo,
                            RouteRevisionRepository routeRevisionRepo,
                            RoutePlanRepository routePlanRepo,
                            TruckRepository truckRepo,
                            AuditService auditService,
                            RouteOptimizationService routeOptimizer) {
        this.roadIssueRepo = roadIssueRepo;
        this.roadRestrictionRepo = roadRestrictionRepo;
        this.routeRevisionRepo = routeRevisionRepo;
        this.routePlanRepo = routePlanRepo;
        this.truckRepo = truckRepo;
        this.auditService = auditService;
        this.routeOptimizer = routeOptimizer;
    }

    @Transactional
    public RoadIssue reportIssue(String issueType, String description, double latitude, double longitude,
                                String locationName, String reportingDriverUsername, String truckId,
                                String photoUrl) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude [-90,90] and longitude [-180,180] are required.");
        }
        if (issueType == null || issueType.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue type is required (e.g. ROAD_BLOCKED, ROAD_DAMAGED).");
        }
        String typeNorm = issueType.trim().toUpperCase();
        if (!Set.of("ROAD_BLOCKED", "ROAD_DAMAGED", "ROAD_MAINTENANCE", "OTHER").contains(typeNorm)) {
            typeNorm = "ROAD_BLOCKED";
        }

        String desc = (description != null && !description.isBlank()) ? description.trim() : (typeNorm + " reported by driver");
        String locName = (locationName != null && !locationName.isBlank()) ? locationName.trim() : "Reported Location";

        RoadIssue issue = new RoadIssue(typeNorm, desc, latitude, longitude, locName, reportingDriverUsername, truckId);
        if (photoUrl != null && !photoUrl.isBlank()) {
            issue.setPhotoUrl(photoUrl);
        }

        // Snap to nearest road segment in municipal network
        String[] edge = routeOptimizer.findNearestRoadEdge(latitude, longitude);
        if (edge != null && edge.length == 2) {
            issue.setFromNode(edge[0]);
            issue.setToNode(edge[1]);
        }

        // Link affected active route if truck has one
        if (truckId != null) {
            Optional<Truck> t = truckRepo.findById(truckId);
            if (t.isPresent() && t.get().getCurrentRouteId() != null) {
                issue.setAffectedRouteId(t.get().getCurrentRouteId());
            }
        }

        RoadIssue saved = roadIssueRepo.save(issue);
        auditService.record("ROAD_ISSUE_REPORTED", "Driver " + reportingDriverUsername + " reported " + typeNorm + " (#" + saved.getId() + ") at " + locName, reportingDriverUsername, saved.getId());
        return saved;
    }

    public List<RoadIssue> getAllIssues(String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            return roadIssueRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return roadIssueRepo.findAllByOrderByCreatedAtDesc();
    }

    public RoadIssue getIssueById(Long id) {
        return roadIssueRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Road issue #" + id + " not found."));
    }

    public List<RoadIssue> getDriverIssues(String driverUsername) {
        return roadIssueRepo.findByReportingDriverUsernameOrderByCreatedAtDesc(driverUsername);
    }

    public List<RoadRestriction> getActiveRestrictions() {
        return roadRestrictionRepo.findByActiveTrue();
    }

    public List<RouteRevision> getRouteRevisions(Long routePlanId) {
        return routeRevisionRepo.findByRoutePlanIdOrderByRevisionNumberDesc(routePlanId);
    }

    @Transactional
    public Map<String, Object> verifyAndActivateRestriction(Long issueId, String adminUsername, String adminNotes) {
        RoadIssue issue = roadIssueRepo.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Road issue #" + issueId + " not found."));

        if ("RESOLVED".equalsIgnoreCase(issue.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Issue #" + issueId + " is already RESOLVED.");
        }

        issue.setStatus("VERIFIED");
        issue.setVerifiedAt(LocalDateTime.now());
        issue.setVerifiedBy(adminUsername);
        if (adminNotes != null && !adminNotes.isBlank()) {
            issue.setAdminNotes(adminNotes.trim());
        }

        // Ensure fromNode and toNode are populated
        if (issue.getFromNode() == null || issue.getToNode() == null) {
            String[] edge = routeOptimizer.findNearestRoadEdge(issue.getLatitude(), issue.getLongitude());
            if (edge != null && edge.length == 2) {
                issue.setFromNode(edge[0]);
                issue.setToNode(edge[1]);
            }
        }
        roadIssueRepo.save(issue);

        // Check if active restriction already exists for this issue
        Optional<RoadRestriction> existingRest = roadRestrictionRepo.findByRoadIssueIdAndActiveTrue(issueId);
        RoadRestriction restriction;
        if (existingRest.isPresent()) {
            restriction = existingRest.get();
        } else {
            String reason = issue.getIssueType() + " at " + issue.getLocationName() + " (Issue #" + issue.getId() + ")";
            restriction = new RoadRestriction(issue.getId(), issue.getFromNode(), issue.getToNode(), reason, adminUsername);
            restriction = roadRestrictionRepo.save(restriction);
        }

        auditService.record("ROAD_RESTRICTION_ACTIVATED", "Admin " + adminUsername + " verified issue #" + issue.getId() + " and activated restriction on road " + issue.getFromNode() + "-" + issue.getToNode(), adminUsername, issue.getId());

        // Recalculate affected active routes (status PLANNED or IN_PROGRESS)
        List<RoutePlan> activeRoutes = routePlanRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> "PLANNED".equalsIgnoreCase(r.getStatus()) || "IN_PROGRESS".equalsIgnoreCase(r.getStatus()))
                .toList();

        List<Map<String, Object>> recalculationResults = new ArrayList<>();
        for (RoutePlan rp : activeRoutes) {
            RoutePlan recalculated = routeOptimizer.recalculateRouteForRoadIssue(rp.getId(), issue, adminUsername);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("routeId", recalculated.getId());
            m.put("routeName", recalculated.getName());
            m.put("truckId", recalculated.getTruckId());
            m.put("revisionNumber", recalculated.getRevisionNumber());
            m.put("newDistanceKm", recalculated.getTotalDistanceKm());
            m.put("newDurationMin", recalculated.getEstimatedDurationMin());
            m.put("status", recalculated.getStatus());
            m.put("recalculationReason", recalculated.getRecalculationReason());
            recalculationResults.add(m);
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("success", true);
        resp.put("issue", issue);
        resp.put("restriction", restriction);
        resp.put("recalculatedRoutesCount", recalculationResults.size());
        resp.put("recalculatedRoutes", recalculationResults);
        resp.put("message", "Road restriction activated on " + issue.getFromNode() + "-" + issue.getToNode() + ". " + recalculationResults.size() + " active route(s) checked and recalculated.");
        return resp;
    }

    @Transactional
    public Map<String, Object> resolveIssue(Long issueId, String adminUsername, String adminNotes) {
        RoadIssue issue = roadIssueRepo.findById(issueId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Road issue #" + issueId + " not found."));

        issue.setStatus("RESOLVED");
        issue.setResolvedAt(LocalDateTime.now());
        if (adminNotes != null && !adminNotes.isBlank()) {
            issue.setAdminNotes(adminNotes.trim());
        }
        roadIssueRepo.save(issue);

        // Deactivate associated road restriction
        List<RoadRestriction> restrictions = roadRestrictionRepo.findByRoadIssueId(issueId);
        for (RoadRestriction rr : restrictions) {
            if (rr.isActive()) {
                rr.setActive(false);
                rr.setDeactivatedAt(LocalDateTime.now());
                roadRestrictionRepo.save(rr);
            }
        }

        auditService.record("ROAD_ISSUE_RESOLVED", "Admin " + adminUsername + " resolved road issue #" + issue.getId() + " and reopened road " + issue.getFromNode() + "-" + issue.getToNode(), adminUsername, issue.getId());

        return Map.of(
                "success", true,
                "issue", issue,
                "message", "Road issue #" + issueId + " resolved and road restriction lifted."
        );
    }
}
