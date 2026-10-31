package com.marudham.web.controller;

import com.marudham.web.model.CollectionPoint;
import com.marudham.web.model.CollectionRequest;
import com.marudham.web.repository.AppUserRepository;
import com.marudham.web.repository.CollectionPointRepository;
import com.marudham.web.repository.CollectionRequestRepository;
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
public class ApiController {
    private final CollectionPointRepository points;
    private final CollectionRequestRepository requests;
    private final AppUserRepository users;
    private final AuditService auditService;

    public ApiController(CollectionPointRepository points, CollectionRequestRepository requests,
                         AppUserRepository users, AuditService auditService) {
        this.points = points;
        this.requests = requests;
        this.users = users;
        this.auditService = auditService;
    }

    private String getUsername(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null) return null;
        Object u = s.getAttribute("auth.username");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.user");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.admin");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.driver");
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
    // COLLECTION POINTS
    // -------------------------------------------------------------

    @GetMapping("/points")
    public List<CollectionPoint> allPoints(@RequestParam(required = false, defaultValue = "false") boolean all) {
        if (all) {
            return points.findAllByOrderByIdAsc();
        }
        return points.findByActiveTrueOrderByNameAsc();
    }

    @PostMapping("/points")
    public CollectionPoint addPoint(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String id = String.valueOf(body.getOrDefault("id", "")).trim().toUpperCase();
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String zone = String.valueOf(body.getOrDefault("zone", "General")).trim();
        double kg = parseNumber(body.get("wasteKg"), 0.0);
        Double lat = parseCoord(body.get("latitude"));
        Double lng = parseCoord(body.get("longitude"));

        if (id.isEmpty() || name.isEmpty() || zone.isEmpty() || kg < 0 || kg > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid Point ID, name, zone, and initial waste level (0 to 5000 kg).");
        }
        if (points.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collection Point ID '" + id + "' already exists.");
        }

        CollectionPoint p = new CollectionPoint(id, name, zone, kg);
        if (lat != null && lng != null) {
            p.setLatitude(lat);
            p.setLongitude(lng);
        }
        CollectionPoint saved = points.save(p);
        auditService.record("POINT_CREATE", "Added collection point " + id + " (" + name + ")", getUsername(req), null);
        return saved;
    }

    @PutMapping("/points/{id}")
    public CollectionPoint updatePoint(@PathVariable String id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        CollectionPoint p = points.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collection point not found."));

        if (body.containsKey("name")) {
            String name = String.valueOf(body.get("name")).trim();
            if (!name.isEmpty()) p.setName(name);
        }
        if (body.containsKey("zone")) {
            String zone = String.valueOf(body.get("zone")).trim();
            if (!zone.isEmpty()) p.setZone(zone);
        }
        if (body.containsKey("wasteKg")) {
            double kg = parseNumber(body.get("wasteKg"), p.getWasteKg());
            if (kg < 0 || kg > 5000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waste must be 0–5000 kg.");
            p.setWasteKg(kg);
        }
        if (body.containsKey("latitude") && body.containsKey("longitude")) {
            Double lat = parseCoord(body.get("latitude"));
            Double lng = parseCoord(body.get("longitude"));
            if (lat != null && lng != null) {
                p.setLatitude(lat);
                p.setLongitude(lng);
            }
        }
        if (body.containsKey("active")) {
            p.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
        }

        p.touch();
        CollectionPoint saved = points.save(p);
        auditService.record("POINT_UPDATE", "Updated details for point " + id, getUsername(req), null);
        return saved;
    }

    @PutMapping("/points/{id}/waste")
    public CollectionPoint updateWaste(@PathVariable String id, @RequestBody Map<String, Object> b, HttpServletRequest req) {
        CollectionPoint p = points.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Point not found."));
        double kg = parseNumber(b.get("wasteKg"), -1);
        if (kg < 0 || kg > 5000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Waste must be between 0 and 5000 kg.");
        }
        p.setWasteKg(kg);
        p.touch();
        CollectionPoint saved = points.save(p);
        auditService.record("WASTE_LEVEL_UPDATE", "Updated waste level at " + id + " to " + kg + " kg", getUsername(req), null);
        return saved;
    }

    @PutMapping("/points/{id}/location")
    public CollectionPoint pointLocation(@PathVariable String id, @RequestBody Map<String, Double> b, HttpServletRequest req) {
        Double lat = b.get("lat"), lng = b.get("lng");
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude and longitude required.");
        }
        CollectionPoint p = points.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Point not found."));
        p.setLatitude(lat);
        p.setLongitude(lng);
        p.touch();
        CollectionPoint saved = points.save(p);
        auditService.record("POINT_LOCATION_UPDATE", "Updated coordinates for " + id + " to (" + lat + ", " + lng + ")", getUsername(req), null);
        return saved;
    }

    @DeleteMapping("/points/{id}")
    public Map<String, Object> deactivatePoint(@PathVariable String id, HttpServletRequest req) {
        CollectionPoint p = points.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Point not found."));
        p.setActive(false);
        p.touch();
        points.save(p);
        auditService.record("POINT_DEACTIVATE", "Deactivated collection point " + id, getUsername(req), null);
        return Map.of("success", true, "message", "Collection point deactivated.");
    }

    // -------------------------------------------------------------
    // COLLECTION REQUESTS
    // -------------------------------------------------------------

    @GetMapping("/requests")
    public List<CollectionRequest> allRequests(@RequestParam(required = false) String status, HttpServletRequest req) {
        String role = getRole(req);
        String username = getUsername(req);

        // Citizen portal: citizens ONLY see their own requests
        if ("USER".equals(role)) {
            if (username == null) return List.of();
            return requests.findAllByUsernameOrderByCreatedAtDesc(username);
        }

        // Admin & Driver view
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            return requests.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return requests.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/requests")
    public CollectionRequest createRequest(@RequestBody Map<String, Object> b, HttpServletRequest req) {
        String pointId = String.valueOf(b.getOrDefault("pointId", "")).trim();
        String priority = String.valueOf(b.getOrDefault("priority", "MEDIUM")).toUpperCase();
        String wasteType = String.valueOf(b.getOrDefault("wasteType", "GENERAL")).toUpperCase();
        String priorityReason = String.valueOf(b.getOrDefault("priorityReason", "")).trim();
        String username = getUsername(req);

        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(priority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority must be HIGH, MEDIUM, or LOW.");
        }
        if (!Set.of("ORGANIC", "RECYCLABLE", "HAZARDOUS", "GENERAL").contains(wasteType)) {
            wasteType = "GENERAL";
        }

        CollectionPoint p = points.findById(pointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Select a valid collection point."));
        if (!p.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This collection point is currently inactive.");
        }

        // User can pass custom estimated waste if reporting overflow, else use recorded level
        double wasteKg = p.getWasteKg();
        if (b.containsKey("wasteKg")) {
            double customKg = parseNumber(b.get("wasteKg"), -1);
            if (customKg > 0 && customKg <= 5000) {
                wasteKg = customKg;
            }
        }
        if (wasteKg <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This collection point has no waste recorded.");
        }

        // Check if an open request already exists for this point
        List<CollectionRequest> existing = requests.findByPointIdAndStatusIn(pointId, List.of("PENDING", "ASSIGNED", "IN_PROGRESS"));
        if (!existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This collection point already has an active request (#" + existing.get(0).getId() + ") in status " + existing.get(0).getStatus() + ".");
        }

        CollectionRequest cr = new CollectionRequest(p.getId(), p.getName(), priority, wasteKg, wasteType, username);
        if ("HIGH".equals(priority)) {
            cr.setPriorityReason(priorityReason.isEmpty() ? "Marked urgent by citizen/dispatcher" : priorityReason);
        }

        CollectionRequest saved = requests.save(cr);
        auditService.record("REQUEST_CREATE", "Created collection request #" + saved.getId() + " for " + p.getName() + " (" + priority + ", " + wasteKg + " kg)", username, saved.getId());
        return saved;
    }

    @PostMapping("/requests/{id}/cancel")
    public CollectionRequest cancelRequest(@PathVariable Long id, HttpServletRequest req) {
        CollectionRequest r = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));

        String role = getRole(req);
        String username = getUsername(req);

        if ("USER".equals(role) && (r.getUsername() != null && !r.getUsername().equalsIgnoreCase(username))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not authorized to cancel this request.");
        }

        if (!"PENDING".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PENDING requests can be cancelled. Current status is " + r.getStatus() + ".");
        }

        r.setStatus("CANCELLED");
        CollectionRequest saved = requests.save(r);
        auditService.record("REQUEST_CANCEL", "Cancelled request #" + id, username, id);
        return saved;
    }

    @PutMapping("/requests/{id}/priority")
    public CollectionRequest adjustPriority(@PathVariable Long id, @RequestBody Map<String, String> b, HttpServletRequest req) {
        String newPriority = b.getOrDefault("priority", "").toUpperCase();
        String reason = b.getOrDefault("reason", "").trim();

        if (!Set.of("HIGH", "MEDIUM", "LOW").contains(newPriority)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Priority must be HIGH, MEDIUM, or LOW.");
        }

        CollectionRequest r = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));

        String old = r.getPriority();
        r.setPriority(newPriority);
        r.setPriorityReason(reason.isEmpty() ? "Adjusted by administrator" : reason);
        CollectionRequest saved = requests.save(r);

        auditService.record("PRIORITY_ADJUST", "Changed request #" + id + " priority from " + old + " to " + newPriority + " (Reason: " + r.getPriorityReason() + ")", getUsername(req), id);
        return saved;
    }

    @PostMapping("/requests/{id}/complete")
    @Transactional
    public CollectionRequest complete(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body, HttpServletRequest req) {
        CollectionRequest r = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));

        if (!"PENDING".equals(r.getStatus()) && !"ASSIGNED".equals(r.getStatus()) && !"IN_PROGRESS".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot complete a request that is already " + r.getStatus() + ".");
        }

        double collectedKg = r.getWasteKg();
        if (body != null && body.containsKey("actualKg")) {
            collectedKg = parseNumber(body.get("actualKg"), r.getWasteKg());
        }

        r.setActualCollectedKg(collectedKg);
        r.setStatus("COMPLETED");
        r.setCompletedAt(LocalDateTime.now());
        if (body != null && body.containsKey("notes")) {
            r.setCompletionNotes(String.valueOf(body.get("notes")));
        }

        // Deduct collected waste from the collection point
        points.findById(r.getPointId()).ifPresent(p -> {
            p.setWasteKg(Math.max(0.0, p.getWasteKg() - r.getWasteKg()));
            p.touch();
            points.save(p);
        });

        CollectionRequest saved = requests.save(r);
        auditService.record("REQUEST_COMPLETE", "Completed collection request #" + id + " (" + collectedKg + " kg collected)", getUsername(req), id);
        return saved;
    }

    // -------------------------------------------------------------
    // USER MANAGEMENT (Part 5B.12)
    // -------------------------------------------------------------

    @GetMapping("/admin/users")
    public List<Map<String, Object>> listUsers(HttpServletRequest req) {
        String role = getRole(req);
        if (!"ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        List<com.marudham.web.model.AppUser> all = users.findAll();
        List<Map<String, Object>> out = new ArrayList<>();
        for (com.marudham.web.model.AppUser u : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("role", u.getRole());
            m.put("fullName", u.getFullName());
            m.put("createdAt", u.getCreatedAt());
            out.add(m);
        }
        return out;
    }

    @PutMapping("/admin/users/{username}/role")
    public Map<String, Object> updateUserRole(@PathVariable String username, @RequestBody Map<String, String> body, HttpServletRequest req) {
        String adminRole = getRole(req);
        if (!"ADMIN".equals(adminRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        String newRole = body.getOrDefault("role", "").toUpperCase();
        if (!Set.of("ADMIN", "DRIVER", "USER").contains(newRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid role (ADMIN, DRIVER, USER) required.");
        }
        com.marudham.web.model.AppUser u = users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        users.delete(u);
        com.marudham.web.model.AppUser updated = users.save(new com.marudham.web.model.AppUser(u.getUsername(), u.getPasswordHash(), newRole, u.getFullName()));
        auditService.record("USER_ROLE_CHANGE", "Changed user '" + username + "' role to " + newRole, getUsername(req), updated.getId());
        return Map.of("username", updated.getUsername(), "role", updated.getRole());
    }

    // -------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------

    private double parseNumber(Object v, double fallback) {
        if (v == null) return fallback;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
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
