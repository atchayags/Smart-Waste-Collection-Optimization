package com.marudham.web.controller;

import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import com.marudham.web.service.AuditService;
import com.marudham.web.service.RouteOptimizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api")
public class OptimizeController {

    private static final String ORS_URL = "https://api.openrouteservice.org/optimization";

    @Value("${ors.api.key:}")
    private String orsKey;

    private final RouteOptimizationService routeOptimizer;
    private final RoutePlanRepository routePlanRepo;
    private final RouteStopRepository routeStopRepo;
    private final CollectionRequestRepository requestRepo;
    private final CollectionPointRepository pointRepo;
    private final TruckRepository truckRepo;
    private final AuditService auditService;
    private final RestClient client = RestClient.create();

    public OptimizeController(RouteOptimizationService routeOptimizer,
                              RoutePlanRepository routePlanRepo,
                              RouteStopRepository routeStopRepo,
                              CollectionRequestRepository requestRepo,
                              CollectionPointRepository pointRepo,
                              TruckRepository truckRepo,
                              AuditService auditService) {
        this.routeOptimizer = routeOptimizer;
        this.routePlanRepo = routePlanRepo;
        this.routeStopRepo = routeStopRepo;
        this.requestRepo = requestRepo;
        this.pointRepo = pointRepo;
        this.truckRepo = truckRepo;
        this.auditService = auditService;
    }

