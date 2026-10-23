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

@Entity
@Table(name = "claim_types", uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "name"}))
public class ClaimType {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id")
    private InsuranceProduct product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "queue_id")
    private ClaimQueue queue;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    protected ClaimType() { }
    public ClaimType(InsuranceProduct product, ClaimQueue queue, String name) {
        this.product = product; this.queue = queue; this.name = name;
    }
    public Long getId() { return id; }
    public InsuranceProduct getProduct() { return product; }
    public ClaimQueue getQueue() { return queue; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public void update(InsuranceProduct product, ClaimQueue queue, String name) {
        this.product = product; this.queue = queue; this.name = name;
    }
    public void setActive(boolean active) { this.active = active; }
}
