package com.marudham.web.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(length = 50)
    private String username;

    private Long referenceId;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public AuditEvent() {}

    public AuditEvent(String eventType, String description, String username, Long referenceId) {
        this.eventType = eventType;
        this.description = description;
        this.username = username;
        this.referenceId = referenceId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEventType() { return eventType; }
    public String getDescription() { return description; }
    public String getUsername() { return username; }
    public Long getReferenceId() { return referenceId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
