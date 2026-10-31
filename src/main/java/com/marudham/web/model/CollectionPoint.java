package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "collection_points")
public class CollectionPoint {
    @Id
    @Column(length = 20)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String zone;

    @Column(nullable = false)
    private double wasteKg;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public CollectionPoint() {}

    public CollectionPoint(String id, String name, String zone, double wasteKg) {
        this.id = id;
        this.name = name;
        this.zone = zone;
        this.wasteKg = wasteKg;
        this.active = true;
        this.updatedAt = LocalDateTime.now();
    }

    public CollectionPoint(String id, String name, String zone, double wasteKg, double lat, double lng) {
        this(id, name, zone, wasteKg);
        this.latitude = lat;
        this.longitude = lng;
    }

    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String v) { id = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public String getZone() { return zone; }
    public void setZone(String v) { zone = v; }
    public double getWasteKg() { return wasteKg; }
    public void setWasteKg(double v) { wasteKg = v; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double v) { latitude = v; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double v) { longitude = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime v) { updatedAt = v; }
}
