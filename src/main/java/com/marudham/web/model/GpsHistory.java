package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "gps_history", indexes = {
    @Index(name = "idx_gps_truck_time", columnList = "truckId, recordedAt")
})
public class GpsHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String truckId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false, length = 30)
    private String locationSource = "DEVICE_GPS"; // DEVICE_GPS, MANUAL_UPDATE, SIMULATED_DEMO

    @Column(nullable = false)
    private LocalDateTime recordedAt = LocalDateTime.now();

    public GpsHistory() {}

    public GpsHistory(String truckId, double latitude, double longitude, String locationSource) {
        this.truckId = truckId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationSource = locationSource;
        this.recordedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTruckId() { return truckId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getLocationSource() { return locationSource; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
}
