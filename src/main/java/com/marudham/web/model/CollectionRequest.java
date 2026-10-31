package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collection_requests")
public class CollectionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pointId;

    @Column(nullable = false)
    private String pointName;

    @Column(nullable = false)
    private String priority; // HIGH, MEDIUM, LOW

    @Column(nullable = false)
    private String status = "PENDING"; // PENDING, ASSIGNED, IN_PROGRESS, COMPLETED, CANCELLED

    @Column(nullable = false)
    private double wasteKg;

    private Double actualCollectedKg;

    @Column(length = 50)
    private String wasteType = "GENERAL"; // ORGANIC, RECYCLABLE, HAZARDOUS, GENERAL

    @Column(length = 50)
    private String username; // The citizen who created it

    @Column(length = 255)
    private String priorityReason;

    @Column(length = 500)
    private String completionNotes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    private Long assignedRouteId;

    public CollectionRequest() {}

    public CollectionRequest(String pointId, String pointName, String priority, double wasteKg) {
        this.pointId = pointId;
        this.pointName = pointName;
        this.priority = priority;
        this.wasteKg = wasteKg;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
        this.wasteType = "GENERAL";
    }

    public CollectionRequest(String pointId, String pointName, String priority, double wasteKg, String wasteType, String username) {
        this.pointId = pointId;
        this.pointName = pointName;
        this.priority = priority;
        this.wasteKg = wasteKg;
        this.wasteType = (wasteType == null || wasteType.isBlank()) ? "GENERAL" : wasteType;
        this.username = username;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getPointId() { return pointId; }
    public void setPointId(String pointId) { this.pointId = pointId; }
    public String getPointName() { return pointName; }
    public void setPointName(String pointName) { this.pointName = pointName; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getWasteKg() { return wasteKg; }
    public void setWasteKg(double wasteKg) { this.wasteKg = wasteKg; }
    public Double getActualCollectedKg() { return actualCollectedKg; }
    public void setActualCollectedKg(Double actualCollectedKg) { this.actualCollectedKg = actualCollectedKg; }
    public String getWasteType() { return wasteType; }
    public void setWasteType(String wasteType) { this.wasteType = wasteType; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPriorityReason() { return priorityReason; }
    public void setPriorityReason(String priorityReason) { this.priorityReason = priorityReason; }
    public String getCompletionNotes() { return completionNotes; }
    public void setCompletionNotes(String completionNotes) { this.completionNotes = completionNotes; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public Long getAssignedRouteId() { return assignedRouteId; }
    public void setAssignedRouteId(Long assignedRouteId) { this.assignedRouteId = assignedRouteId; }
}
