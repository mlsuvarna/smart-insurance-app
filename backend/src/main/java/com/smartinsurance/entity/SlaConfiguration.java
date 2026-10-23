package com.smartinsurance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sla_configurations")
public class SlaConfiguration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "claim_type", nullable = false, unique = true, length = 80)
    private String claimType;
    @Column(name = "duration_days", nullable = false)
    private int durationDays;
    @Column(nullable = false)
    private boolean active = true;
    protected SlaConfiguration() { }
    public SlaConfiguration(String claimType, int durationDays) {
        this.claimType = claimType; this.durationDays = durationDays;
    }
    public Long getId() { return id; }
    public String getClaimType() { return claimType; }
    public int getDurationDays() { return durationDays; }
    public boolean isActive() { return active; }
    public void update(int days, boolean isActive) { this.durationDays = days; this.active = isActive; }
}
