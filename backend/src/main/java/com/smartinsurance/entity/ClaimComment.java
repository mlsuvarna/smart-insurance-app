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
@Table(name = "claim_comments")
public class ClaimComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id")
    private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "author_id")
    private AppUser author;
    @Column(nullable = false, length = 20)
    private String visibility;
    @Column(nullable = false, length = 4000)
    private String body;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    protected ClaimComment() { }
    public ClaimComment(Claim claim, AppUser author, String body) {
        this.claim = claim; this.author = author; this.visibility = "INTERNAL"; this.body = body;
    }
    public ClaimComment(Claim claim, AppUser author, String body, String visibility) {
        this.claim = claim; this.author = author; this.visibility = visibility; this.body = body;
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public AppUser getAuthor() { return author; }
    public String getBody() { return body; }
    public String getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
}
