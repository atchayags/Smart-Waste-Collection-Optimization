package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "route_plans")
public class RoutePlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String truckId;

    @Column(nullable = false, length = 100)
    private String truckName;

    @Column(nullable = false)
    private double plannedStartLat;

    @Column(nullable = false)
    private double plannedStartLng;

    @Column(length = 100)
    private String startLocationName = "Planned Origin";

    @Column(nullable = false)
    private double depotLat;

    @Column(nullable = false)
    private double depotLng;

    @Column(length = 100)
    private String depotName = "Central Depot";

    @Column(nullable = false, length = 20)
    private String status = "PLANNED"; // PLANNED, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(nullable = false)
    private double totalDistanceKm;

    @Column(nullable = false)
    private double estimatedDurationMin;

    @Column(nullable = false)
    private double totalWasteKg;

    @Column(nullable = false)
    private double capacityUtilizationPct;

    @Column(nullable = false, length = 50)
    private String routingMethod = "ROAD_NETWORK"; // ORS_ROAD, INTERNAL_DIJKSTRA, ROAD_OSRM_PROXY, HAVERSINE_FALLBACK

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String routeGeometryJson; // GeoJSON line string [[lng,lat],...] or [[lat,lng],...]

    @Column(nullable = false)
    private int revisionNumber = 1;

    private LocalDateTime lastRecalculatedAt;

    @Column(length = 255)
    private String recalculationReason;

    private Long triggeredByRoadIssueId;

    @Column(length = 50)
    private String assignedDriverUsername;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String segmentDetailsJson; // JSON array of segments [{segmentIndex, fromName, toName, distanceKm, drivingTimeMin, collectionTimeMin, totalTimeMin}]

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "routePlan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("stopOrder ASC")
    private List<RouteStop> stops = new ArrayList<>();

    public RoutePlan() {}

    public RoutePlan(String name, String truckId, String truckName, double startLat, double startLng,
                     double depotLat, double depotLng, double distanceKm, double durationMin,
                     double wasteKg, double utilPct, String method, String geometryJson) {
        this.name = name;
        this.truckId = truckId;
        this.truckName = truckName;
        this.plannedStartLat = startLat;
        this.plannedStartLng = startLng;
        this.depotLat = depotLat;
        this.depotLng = depotLng;
        this.totalDistanceKm = distanceKm;
        this.estimatedDurationMin = durationMin;
        this.totalWasteKg = wasteKg;
        this.capacityUtilizationPct = utilPct;
        this.routingMethod = method;
        this.routeGeometryJson = geometryJson;
        this.status = "PLANNED";
        this.createdAt = LocalDateTime.now();
    }

    public void addStop(RouteStop stop) {
        stops.add(stop);
        stop.setRoutePlan(this);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTruckId() { return truckId; }
    public void setTruckId(String truckId) { this.truckId = truckId; }
    public String getTruckName() { return truckName; }
    public void setTruckName(String truckName) { this.truckName = truckName; }
    public double getPlannedStartLat() { return plannedStartLat; }
    public void setPlannedStartLat(double plannedStartLat) { this.plannedStartLat = plannedStartLat; }
    public double getPlannedStartLng() { return plannedStartLng; }
    public void setPlannedStartLng(double plannedStartLng) { this.plannedStartLng = plannedStartLng; }
    public String getStartLocationName() { return startLocationName; }
    public void setStartLocationName(String startLocationName) { this.startLocationName = startLocationName; }
    public double getDepotLat() { return depotLat; }
    public void setDepotLat(double depotLat) { this.depotLat = depotLat; }
    public double getDepotLng() { return depotLng; }
    public void setDepotLng(double depotLng) { this.depotLng = depotLng; }

    // Compatibility getters for frontend and driver map
    public double getDepotLatitude() { return depotLat; }
    public double getDepotLongitude() { return depotLng; }
    public double getPlannedStartLatitude() { return plannedStartLat; }
    public double getPlannedStartLongitude() { return plannedStartLng; }
    public String getDepotName() { return depotName; }
    public void setDepotName(String depotName) { this.depotName = depotName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }
    public double getEstimatedDurationMin() { return estimatedDurationMin; }
    public void setEstimatedDurationMin(double estimatedDurationMin) { this.estimatedDurationMin = estimatedDurationMin; }
    public double getTotalWasteKg() { return totalWasteKg; }
    public void setTotalWasteKg(double totalWasteKg) { this.totalWasteKg = totalWasteKg; }
    public double getCapacityUtilizationPct() { return capacityUtilizationPct; }
    public void setCapacityUtilizationPct(double capacityUtilizationPct) { this.capacityUtilizationPct = capacityUtilizationPct; }
    public String getRoutingMethod() { return routingMethod; }
    public void setRoutingMethod(String routingMethod) { this.routingMethod = routingMethod; }
    public String getRouteGeometryJson() { return routeGeometryJson; }
    public void setRouteGeometryJson(String routeGeometryJson) { this.routeGeometryJson = routeGeometryJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public List<RouteStop> getStops() { return stops; }
    public void setStops(List<RouteStop> stops) { this.stops = stops; }

    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }
    public LocalDateTime getLastRecalculatedAt() { return lastRecalculatedAt; }
    public void setLastRecalculatedAt(LocalDateTime lastRecalculatedAt) { this.lastRecalculatedAt = lastRecalculatedAt; }
    public String getRecalculationReason() { return recalculationReason; }
    public void setRecalculationReason(String recalculationReason) { this.recalculationReason = recalculationReason; }
    public Long getTriggeredByRoadIssueId() { return triggeredByRoadIssueId; }
    public void setTriggeredByRoadIssueId(Long triggeredByRoadIssueId) { this.triggeredByRoadIssueId = triggeredByRoadIssueId; }
    public String getAssignedDriverUsername() { return assignedDriverUsername; }
    public void setAssignedDriverUsername(String assignedDriverUsername) { this.assignedDriverUsername = assignedDriverUsername; }
    public String getSegmentDetailsJson() { return segmentDetailsJson; }
    public void setSegmentDetailsJson(String segmentDetailsJson) { this.segmentDetailsJson = segmentDetailsJson; }
}
