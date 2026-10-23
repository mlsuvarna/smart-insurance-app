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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "claims")
public class Claim {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "claim_number", nullable = false, unique = true, length = 40)
    private String claimNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "policy_id")
    private Policy policy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id")
    private AppUser customer;
    @Column(name = "claim_type", nullable = false, length = 80)
    private String claimType;
    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;
    @Column(nullable = false, length = 4000)
    private String description;
    @Column(name = "claimed_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal claimedAmount;
    @Column(name = "approved_amount", precision = 14, scale = 2)
    private BigDecimal approvedAmount;
    @Column(name = "settlement_amount", precision = 14, scale = 2)
    private BigDecimal settlementAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private ClaimStatus status = ClaimStatus.SUBMITTED;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "assigned_officer_id")
    private AppUser assignedOfficer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "queue_id")
    private ClaimQueue queue;
    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;
    @Column(name = "sla_paused_at")
    private Instant slaPausedAt;
    @Column(name = "sla_paused_seconds", nullable = false)
    private long slaPausedSeconds;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Version
    private long version;

    protected Claim() { }
    public Claim(String claimNumber, Policy policy, ClaimQueue queue, String claimType, LocalDate incidentDate,
                 String description, BigDecimal claimedAmount, Instant slaDueAt) {
        this.claimNumber = claimNumber;
        this.policy = policy;
        this.customer = policy.getCustomer();
        this.queue = queue;
        this.claimType = claimType;
        this.incidentDate = incidentDate;
        this.description = description;
        this.claimedAmount = claimedAmount;
        this.slaDueAt = slaDueAt;
    }
    public Long getId() { return id; }
    public String getClaimNumber() { return claimNumber; }
    public Policy getPolicy() { return policy; }
    public AppUser getCustomer() { return customer; }
    public String getClaimType() { return claimType; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getDescription() { return description; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getApprovedAmount() { return approvedAmount; }
    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public ClaimStatus getStatus() { return status; }
    public AppUser getAssignedOfficer() { return assignedOfficer; }
    public ClaimQueue getQueue() { return queue; }
    public Instant getSlaDueAt() { return slaDueAt; }
    public Instant getSlaPausedAt() { return slaPausedAt; }
    public long getSlaPausedSeconds() { return slaPausedSeconds; }
    public Instant getEffectiveSlaDueAt() {
        long pauseSeconds = slaPausedSeconds;
        if (slaPausedAt != null) {
            pauseSeconds += Math.max(0, java.time.Duration.between(slaPausedAt, Instant.now()).getSeconds());
        }
        return slaDueAt.plusSeconds(pauseSeconds);
    }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isOverdue() { return !isClosed() && slaPausedAt == null && Instant.now().isAfter(getEffectiveSlaDueAt()); }
    public boolean isClosed() { return status == ClaimStatus.REJECTED || status == ClaimStatus.SETTLED || status == ClaimStatus.WITHDRAWN; }
    public void transition(ClaimStatus status) { this.status = status; this.updatedAt = Instant.now(); }
    public void pauseSla() { this.slaPausedAt = Instant.now(); }
    public void resumeSla() {
        if (slaPausedAt != null) {
            long pause = Math.max(0, java.time.Duration.between(slaPausedAt, Instant.now()).getSeconds());
            slaPausedSeconds += pause;
            slaPausedAt = null;
        }
    }
    public void setAssignedOfficer(AppUser assignedOfficer) { this.assignedOfficer = assignedOfficer; }
    public void approve(BigDecimal amount) { this.approvedAmount = amount; }
    public void settle(BigDecimal amount) { this.settlementAmount = amount; }
}
