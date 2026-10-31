package com.marudham.web.controller;

import com.marudham.web.model.GpsHistory;
import com.marudham.web.model.RoutePlan;
import com.marudham.web.model.Truck;
import com.marudham.web.repository.AppUserRepository;
import com.marudham.web.repository.CollectionRequestRepository;
import com.marudham.web.repository.GpsHistoryRepository;
import com.marudham.web.repository.RoutePlanRepository;
import com.marudham.web.repository.TruckRepository;
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
@RequestMapping("/api")
public class TruckController {
    private final TruckRepository trucks;
    private final GpsHistoryRepository gpsHistory;
    private final RoutePlanRepository routes;
    private final CollectionRequestRepository requests;
    private final AppUserRepository users;
    private final AuditService auditService;

    public TruckController(TruckRepository trucks, GpsHistoryRepository gpsHistory,
                           RoutePlanRepository routes, CollectionRequestRepository requests,
                           AppUserRepository users, AuditService auditService) {
        this.trucks = trucks;
        this.gpsHistory = gpsHistory;
        this.routes = routes;
        this.requests = requests;
        this.users = users;
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

    private Truck findTruck(String id) {
        return trucks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Vehicle '" + id + "' not found."));
    }

    @GetMapping("/trucks")
    public List<Map<String, Object>> allTrucks() {
        List<Truck> list = trucks.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Truck t : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("capacityKg", t.getCapacityKg());
            m.put("assignedLoadKg", t.getAssignedLoadKg());
            m.put("remainingCapacityKg", t.getRemainingCapacityKg());
            m.put("utilizationPct", Math.round(t.getUtilizationPct() * 10.0) / 10.0);
            m.put("latitude", t.getLatitude());
            m.put("longitude", t.getLongitude());
            m.put("status", t.getStatus());
            m.put("assignedDriverUsername", t.getAssignedDriverUsername());
            m.put("currentRouteId", t.getCurrentRouteId());
            m.put("lastGpsUpdate", t.getLastGpsUpdate());
            m.put("lastLocationSource", t.getLastLocationSource());
            m.put("updatedAt", t.getUpdatedAt());

            // Add age in seconds if GPS timestamp is present
            if (t.getLastGpsUpdate() != null) {
                long ageSec = java.time.Duration.between(t.getLastGpsUpdate(), LocalDateTime.now()).getSeconds();
                m.put("gpsAgeSeconds", Math.max(0, ageSec));
            } else {
                m.put("gpsAgeSeconds", null);
            }
            out.add(m);
        }
        return out;
    }

    @PostMapping("/trucks")
    public Truck addTruck(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String id = String.valueOf(body.getOrDefault("id", "")).trim().toUpperCase();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        double capacity = parseDouble(body.get("capacityKg"), 0.0);

        if (id.isEmpty() || name.isEmpty() || capacity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid vehicle registration/ID, name, and positive capacity (kg) are required.");
        }
        if (trucks.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vehicle ID '" + id + "' already exists.");
        }

        Truck t = new Truck(id, name, capacity);
        if (body.containsKey("assignedDriverUsername")) {
            String driver = String.valueOf(body.get("assignedDriverUsername")).trim();
            if (!driver.isEmpty()) t.setAssignedDriverUsername(driver);
        }

        Truck saved = trucks.save(t);
        auditService.record("TRUCK_CREATE", "Added vehicle " + id + " (" + name + ", " + capacity + " kg)", getUsername(req), null);
        return saved;
    }

