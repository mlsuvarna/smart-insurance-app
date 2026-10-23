package com.smartinsurance.entity;

import com.smartinsurance.enums.PolicyStatus;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "policies")
public class Policy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "policy_number", nullable = false, unique = true, length = 40)
    private String policyNumber;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id")
    private AppUser customer;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id")
    private InsuranceProduct product;
    @Column(name = "coverage_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal coverageAmount;
    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PolicyStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Policy() { }
    public Policy(String policyNumber, AppUser customer, InsuranceProduct product, LocalDate startDate, LocalDate endDate) {
        this.policyNumber = policyNumber;
        this.customer = customer;
        this.product = product;
        this.coverageAmount = product.getCoverageLimit();
        this.premiumAmount = product.getAnnualPremium();
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = PolicyStatus.PENDING_PAYMENT;
    }
    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }
    @PreUpdate
    private void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public AppUser getCustomer() { return customer; }
    public InsuranceProduct getProduct() { return product; }
    public BigDecimal getCoverageAmount() { return coverageAmount; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public PolicyStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setStatus(PolicyStatus status) { this.status = status; }
}
