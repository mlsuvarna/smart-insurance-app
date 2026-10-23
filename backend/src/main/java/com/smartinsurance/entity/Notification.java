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
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "claim_id")
    private Claim claim;
    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;
    @Column(nullable = false, length = 500)
    private String message;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    protected Notification() { }
    public Notification(AppUser user, Claim claim, String eventType, String message) {
        this.user = user; this.claim = claim; this.eventType = eventType; this.message = message;
    }
    public Long getId() { return id; }
    public Long getUserId() { return user.getId(); }
    public Claim getClaim() { return claim; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public Instant getCreatedAt() { return createdAt; }
    public void setRead(boolean read) { this.read = read; }
}
