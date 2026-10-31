package com.marudham.web.controller;

import com.marudham.web.model.AuditEvent;
import com.marudham.web.model.CollectionRequest;
import com.marudham.web.model.RoutePlan;
import com.marudham.web.repository.AuditEventRepository;
import com.marudham.web.repository.CollectionRequestRepository;
import com.marudham.web.repository.RoutePlanRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports")
public class ReportsController {

    private final CollectionRequestRepository requests;
    private final RoutePlanRepository routes;
    private final AuditEventRepository audit;

    public ReportsController(CollectionRequestRepository requests,
                             RoutePlanRepository routes,
                             AuditEventRepository audit) {
        this.requests = requests;
        this.routes = routes;
        this.audit = audit;
    }

    @GetMapping("/requests")
    public List<CollectionRequest> getRequestsReport(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String priority) {
        List<CollectionRequest> list = requests.findAllByOrderByCreatedAtDesc();
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            list = list.stream().filter(r -> r.getStatus().equalsIgnoreCase(status)).collect(Collectors.toList());
        }
        if (priority != null && !priority.isBlank() && !priority.equalsIgnoreCase("ALL")) {
            list = list.stream().filter(r -> r.getPriority().equalsIgnoreCase(priority)).collect(Collectors.toList());
        }
        return list;
    }

    @GetMapping("/requests/export-csv")
    public ResponseEntity<byte[]> exportRequestsCsv(@RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String priority) {
        List<CollectionRequest> list = getRequestsReport(status, priority);
        StringBuilder sb = new StringBuilder();
        sb.append("Request ID,Location ID,Location Name,Priority,Status,Waste Type,Estimated Waste (kg),Actual Collected (kg),Citizen Username,Priority Reason,Created At,Completed At\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (CollectionRequest r : list) {
            sb.append(r.getId()).append(",");
            sb.append(escapeCsv(r.getPointId())).append(",");
            sb.append(escapeCsv(r.getPointName())).append(",");
            sb.append(r.getPriority()).append(",");
            sb.append(r.getStatus()).append(",");
            sb.append(escapeCsv(r.getWasteType())).append(",");
            sb.append(r.getWasteKg()).append(",");
            sb.append(r.getActualCollectedKg() != null ? r.getActualCollectedKg() : "").append(",");
            sb.append(escapeCsv(r.getUsername() != null ? r.getUsername() : "")).append(",");
            sb.append(escapeCsv(r.getPriorityReason() != null ? r.getPriorityReason() : "")).append(",");
            sb.append(r.getCreatedAt() != null ? r.getCreatedAt().format(fmt) : "").append(",");
            sb.append(r.getCompletedAt() != null ? r.getCompletedAt().format(fmt) : "").append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"waste_collection_requests.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/routes")
    public List<RoutePlan> getRoutesReport(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            return routes.findByStatusOrderByCreatedAtDesc(status.toUpperCase());
        }
        return routes.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/routes/export-csv")
    public ResponseEntity<byte[]> exportRoutesCsv(@RequestParam(required = false) String status) {
        List<RoutePlan> list = getRoutesReport(status);
        StringBuilder sb = new StringBuilder();
        sb.append("Route ID,Route Name,Vehicle ID,Vehicle Name,Status,Stops Count,Total Waste (kg),Utilization (%),Distance (km),Est Duration (min),Routing Method,Created At,Completed At\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (RoutePlan r : list) {
            sb.append(r.getId()).append(",");
            sb.append(escapeCsv(r.getName())).append(",");
            sb.append(escapeCsv(r.getTruckId())).append(",");
            sb.append(escapeCsv(r.getTruckName())).append(",");
            sb.append(r.getStatus()).append(",");
            sb.append(r.getStops().size()).append(",");
            sb.append(r.getTotalWasteKg()).append(",");
            sb.append(r.getCapacityUtilizationPct()).append(",");
            sb.append(r.getTotalDistanceKm()).append(",");
            sb.append(r.getEstimatedDurationMin()).append(",");
            sb.append(escapeCsv(r.getRoutingMethod())).append(",");
            sb.append(r.getCreatedAt() != null ? r.getCreatedAt().format(fmt) : "").append(",");
            sb.append(r.getCompletedAt() != null ? r.getCompletedAt().format(fmt) : "").append("\n");
        }

        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"optimized_routes_report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    @GetMapping("/audit")
    public List<AuditEvent> getAuditTrail() {
        return audit.findTop100ByOrderByCreatedAtDesc();
    }

    private String escapeCsv(String str) {
        if (str == null) return "";
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
            return "\"" + str.replace("\"", "\"\"") + "\"";
        }
        return str;
    }
}
