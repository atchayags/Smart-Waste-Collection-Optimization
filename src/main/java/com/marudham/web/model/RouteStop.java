package com.marudham.web.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "route_stops")
public class RouteStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_plan_id", nullable = false)
    @JsonIgnore
    private RoutePlan routePlan;

    @Column(nullable = false)
    private int stopOrder;

    private Long requestId;

    @Column(nullable = false, length = 20)
    private String pointId;

    @Column(nullable = false, length = 100)
    private String pointName;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private double expectedWasteKg;

    private Double actualCollectedKg;

    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(nullable = false, length = 20)
    private String status = "PENDING"; // PENDING, IN_PROGRESS, COMPLETED, SKIPPED

    private LocalDateTime completedAt;

    public RouteStop() {}

    public RouteStop(int stopOrder, Long requestId, String pointId, String pointName,
                     double latitude, double longitude, double expectedWasteKg, String priority) {
        this.stopOrder = stopOrder;
        this.requestId = requestId;
        this.pointId = pointId;
        this.pointName = pointName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.expectedWasteKg = expectedWasteKg;
        this.priority = priority;
        this.status = "PENDING";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public RoutePlan getRoutePlan() { return routePlan; }
    public void setRoutePlan(RoutePlan routePlan) { this.routePlan = routePlan; }
    public int getStopOrder() { return stopOrder; }
    public void setStopOrder(int stopOrder) { this.stopOrder = stopOrder; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getPointId() { return pointId; }
    public void setPointId(String pointId) { this.pointId = pointId; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public double getExpectedWasteKg() { return expectedWasteKg; }
    public void setExpectedWasteKg(double expectedWasteKg) { this.expectedWasteKg = expectedWasteKg; }
    public Double getActualCollectedKg() { return actualCollectedKg; }
    public void setActualCollectedKg(Double actualCollectedKg) { this.actualCollectedKg = actualCollectedKg; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
