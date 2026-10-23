package com.smartinsurance.entity;

import com.smartinsurance.enums.PaymentStatus;
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
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_reference", nullable = false, unique = true, length = 50)
    private String paymentReference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "policy_id")
    private Policy policy;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id")
    private AppUser customer;
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
    @Column(name = "processed_at")
    private Instant processedAt;
    @Column(name = "refunded_at")
    private Instant refundedAt;
    protected Payment() { }
    public Payment(String reference, Policy policy) {
        this.paymentReference = reference;
        this.policy = policy;
        this.customer = policy.getCustomer();
        this.amount = policy.getPremiumAmount();
        this.status = PaymentStatus.PENDING;
    }
    public void transition(PaymentStatus next, Instant now) {
        boolean allowed = status == PaymentStatus.PENDING
                && (next == PaymentStatus.SUCCESS || next == PaymentStatus.FAILED)
                || status == PaymentStatus.SUCCESS && next == PaymentStatus.REFUNDED;
        if (!allowed) throw new com.smartinsurance.exception.BusinessRuleException(
                "Invalid payment transition: " + status + " -> " + next);
        status = next;
        updatedAt = now;
        if (next == PaymentStatus.SUCCESS || next == PaymentStatus.FAILED) processedAt = now;
        if (next == PaymentStatus.REFUNDED) refundedAt = now;
    }
    public String getPaymentReference() { return paymentReference; }
    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public Policy getPolicy() { return policy; }
    public AppUser getCustomer() { return customer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getRefundedAt() { return refundedAt; }
}
