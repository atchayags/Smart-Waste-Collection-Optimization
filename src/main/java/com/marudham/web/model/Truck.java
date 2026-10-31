package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trucks")
public class Truck {
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double capacityKg;

    @Column(nullable = false)
    private double assignedLoadKg = 0.0;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private String status = "ACTIVE"; // ACTIVE, IN_USE, BREAKDOWN, MAINTENANCE

    @Column(length = 50)
    private String assignedDriverUsername;

    private Long currentRouteId;

    private LocalDateTime lastGpsUpdate;

    @Column(length = 30)
    private String lastLocationSource; // DEVICE_GPS, MANUAL_UPDATE, SIMULATED_DEMO

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Truck() {}

    public Truck(String id, String name, double capacityKg) {
        this.id = id;
        this.name = name;
        this.capacityKg = capacityKg;
        this.assignedLoadKg = 0.0;
        this.status = "ACTIVE";
        this.updatedAt = LocalDateTime.now();
    }

    public double getUtilizationPct() {
        if (capacityKg <= 0) return 0.0;
        return Math.min(100.0, (assignedLoadKg / capacityKg) * 100.0);
    }

    public double getRemainingCapacityKg() {
        return Math.max(0.0, capacityKg - assignedLoadKg);
    }

    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public double getCapacityKg() { return capacityKg; }
    public void setCapacityKg(double v) { capacityKg = v; }
    public double getAssignedLoadKg() { return assignedLoadKg; }
    public void setAssignedLoadKg(double v) { assignedLoadKg = v; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double v) { latitude = v; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double v) { longitude = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }
    public String getAssignedDriverUsername() { return assignedDriverUsername; }
    public void setAssignedDriverUsername(String v) { assignedDriverUsername = v; }
    public Long getCurrentRouteId() { return currentRouteId; }
    public void setCurrentRouteId(Long v) { currentRouteId = v; }
    public LocalDateTime getLastGpsUpdate() { return lastGpsUpdate; }
    public void setLastGpsUpdate(LocalDateTime v) { lastGpsUpdate = v; }
    public String getLastLocationSource() { return lastLocationSource; }
    public void setLastLocationSource(String v) { lastLocationSource = v; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
