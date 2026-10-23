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
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "officer_queue_memberships",
        uniqueConstraints = @UniqueConstraint(columnNames = {"queue_id", "officer_id"}))
public class OfficerQueueMembership {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "queue_id")
    private ClaimQueue queue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "officer_id")
    private AppUser officer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "assigned_by")
    private AppUser assignedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected OfficerQueueMembership() { }
    public OfficerQueueMembership(ClaimQueue queue, AppUser officer, AppUser assignedBy) {
        this.queue = queue;
        this.officer = officer;
        this.assignedBy = assignedBy;
    }
    public ClaimQueue getQueue() { return queue; }
    public AppUser getOfficer() { return officer; }
    public AppUser getAssignedBy() { return assignedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
