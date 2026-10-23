package com.smartinsurance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    @Column(name = "resource_type", nullable = false, length = 100)
    private String resourceType;
    @Column(name = "resource_id", nullable = false, length = 80)
    private String resourceId;
    @Column(name = "correlation_id", length = 80)
    private String correlationId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    protected AuditLog() { }
    public AuditLog(AppUser actor, String eventType, String resourceType, String resourceId, String correlationId) {
        this.actor = actor; this.eventType = eventType; this.resourceType = resourceType;
        this.resourceId = resourceId; this.correlationId = correlationId;
    }
    public String getEventType() { return eventType; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public String getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Long getActorId() { return actor == null ? null : actor.getId(); }
}
