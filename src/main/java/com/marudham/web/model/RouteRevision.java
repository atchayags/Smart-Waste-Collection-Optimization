package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_revision_history")
public class RouteRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long routePlanId;

    @Column(nullable = false)
    private int revisionNumber;

    @Column(nullable = false, length = 255)
    private String reason;

    private Long roadIssueId;

    @Column(nullable = false)
    private double totalDistanceKm;

    @Column(nullable = false)
    private double estimatedDurationMin;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String routeGeometryJson;

    @Column(length = 50)
    private String routingMethod;

    @Column(nullable = false)
    private LocalDateTime revisedAt = LocalDateTime.now();

    @Column(length = 50)
    private String revisedBy;

    public RouteRevision() {}

    public RouteRevision(Long routePlanId, int revisionNumber, String reason, Long roadIssueId,
                         double totalDistanceKm, double estimatedDurationMin, String routeGeometryJson,
                         String routingMethod, String revisedBy) {
        this.routePlanId = routePlanId;
        this.revisionNumber = revisionNumber;
        this.reason = reason;
        this.roadIssueId = roadIssueId;
        this.totalDistanceKm = totalDistanceKm;
        this.estimatedDurationMin = estimatedDurationMin;
        this.routeGeometryJson = routeGeometryJson;
        this.routingMethod = routingMethod;
        this.revisedBy = revisedBy;
        this.revisedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoutePlanId() { return routePlanId; }
    public void setRoutePlanId(Long routePlanId) { this.routePlanId = routePlanId; }

    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getRoadIssueId() { return roadIssueId; }
    public void setRoadIssueId(Long roadIssueId) { this.roadIssueId = roadIssueId; }

    public double getTotalDistanceKm() { return totalDistanceKm; }
    public void setTotalDistanceKm(double totalDistanceKm) { this.totalDistanceKm = totalDistanceKm; }

    public double getEstimatedDurationMin() { return estimatedDurationMin; }
    public void setEstimatedDurationMin(double estimatedDurationMin) { this.estimatedDurationMin = estimatedDurationMin; }

    public String getRouteGeometryJson() { return routeGeometryJson; }
    public void setRouteGeometryJson(String routeGeometryJson) { this.routeGeometryJson = routeGeometryJson; }

    public String getRoutingMethod() { return routingMethod; }
    public void setRoutingMethod(String routingMethod) { this.routingMethod = routingMethod; }

    public LocalDateTime getRevisedAt() { return revisedAt; }
    public void setRevisedAt(LocalDateTime revisedAt) { this.revisedAt = revisedAt; }

    public String getRevisedBy() { return revisedBy; }
    public void setRevisedBy(String revisedBy) { this.revisedBy = revisedBy; }
}
