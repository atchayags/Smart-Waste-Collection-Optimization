package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "road_issues")
public class RoadIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String issueType; // ROAD_BLOCKED, ROAD_DAMAGED, ROAD_MAINTENANCE, OTHER

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 150)
    private String locationName;

    @Column(nullable = false, length = 50)
    private String reportingDriverUsername;

    @Column(length = 20)
    private String truckId;

    @Column(nullable = false, length = 30)
    private String status = "REPORTED"; // REPORTED, VERIFIED, RESOLVED, REJECTED

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String photoUrl;

    @Column(length = 50)
    private String fromNode;

    @Column(length = 50)
    private String toNode;

    private Long affectedRouteId;

    @Column(length = 500)
    private String adminNotes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime verifiedAt;

    private LocalDateTime resolvedAt;

    @Column(length = 50)
    private String verifiedBy;

    public RoadIssue() {}

    public RoadIssue(String issueType, String description, double latitude, double longitude,
                     String locationName, String reportingDriverUsername, String truckId) {
        this.issueType = issueType;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationName = locationName;
        this.reportingDriverUsername = reportingDriverUsername;
        this.truckId = truckId;
        this.status = "REPORTED";
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getLocationName() { return locationName; }
    public void setLocationName(String locationName) { this.locationName = locationName; }

    public String getReportingDriverUsername() { return reportingDriverUsername; }
    public String getDriverUsername() { return reportingDriverUsername; }
    public void setReportingDriverUsername(String reportingDriverUsername) { this.reportingDriverUsername = reportingDriverUsername; }

    public String getTruckId() { return truckId; }
    public void setTruckId(String truckId) { this.truckId = truckId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getFromNode() { return fromNode; }
    public void setFromNode(String fromNode) { this.fromNode = fromNode; }

    public String getToNode() { return toNode; }
    public void setToNode(String toNode) { this.toNode = toNode; }

    public Long getAffectedRouteId() { return affectedRouteId; }
    public void setAffectedRouteId(Long affectedRouteId) { this.affectedRouteId = affectedRouteId; }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(String verifiedBy) { this.verifiedBy = verifiedBy; }
}
