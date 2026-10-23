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
import java.time.Instant;

@Entity
@Table(name = "agent_assignment_history")
public class AgentAssignmentHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_id")
    private AppUser actor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "agent_id")
    private AppUser agent;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "customer_id")
    private AppUser customer;
    @Column(nullable = false, length = 20)
    private String action;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    protected AgentAssignmentHistory() { }
    public AgentAssignmentHistory(AppUser actor, AppUser agent, AppUser customer, String action) {
        this.actor = actor; this.agent = agent; this.customer = customer; this.action = action;
    }
    public AppUser getActor() { return actor; }
    public AppUser getAgent() { return agent; }
    public AppUser getCustomer() { return customer; }
    public String getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }
}