    @PutMapping("/trucks/{id}")
    public Truck updateTruck(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        Truck t = findTruck(id);
        if (body.containsKey("name")) {
            String name = String.valueOf(body.get("name")).trim();
            if (!name.isEmpty()) t.setName(name);
        }
        if (body.containsKey("capacityKg")) {
            double cap = parseDouble(body.get("capacityKg"), 0.0);
            if (cap <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity must be greater than zero.");
            if (cap < t.getAssignedLoadKg()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacity cannot be less than current assigned load (" + t.getAssignedLoadKg() + " kg).");
            }
            t.setCapacityKg(cap);
        }
        if (body.containsKey("assignedDriverUsername")) {
            String driver = String.valueOf(body.get("assignedDriverUsername")).trim();
            t.setAssignedDriverUsername(driver.isEmpty() ? null : driver);
        }
        t.setUpdatedAt(LocalDateTime.now());
        Truck saved = trucks.save(t);
        auditService.record("TRUCK_UPDATE", "Updated vehicle " + id, getUsername(req), null);
        return saved;
    }

    @DeleteMapping("/trucks/{id}")
    public Map<String, Object> deleteTruck(@PathVariable String id, HttpServletRequest req) {
        Truck t = findTruck(id);
        if (t.getAssignedLoadKg() > 0 || t.getCurrentRouteId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete vehicle '" + id + "' while it has an active route assignment.");
        }
        trucks.delete(t);
        auditService.record("TRUCK_DELETE", "Deleted vehicle " + id, getUsername(req), null);
        return Map.of("success", true, "message", "Vehicle deleted successfully.");
    }

    @PutMapping("/trucks/{id}/location")
    @Transactional
    public Truck updateLocation(@PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest req) {
        Double lat = parseDoubleObj(b.get("lat") != null ? b.get("lat") : b.get("latitude"));
        Double lng = parseDoubleObj(b.get("lng") != null ? b.get("lng") : b.get("longitude"));
        String source = String.valueOf(b.getOrDefault("source", "DEVICE_GPS")).trim().toUpperCase();

        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude are required.");
        }
        if (!Set.of("DEVICE_GPS", "MANUAL_UPDATE", "SIMULATED_DEMO").contains(source)) {
            source = "DEVICE_GPS";
        }

        Truck t = findTruck(id);
        t.setLatitude(lat);
        t.setLongitude(lng);
        t.setLastGpsUpdate(LocalDateTime.now());
        t.setLastLocationSource(source);
        t.setUpdatedAt(LocalDateTime.now());
        Truck saved = trucks.save(t);

        // Record to GPS history
        gpsHistory.save(new GpsHistory(id, lat, lng, source));
        return saved;
    }

    @GetMapping("/trucks/{id}/gps-history")
    public List<GpsHistory> getGpsHistory(@PathVariable String id) {
        findTruck(id); // verify exists
        return gpsHistory.findTop50ByTruckIdOrderByRecordedAtDesc(id);
    }

    @PostMapping("/trucks/{id}/breakdown")
    @Transactional
    public Truck breakdown(@PathVariable String id, HttpServletRequest req) {
        Truck t = findTruck(id);
        t.setStatus("BREAKDOWN");
        t.setUpdatedAt(LocalDateTime.now());

        // Safe reassignment / release of pending requests from any active route
        if (t.getCurrentRouteId() != null) {
            routes.findById(t.getCurrentRouteId()).ifPresent(route -> {
                route.setStatus("CANCELLED");
                routes.save(route);
            });
            // Reset assigned requests belonging to this route back to PENDING
            requests.findAllByAssignedRouteId(t.getCurrentRouteId()).forEach(r -> {
                if (!"COMPLETED".equals(r.getStatus())) {
                    r.setStatus("PENDING");
                    r.setAssignedRouteId(null);
                    requests.save(r);
                }
            });
            t.setCurrentRouteId(null);
            t.setAssignedLoadKg(0.0);
        }

        Truck saved = trucks.save(t);
        auditService.record("TRUCK_BREAKDOWN", "Vehicle " + id + " reported BREAKDOWN. Active assignments released.", getUsername(req), null);
        return saved;
    }

    @PostMapping("/trucks/{id}/repair")
    public Truck repair(@PathVariable String id, HttpServletRequest req) {
        Truck t = findTruck(id);
        t.setStatus("ACTIVE");
        t.setUpdatedAt(LocalDateTime.now());
        Truck saved = trucks.save(t);
        auditService.record("TRUCK_REPAIR", "Vehicle " + id + " returned to ACTIVE service", getUsername(req), null);
        return saved;
    }

    @PostMapping("/trucks/{id}/assign-driver")
    public Truck assignDriver(@PathVariable String id, @RequestBody Map<String, String> b, HttpServletRequest req) {
        String driver = b.getOrDefault("username", "").trim();
        Truck t = findTruck(id);
        if (driver.isEmpty()) {
            t.setAssignedDriverUsername(null);
        } else {
            if (!users.existsByUsername(driver)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User '" + driver + "' not found.");
            }
            t.setAssignedDriverUsername(driver);
        }
        t.setUpdatedAt(LocalDateTime.now());
        Truck saved = trucks.save(t);
        auditService.record("DRIVER_ASSIGNMENT", "Assigned driver '" + driver + "' to vehicle " + id, getUsername(req), null);
        return saved;
    }

    private double parseDouble(Object v, double fallback) {
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    private Double parseDoubleObj(Object v) {
        if (v == null) return null;
        try {
            double d = Double.parseDouble(String.valueOf(v));
            return Double.isFinite(d) ? d : null;
        } catch (Exception e) {
            return null;
        }
    }
}
