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
@Table(name = "claim_reviews")
public class ClaimReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id")
    private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "officer_id")
    private AppUser officer;
    @Column(length = 30)
    private String outcome;
    @Column(length = 4000)
    private String notes;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "closed_at")
    private Instant closedAt;

    protected ClaimReview() { }
    public ClaimReview(Claim claim, AppUser officer) { this.claim = claim; this.officer = officer; }
    public void close(String outcome, String notes) {
        this.outcome = outcome;
        appendNotes(notes);
        this.closedAt = Instant.now();
        this.updatedAt = this.closedAt;
    }
    public void appendNotes(String value) {
        if (value != null && !value.isBlank()) this.notes = this.notes == null ? value : this.notes + "\n" + value;
        this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public AppUser getOfficer() { return officer; }
    public String getOutcome() { return outcome; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getClosedAt() { return closedAt; }
}
