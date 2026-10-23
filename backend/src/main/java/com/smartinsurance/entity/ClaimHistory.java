package com.smartinsurance.entity;

import com.smartinsurance.enums.ClaimStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "claim_history")
public class ClaimHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id")
    private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @Column(nullable = false, length = 80)
    private String action;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 30)
    private ClaimStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", length = 30)
    private ClaimStatus newStatus;
    @Column(length = 2000)
    private String remarks;
    @Column(nullable = false, length = 20)
    private String visibility = "CUSTOMER";
    @Column(name = "actor_role", nullable = false, length = 30)
    private String actorRole;
    @Column(name = "previous_amount", precision = 14, scale = 2)
    private BigDecimal previousAmount;
    @Column(name = "new_amount", precision = 14, scale = 2)
    private BigDecimal newAmount;
    @Column(name = "previous_officer_id")
    private Long previousOfficerId;
    @Column(name = "new_officer_id")
    private Long newOfficerId;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ClaimHistory() { }
    public ClaimHistory(Claim claim, AppUser actor, String action, ClaimStatus previousStatus,
                        ClaimStatus newStatus, String remarks) {
        this.claim = claim;
        this.actor = actor;
        this.action = action;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.remarks = remarks;
        this.actorRole = actor.getRole().name();
        if ("AGENT_NOTE".equals(action) || "REVIEW_NOTE".equals(action)) this.visibility = "INTERNAL";
    }
    public ClaimHistory(Claim claim, AppUser actor, String action, ClaimStatus previousStatus,
                        ClaimStatus newStatus, String remarks, BigDecimal previousAmount, BigDecimal newAmount) {
        this(claim, actor, action, previousStatus, newStatus, remarks);
        this.previousAmount = previousAmount;
        this.newAmount = newAmount;
    }
    public ClaimHistory(Claim claim, AppUser actor, String action, ClaimStatus previousStatus,
                        ClaimStatus newStatus, String remarks, Long previousOfficerId, Long newOfficerId) {
        this(claim, actor, action, previousStatus, newStatus, remarks);
        this.previousOfficerId = previousOfficerId;
        this.newOfficerId = newOfficerId;
    }
    public String getAction() { return action; }
    public AppUser getActor() { return actor; }
    public ClaimStatus getPreviousStatus() { return previousStatus; }
    public ClaimStatus getNewStatus() { return newStatus; }
    public String getRemarks() { return remarks; }
    public String getVisibility() { return visibility; }
    public String getActorRole() { return actorRole; }
    public BigDecimal getPreviousAmount() { return previousAmount; }
    public BigDecimal getNewAmount() { return newAmount; }
    public Long getPreviousOfficerId() { return previousOfficerId; }
    public Long getNewOfficerId() { return newOfficerId; }
    public Instant getCreatedAt() { return createdAt; }
}
