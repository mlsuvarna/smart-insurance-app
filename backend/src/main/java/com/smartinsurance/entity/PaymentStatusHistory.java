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
import java.time.Instant;

@Entity
@Table(name = "payment_status_history")
public class PaymentStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "payment_id")
    private Payment payment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 20)
    private PaymentStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false, length = 20)
    private PaymentStatus newStatus;
    @Column(name = "processor", nullable = false, length = 30)
    private String processor;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PaymentStatusHistory() { }
    public PaymentStatusHistory(Payment payment, AppUser actor, PaymentStatus previousStatus,
                                PaymentStatus newStatus, String processor) {
        this.payment = payment;
        this.actor = actor;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.processor = processor;
    }
    public PaymentStatus getPreviousStatus() { return previousStatus; }
    public PaymentStatus getNewStatus() { return newStatus; }
    public String getProcessor() { return processor; }
    public Instant getCreatedAt() { return createdAt; }
}
