package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "road_restrictions")
public class RoadRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long roadIssueId;

    @Column(nullable = false, length = 50)
    private String fromNode;

    @Column(nullable = false, length = 50)
    private String toNode;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime activatedAt = LocalDateTime.now();

    @Column(length = 50)
    private String activatedBy;

    private LocalDateTime deactivatedAt;

    public RoadRestriction() {}

    public RoadRestriction(Long roadIssueId, String fromNode, String toNode, String reason, String activatedBy) {
        this.roadIssueId = roadIssueId;
        this.fromNode = fromNode;
        this.toNode = toNode;
        this.reason = reason;
        this.activatedBy = activatedBy;
        this.active = true;
        this.activatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRoadIssueId() { return roadIssueId; }
    public void setRoadIssueId(Long roadIssueId) { this.roadIssueId = roadIssueId; }

    public String getFromNode() { return fromNode; }
    public void setFromNode(String fromNode) { this.fromNode = fromNode; }

    public String getToNode() { return toNode; }
    public void setToNode(String toNode) { this.toNode = toNode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public LocalDateTime getActivatedAt() { return activatedAt; }
    public void setActivatedAt(LocalDateTime activatedAt) { this.activatedAt = activatedAt; }

    public String getActivatedBy() { return activatedBy; }
    public void setActivatedBy(String activatedBy) { this.activatedBy = activatedBy; }

    public LocalDateTime getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(LocalDateTime deactivatedAt) { this.deactivatedAt = deactivatedAt; }
}