    private String getUsername(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null) return "system";
        Object u = s.getAttribute("auth.username");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.admin");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.driver");
        return u != null ? String.valueOf(u) : "system";
    }

    @PostMapping(value = {"/optimizer/preview", "/optimizer/optimize"})
    public RouteOptimizationService.OptimizationPreviewResult previewRoute(@RequestBody RouteOptimizationService.OptimizationRequest body) {
        return routeOptimizer.previewRoute(body);
    }

    @PostMapping("/optimizer/dispatch")
    public RouteOptimizationService.OptimizationResult dispatchRoute(@RequestBody RouteOptimizationService.OptimizationRequest body, HttpServletRequest req) {
        return routeOptimizer.planAndDispatch(body, getUsername(req));
    }

    @PostMapping("/optimizer/plan")
    public RouteOptimizationService.OptimizationResult planRoute(@RequestBody RouteOptimizationService.OptimizationRequest body, HttpServletRequest req) {
        return routeOptimizer.planAndDispatch(body, getUsername(req));
    }

    @GetMapping("/routes")
    public List<RoutePlan> listRoutes(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            return routePlanRepo.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return routePlanRepo.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/routes/{id}")
    public RoutePlan getRoute(@PathVariable Long id) {
        return routePlanRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route #" + id + " not found."));
    }

    @PostMapping("/routes/{id}/start")
    @Transactional
    public RoutePlan startRoute(@PathVariable Long id, HttpServletRequest req) {
        RoutePlan plan = routePlanRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route #" + id + " not found."));
        if (!"PLANNED".equals(plan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PLANNED routes can be started. Current status is " + plan.getStatus() + ".");
        }
        plan.setStatus("IN_PROGRESS");
        plan.setStartedAt(LocalDateTime.now());
        RoutePlan saved = routePlanRepo.save(plan);

        truckRepo.findById(plan.getTruckId()).ifPresent(t -> {
            t.setStatus("IN_USE");
            truckRepo.save(t);
        });

        auditService.record("ROUTE_START", "Started route #" + id + " for " + plan.getTruckName(), getUsername(req), id);
        return saved;
    }

    @PostMapping("/routes/{id}/cancel")
    @Transactional
    public RoutePlan cancelRoute(@PathVariable Long id, HttpServletRequest req) {
        RoutePlan plan = routePlanRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route #" + id + " not found."));
        if ("COMPLETED".equals(plan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot cancel a completed route.");
        }
        plan.setStatus("CANCELLED");
        RoutePlan saved = routePlanRepo.save(plan);

        // Reset associated requests to PENDING
        requestRepo.findAllByAssignedRouteId(id).forEach(r -> {
            if (!"COMPLETED".equals(r.getStatus())) {
                r.setStatus("PENDING");
                r.setAssignedRouteId(null);
                requestRepo.save(r);
            }
        });

        // Free vehicle load
        truckRepo.findById(plan.getTruckId()).ifPresent(t -> {
            t.setAssignedLoadKg(0.0);
            t.setCurrentRouteId(null);
            if ("IN_USE".equals(t.getStatus())) t.setStatus("ACTIVE");
            truckRepo.save(t);
        });

        auditService.record("ROUTE_CANCEL", "Cancelled route #" + id, getUsername(req), id);
        return saved;
    }

    @PostMapping("/routes/stops/{stopId}/complete")
    @Transactional
    public RouteStop completeStop(@PathVariable Long stopId, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest req) {
        RouteStop stop = routeStopRepo.findById(stopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route stop #" + stopId + " not found."));
        if ("COMPLETED".equals(stop.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop is already completed.");
        }

        double collectedKg = stop.getExpectedWasteKg();
        if (body != null && body.containsKey("actualKg")) {
            try {
                collectedKg = Double.parseDouble(String.valueOf(body.get("actualKg")));
            } catch (Exception ignored) {}
        }

        stop.setActualCollectedKg(collectedKg);
        stop.setStatus("COMPLETED");
        stop.setCompletedAt(LocalDateTime.now());
        RouteStop savedStop = routeStopRepo.save(stop);

        // Update corresponding request
        if (stop.getRequestId() != null) {
            requestRepo.findById(stop.getRequestId()).ifPresent(r -> {
                r.setStatus("COMPLETED");
                r.setActualCollectedKg(savedStop.getActualCollectedKg());
                r.setCompletedAt(LocalDateTime.now());
                if (body != null && body.containsKey("notes")) {
                    r.setCompletionNotes(String.valueOf(body.get("notes")));
                }
                requestRepo.save(r);
            });
        }

        // Deduct collected waste from the point
        pointRepo.findById(stop.getPointId()).ifPresent(p -> {
            p.setWasteKg(Math.max(0.0, p.getWasteKg() - stop.getExpectedWasteKg()));
            p.touch();
            pointRepo.save(p);
        });

        // Check if all stops on this route are completed
        RoutePlan plan = stop.getRoutePlan();
        if (plan != null) {
            List<RouteStop> allStops = routeStopRepo.findByRoutePlanIdOrderByStopOrderAsc(plan.getId());
            boolean allDone = allStops.stream().allMatch(s -> "COMPLETED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus()));
            if (allDone) {
                plan.setStatus("COMPLETED");
                plan.setCompletedAt(LocalDateTime.now());
                routePlanRepo.save(plan);

                truckRepo.findById(plan.getTruckId()).ifPresent(t -> {
                    t.setAssignedLoadKg(0.0);
                    t.setCurrentRouteId(null);
                    if ("IN_USE".equals(t.getStatus())) t.setStatus("ACTIVE");
                    truckRepo.save(t);
                });
                auditService.record("ROUTE_COMPLETE", "All stops completed for " + plan.getName(), getUsername(req), plan.getId());
            }
        }

        auditService.record("STOP_COMPLETE", "Completed stop #" + stop.getStopOrder() + " at " + stop.getPointName(), getUsername(req), stopId);
        return savedStop;
    }

    // -------------------------------------------------------------
    // LEGACY ORS FORWARDER (Backward compatibility)
    // -------------------------------------------------------------

    @PostMapping("/optimize")
    public ResponseEntity<String> optimizeLegacy(@RequestBody String body) {
        if (orsKey == null || orsKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"ORS_API_KEY is not configured on the server. Use /api/optimizer/plan for smart capacity routing.\"}");
        }
        try {
            String result = client.post()
                    .uri(ORS_URL)
                    .header("Authorization", orsKey.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(result);
        } catch (RestClientResponseException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(e.getResponseBodyAsString());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"error\":\"Could not reach the optimization service: " + e.getMessage() + "\"}");
        }
    }
}
