package com.smartinsurance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "insurance_products")
public class InsuranceProduct {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 40)
    private String category;
    @Column(nullable = false, length = 2000)
    private String description;
    @Column(name = "annual_premium", nullable = false, precision = 12, scale = 2)
    private BigDecimal annualPremium;
    @Column(name = "coverage_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal coverageLimit;
    @Column(nullable = false)
    private boolean active = true;

    protected InsuranceProduct() { }
    public InsuranceProduct(String name, String category, String description, BigDecimal annualPremium, BigDecimal coverageLimit) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.annualPremium = annualPremium;
        this.coverageLimit = coverageLimit;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public BigDecimal getAnnualPremium() { return annualPremium; }
    public BigDecimal getCoverageLimit() { return coverageLimit; }
    public boolean isActive() { return active; }
    public void update(String name, String category, String description, BigDecimal annualPremium, BigDecimal coverageLimit) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.annualPremium = annualPremium;
        this.coverageLimit = coverageLimit;
    }
    public void setActive(boolean active) { this.active = active; }
}
