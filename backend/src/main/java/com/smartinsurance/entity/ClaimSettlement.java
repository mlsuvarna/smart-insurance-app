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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "claim_settlements")
public class ClaimSettlement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id")
    private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @Column(name = "settlement_reference", nullable = false, unique = true, length = 100)
    private String reference;
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected ClaimSettlement() { }
    public ClaimSettlement(Claim claim, AppUser actor, String reference, BigDecimal amount) {
        this.claim = claim; this.actor = actor; this.reference = reference; this.amount = amount;
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public AppUser getActor() { return actor; }
    public String getReference() { return reference; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
