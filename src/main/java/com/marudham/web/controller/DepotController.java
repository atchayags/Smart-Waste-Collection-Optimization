package com.marudham.web.controller;

import com.marudham.web.model.Depot;
import com.marudham.web.repository.DepotRepository;
import com.marudham.web.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/depot")
public class DepotController {
    private final DepotRepository depots;
    private final AuditService auditService;

    public DepotController(DepotRepository depots, AuditService auditService) {
        this.depots = depots;
        this.auditService = auditService;
    }

    @GetMapping
    public Depot getActiveDepot() {
        return depots.findFirstByActiveTrueOrderByIdAsc()
                .orElseGet(() -> depots.save(new Depot("Marudham Central Depot", "Central Municipal Yard, Marudham", 8.7960, 78.1330)));
    }

    @PutMapping
    public Depot updateDepot(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        String name = String.valueOf(body.getOrDefault("name", "")).trim();
        String address = String.valueOf(body.getOrDefault("address", "")).trim();
        Double lat = parseCoord(body.get("latitude"));
        Double lng = parseCoord(body.get("longitude"));

        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depot name cannot be empty.");
        }
        if (lat == null || lat < -90 || lat > 90 || lng == null || lng < -180 || lng > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valid latitude (-90 to 90) and longitude (-180 to 180) are required.");
        }

        Depot depot = getActiveDepot();
        depot.setName(name);
        depot.setAddress(address.isEmpty() ? depot.getAddress() : address);
        depot.setLatitude(lat);
        depot.setLongitude(lng);
        Depot saved = depots.save(depot);

        String user = currentUser(req);
        auditService.record("DEPOT_UPDATE", "Updated depot to " + name + " (" + lat + ", " + lng + ")", user, saved.getId());

        return saved;
    }

    private Double parseCoord(Object val) {
        if (val == null) return null;
        try {
            return Double.parseDouble(String.valueOf(val));
        } catch (Exception e) {
            return null;
        }
    }

    private String currentUser(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return "anonymous";
        Object u = s.getAttribute("auth.admin");
        if (u != null) return String.valueOf(u);
        u = s.getAttribute("auth.user");
        if (u != null) return String.valueOf(u);
        return "system";
    }
}
