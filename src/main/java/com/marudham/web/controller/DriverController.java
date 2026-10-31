package com.marudham.web.controller;

import com.marudham.web.model.*;
import com.marudham.web.repository.*;
import com.marudham.web.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/driver")
public class DriverController {
    private final TruckRepository trucks;
    private final RoutePlanRepository routes;
    private final RouteStopRepository stops;
    private final CollectionRequestRepository requests;
    private final CollectionPointRepository points;
    private final GpsHistoryRepository gpsHistory;
    private final AuditService auditService;

    public DriverController(TruckRepository trucks, RoutePlanRepository routes,
                            RouteStopRepository stops, CollectionRequestRepository requests,
                            CollectionPointRepository points, GpsHistoryRepository gpsHistory,
                            AuditService auditService) {
        this.trucks = trucks;
        this.routes = routes;
        this.stops = stops;
        this.requests = requests;
        this.points = points;
        this.gpsHistory = gpsHistory;
        this.auditService = auditService;
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

    private Truck getDriverTruck(HttpServletRequest req) {
        String username = getUsername(req);
        if (username == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Driver must be signed in.");
        }

        // 1. Try to find truck assigned to this driver
        Optional<Truck> assigned = trucks.findByAssignedDriverUsername(username);
        if (assigned.isPresent()) return assigned.get();

        // 2. If no truck is explicitly assigned, check if there's any active truck or fallback to first truck
        List<Truck> all = trucks.findAll();
        if (!all.isEmpty()) {
            return all.get(0);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No vehicle available for driver " + username);
    }

    @GetMapping("/my-assignment")
    public Map<String, Object> getMyAssignment(HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("truck", truck);

        Optional<RoutePlan> activeRoute = routes.findFirstByTruckIdAndStatusInOrderByCreatedAtDesc(
                truck.getId(), List.of("PLANNED", "IN_PROGRESS"));

        if (activeRoute.isPresent()) {
            RoutePlan plan = activeRoute.get();
            out.put("route", plan);
            List<RouteStop> stopList = stops.findByRoutePlanIdOrderByStopOrderAsc(plan.getId());
            out.put("stops", stopList);

            RouteStop nextStop = stopList.stream()
                    .filter(s -> "PENDING".equals(s.getStatus()) || "IN_PROGRESS".equals(s.getStatus()))
                    .findFirst()
                    .orElse(null);
            out.put("nextStop", nextStop);

            long completedCount = stopList.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
            out.put("completedStopsCount", completedCount);
            out.put("totalStopsCount", stopList.size());
            out.put("remainingWasteKg", stopList.stream()
                    .filter(s -> !"COMPLETED".equals(s.getStatus()))
                    .mapToDouble(RouteStop::getExpectedWasteKg)
                    .sum());
        } else {
            out.put("route", null);
            out.put("stops", List.of());
            out.put("nextStop", null);
            out.put("completedStopsCount", 0);
            out.put("totalStopsCount", 0);
        }

        return out;
    }

    @PostMapping("/report-location")
    @Transactional
    public Map<String, Object> reportLocation(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        Double lat = parseCoord(body.get("lat") != null ? body.get("lat") : body.get("latitude"));
        Double lng = parseCoord(body.get("lng") != null ? body.get("lng") : body.get("longitude"));
        String source = String.valueOf(body.getOrDefault("source", "DEVICE_GPS")).trim().toUpperCase();

        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required.");
        }
        if (!Set.of("DEVICE_GPS", "MANUAL_UPDATE", "SIMULATED_DEMO").contains(source)) {
            source = "DEVICE_GPS";
        }

        truck.setLatitude(lat);
        truck.setLongitude(lng);
        truck.setLastGpsUpdate(LocalDateTime.now());
        truck.setLastLocationSource(source);
        truck.setUpdatedAt(LocalDateTime.now());
        trucks.save(truck);

        gpsHistory.save(new GpsHistory(truck.getId(), lat, lng, source));

        return Map.of("success", true, "timestamp", LocalDateTime.now().toString(), "source", source);
    }

    @PostMapping("/start-route")
    @Transactional
    public RoutePlan startRoute(HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        RoutePlan plan = routes.findFirstByTruckIdAndStatusInOrderByCreatedAtDesc(truck.getId(), List.of("PLANNED"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No planned route available to start."));

        plan.setStatus("IN_PROGRESS");
        plan.setStartedAt(LocalDateTime.now());
        truck.setStatus("IN_USE");
        trucks.save(truck);

        RoutePlan saved = routes.save(plan);
        auditService.record("DRIVER_START_ROUTE", "Driver started route #" + plan.getId(), getUsername(req), plan.getId());
        return saved;
    }

    @PostMapping("/stop/{stopId}/collect")
    @Transactional
    public Map<String, Object> markStopCollected(@PathVariable Long stopId,
                                                 @RequestBody(required = false) Map<String, Object> body,
                                                 HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        RouteStop stop = stops.findById(stopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stop #" + stopId + " not found."));

        if (!stop.getRoutePlan().getTruckId().equals(truck.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Stop #" + stopId + " belongs to vehicle " + stop.getRoutePlan().getTruckId() + ", not your vehicle " + truck.getId());
        }

        if ("COMPLETED".equals(stop.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stop #" + stopId + " is already marked completed.");
        }

        double collectedKg = stop.getExpectedWasteKg();
        if (body != null && body.containsKey("actualKg")) {
            try {
                double parsed = Double.parseDouble(String.valueOf(body.get("actualKg")));
                if (parsed > 0) collectedKg = parsed;
            } catch (Exception ignored) {}
        }

        stop.setActualCollectedKg(collectedKg);
        stop.setStatus("COMPLETED");
        stop.setCompletedAt(LocalDateTime.now());
        stops.save(stop);

        // Update corresponding request
        if (stop.getRequestId() != null) {
            requests.findById(stop.getRequestId()).ifPresent(r -> {
                r.setStatus("COMPLETED");
                r.setActualCollectedKg(stop.getActualCollectedKg());
                r.setCompletedAt(LocalDateTime.now());
                if (body != null && body.containsKey("notes")) {
                    r.setCompletionNotes(String.valueOf(body.get("notes")));
                }
                requests.save(r);
            });
        }

        // Deduct from point waste
        points.findById(stop.getPointId()).ifPresent(p -> {
            p.setWasteKg(Math.max(0.0, p.getWasteKg() - stop.getExpectedWasteKg()));
            p.touch();
            points.save(p);
        });

        // Check if route is now completed
        RoutePlan plan = stop.getRoutePlan();
        List<RouteStop> allStops = stops.findByRoutePlanIdOrderByStopOrderAsc(plan.getId());
        boolean allDone = allStops.stream().allMatch(s -> "COMPLETED".equals(s.getStatus()) || "SKIPPED".equals(s.getStatus()));
        boolean routeFinished = false;

        if (allDone) {
            plan.setStatus("COMPLETED");
            plan.setCompletedAt(LocalDateTime.now());
            routes.save(plan);

            truck.setAssignedLoadKg(0.0);
            truck.setCurrentRouteId(null);
            truck.setStatus("ACTIVE");
            trucks.save(truck);
            routeFinished = true;
            auditService.record("DRIVER_ROUTE_COMPLETED", "Route #" + plan.getId() + " fully completed by driver", getUsername(req), plan.getId());
        }

        auditService.record("DRIVER_COLLECT_STOP", "Collected " + collectedKg + " kg at stop #" + stop.getStopOrder() + " (" + stop.getPointName() + ")", getUsername(req), stopId);

        return Map.of(
                "success", true,
                "stopId", stopId,
                "actualKg", collectedKg,
                "routeCompleted", routeFinished,
                "message", routeFinished ? "All stops completed! Route finished successfully." : "Stop marked as completed."
        );
    }

    @PostMapping("/breakdown")
    @Transactional
    public Map<String, Object> reportBreakdown(HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        truck.setStatus("BREAKDOWN");
        truck.setUpdatedAt(LocalDateTime.now());

        if (truck.getCurrentRouteId() != null) {
            routes.findById(truck.getCurrentRouteId()).ifPresent(r -> {
                r.setStatus("CANCELLED");
                routes.save(r);
            });
            requests.findAllByAssignedRouteId(truck.getCurrentRouteId()).forEach(r -> {
                if (!"COMPLETED".equals(r.getStatus())) {
                    r.setStatus("PENDING");
                    r.setAssignedRouteId(null);
                    requests.save(r);
                }
            });
            truck.setCurrentRouteId(null);
            truck.setAssignedLoadKg(0.0);
        }
        trucks.save(truck);

        auditService.record("DRIVER_REPORT_BREAKDOWN", "Driver reported vehicle breakdown on " + truck.getId(), getUsername(req), null);
        return Map.of("success", true, "status", "BREAKDOWN", "message", "Breakdown reported. Dispatcher has been notified.");
    }

    @PostMapping("/repair")
    public Map<String, Object> returnToService(HttpServletRequest req) {
        Truck truck = getDriverTruck(req);
        truck.setStatus("ACTIVE");
        truck.setUpdatedAt(LocalDateTime.now());
        trucks.save(truck);
        auditService.record("DRIVER_REPORT_REPAIR", "Driver returned vehicle " + truck.getId() + " to service", getUsername(req), null);
        return Map.of("success", true, "status", "ACTIVE", "message", "Vehicle marked ACTIVE and ready for dispatch.");
    }

    private Double parseCoord(Object val) {
        if (val == null) return null;
        try {
            double d = Double.parseDouble(String.valueOf(val));
            return Double.isFinite(d) ? d : null;
        } catch (Exception e) {
            return null;
        }
    }
}
