package com.smartinsurance.entity;

import com.smartinsurance.enums.DocumentScanStatus;
import com.smartinsurance.service.DocumentScanner;
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
@Table(name = "claim_documents")
public class ClaimDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "claim_id")
    private Claim claim;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "uploader_id")
    private AppUser uploader;
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;
    @Column(name = "object_key", nullable = false, unique = true, length = 512)
    private String objectKey;
    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Enumerated(EnumType.STRING) @Column(name = "scan_status", nullable = false, length = 20)
    private DocumentScanStatus scanStatus = DocumentScanStatus.PENDING;
    @Column(name = "scan_engine", nullable = false, length = 50)
    private String scanEngine = "PENDING";
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt = Instant.now();
    protected ClaimDocument() { }
    public ClaimDocument(Claim claim, AppUser uploader, String filename, String key,
                         String contentType, long sizeBytes) {
        this.claim = claim; this.uploader = uploader; this.originalFilename = filename;
        this.objectKey = key; this.contentType = contentType; this.sizeBytes = sizeBytes;
    }
    public Long getId() { return id; }
    public Claim getClaim() { return claim; }
    public String getOriginalFilename() { return originalFilename; }
    public String getObjectKey() { return objectKey; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public DocumentScanStatus getScanStatus() { return scanStatus; }
    public String getScanEngine() { return scanEngine; }
    public void setScanResult(DocumentScanner.ScanResult result) {
        this.scanStatus = result.status();
        this.scanEngine = result.engine();
    }
    public Instant getUploadedAt() { return uploadedAt; }
}
