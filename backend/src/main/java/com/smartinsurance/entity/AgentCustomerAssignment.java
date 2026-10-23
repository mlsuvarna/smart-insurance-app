package com.smartinsurance.entity;

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
@Table(name = "agent_customer_assignments", uniqueConstraints =
        @UniqueConstraint(columnNames = {"agent_id", "customer_id"}))
public class AgentCustomerAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agent_id")
    private AppUser agent;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id")
    private AppUser customer;
    protected AgentCustomerAssignment() { }
    public AgentCustomerAssignment(AppUser agent, AppUser customer) { this.agent = agent; this.customer = customer; }
    public AppUser getAgent() { return agent; }
    public AppUser getCustomer() { return customer; }
}
