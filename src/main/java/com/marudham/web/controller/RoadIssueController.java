package com.marudham.web.controller;

import com.marudham.web.model.RoadIssue;
import com.marudham.web.model.RoadRestriction;
import com.marudham.web.model.RoutePlan;
import com.marudham.web.model.RouteRevision;
import com.marudham.web.model.Truck;
import com.marudham.web.repository.TruckRepository;
import com.marudham.web.service.RoadIssueService;
import com.marudham.web.service.RouteOptimizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class RoadIssueController {

    private final RoadIssueService roadIssueService;
    private final RouteOptimizationService routeOptimizer;
    private final TruckRepository truckRepo;

    public RoadIssueController(RoadIssueService roadIssueService,
                               RouteOptimizationService routeOptimizer,
                               TruckRepository truckRepo) {
        this.roadIssueService = roadIssueService;
        this.routeOptimizer = routeOptimizer;
        this.truckRepo = truckRepo;
    }

    private String getUsername(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null) return null;
        Object u = s.getAttribute("auth.username");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.driver");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.admin");
        return u != null ? String.valueOf(u) : null;
    }

    private String getRole(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null) return null;
        Object role = s.getAttribute("auth.role");
        if (role != null) return String.valueOf(role);
        if (s.getAttribute("auth.admin") != null) return "ADMIN";
        if (s.getAttribute("auth.driver") != null) return "DRIVER";
        if (s.getAttribute("auth.user") != null) return "USER";
        return null;
    }

    // -------------------------------------------------------------
    // 1. DRIVER REPORT ROAD ISSUE
    // -------------------------------------------------------------
    @PostMapping(value = {"/driver/road-issues", "/road-issues"})
    public RoadIssue reportRoadIssue(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String username = getUsername(req);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to report road issues.");
        }

        String issueType = String.valueOf(body.getOrDefault("issueType", "ROAD_BLOCKED")).trim();
        String description = String.valueOf(body.getOrDefault("description", "")).trim();
        Double lat = parseCoord(body.get("latitude") != null ? body.get("latitude") : body.get("lat"));
        Double lng = parseCoord(body.get("longitude") != null ? body.get("longitude") : body.get("lng"));
        String locationName = String.valueOf(body.getOrDefault("locationName", "")).trim();
        String photoUrl = body.containsKey("photoUrl") ? String.valueOf(body.get("photoUrl")).trim() : null;

        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required.");
        }

        // Determine driver's truck
        String truckId = null;
        if (body.containsKey("truckId") && body.get("truckId") != null && !String.valueOf(body.get("truckId")).isBlank()) {
            truckId = String.valueOf(body.get("truckId")).trim();
        } else {
            Optional<Truck> assigned = truckRepo.findByAssignedDriverUsername(username);
            if (assigned.isPresent()) {
                truckId = assigned.get().getId();
            } else {
                List<Truck> all = truckRepo.findAll();
                if (!all.isEmpty()) truckId = all.get(0).getId();
            }
        }

        return roadIssueService.reportIssue(issueType, description, lat, lng, locationName, username, truckId, photoUrl);
    }

    // -------------------------------------------------------------
    // 2. VIEW ROAD ISSUES
    // -------------------------------------------------------------
    @GetMapping("/road-issues")
    public List<RoadIssue> listRoadIssues(@RequestParam(required = false) String status) {
        return roadIssueService.getAllIssues(status);
    }

    @GetMapping("/road-issues/{id}")
    public RoadIssue getRoadIssue(@PathVariable Long id) {
        return roadIssueService.getIssueById(id);
    }

    @GetMapping("/driver/road-issues")
    public List<RoadIssue> getDriverRoadIssues(HttpServletRequest req) {
        String username = getUsername(req);
        if (username == null) {
            return roadIssueService.getAllIssues(null);
        }
        return roadIssueService.getAllIssues(null);
    }

    // -------------------------------------------------------------
    // 3. ADMIN VERIFY & ACTIVATE RESTRICTION (Triggers Recalculation)
    // -------------------------------------------------------------
    @PostMapping("/admin/road-issues/{id}/verify-and-activate")
    public Map<String, Object> verifyAndActivate(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 HttpServletRequest req) {
        String role = getRole(req);
        String username = getUsername(req);

        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can verify and activate road restrictions.");
        }

        String notes = (body != null && body.containsKey("notes")) ? String.valueOf(body.get("notes")) : "";
        return roadIssueService.verifyAndActivateRestriction(id, username != null ? username : "admin", notes);
    }

    // -------------------------------------------------------------
    // 4. ADMIN RESOLVE ROAD ISSUE (Deactivates Restriction)
    // -------------------------------------------------------------
    @PostMapping("/admin/road-issues/{id}/resolve")
    public Map<String, Object> resolveIssue(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            HttpServletRequest req) {
        String role = getRole(req);
        String username = getUsername(req);

        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators can resolve road issues.");
        }

        String notes = (body != null && body.containsKey("notes")) ? String.valueOf(body.get("notes")) : "";
        return roadIssueService.resolveIssue(id, username != null ? username : "admin", notes);
    }

    // -------------------------------------------------------------
    // 5. ROAD RESTRICTIONS & REVISIONS
    // -------------------------------------------------------------
    @GetMapping("/road-restrictions")
    public List<RoadRestriction> listActiveRestrictions() {
        return roadIssueService.getActiveRestrictions();
    }

    @GetMapping("/routes/{id}/revisions")
    public List<RouteRevision> getRouteRevisions(@PathVariable Long id) {
        return roadIssueService.getRouteRevisions(id);
    }

    private Double parseCoord(Object v) {
        if (v == null) return null;
        try {
            double d = Double.parseDouble(String.valueOf(v));
            return Double.isFinite(d) ? d : null;
        } catch (Exception e) {
            return null;
        }
    }
}
