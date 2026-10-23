package com.smartinsurance.service;

import com.smartinsurance.dto.DocumentDtos.DocumentResponse;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Claim;
import com.smartinsurance.entity.ClaimDocument;
import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.enums.DocumentScanStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.ClaimDocumentRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimHistoryRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {
    private static final long DEFAULT_LIMIT = 10L * 1024 * 1024;
    private final ClaimDocumentRepository documents;
    private final ClaimRepository claims;
    private final ClaimHistoryRepository history;
    private final AssignmentRepository assignments;
    private final OfficerQueueMembershipRepository queueMemberships;
    private final DocumentStorage storage;
    private final DocumentScanner scanner;
    @Value("${app.storage.max-file-size-mb:10}")
    private long maxSizeMb;
    @Value("${app.storage.max-documents-per-claim:10}")
    private long maxDocuments;

    public DocumentService(ClaimDocumentRepository documents, ClaimRepository claims, AssignmentRepository assignments,
                           ClaimHistoryRepository history, DocumentStorage storage, DocumentScanner scanner,
                           OfficerQueueMembershipRepository queueMemberships) {
        this.documents = documents;
        this.claims = claims;
        this.history = history;
        this.assignments = assignments;
        this.queueMemberships = queueMemberships;
        this.storage = storage;
        this.scanner = scanner;
    }

    @Transactional
    public DocumentResponse upload(AppUser actor, Long claimId, MultipartFile file) {
        Claim claim = claims.findByIdForUpdate(claimId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        authorizeClaim(actor, claim);
        boolean customerUpload = actor.getRole() == Role.CUSTOMER
                && (claim.getStatus() == ClaimStatus.SUBMITTED || claim.getStatus() == ClaimStatus.INFO_REQUESTED);
        boolean agentAssistance = actor.getRole() == Role.AGENT && claim.getStatus() == ClaimStatus.SUBMITTED
                && assignments.existsByAgentIdAndCustomerId(actor.getId(), claim.getCustomer().getId());
        if (!customerUpload && !agentAssistance) {
            throw new BusinessRuleException("Documents may only be uploaded by the owner during submission/response or by the assigned agent during submission");
        }
        if (file == null || file.isEmpty()) throw new BusinessRuleException("A non-empty file is required");
        long sizeLimit = maxSizeMb > 0 ? maxSizeMb * 1024 * 1024 : DEFAULT_LIMIT;
        if (file.getSize() > sizeLimit) throw new BusinessRuleException("Document exceeds the configured size limit");
        if (documents.countByClaimId(claimId) >= maxDocuments) throw new BusinessRuleException("Document count limit reached");
        String original = file.getOriginalFilename() == null ? "document" : file.getOriginalFilename();
        String safeName = original.replaceAll("[^A-Za-z0-9._ -]", "_");
        String extension = extension(safeName);
        String contentType = allowedContentType(extension);
        if (!contentType.equalsIgnoreCase(file.getContentType() == null ? "" : file.getContentType())) {
            throw new BusinessRuleException("File extension and declared content type do not match");
        }
        byte[] bytes;
        try { bytes = file.getBytes(); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Unable to read uploaded document", ex); }
        if (!signatureMatches(extension, bytes)) throw new BusinessRuleException("File content signature is invalid");
        if ("docx".equals(extension)) inspectDocx(bytes);
        String key = "claims/" + claimId + "/" + UUID.randomUUID();
        storage.store(key, bytes);
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCompletion(int status) {
                        if (status != org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED) {
                            storage.delete(key);
                        }
                    }
                });
        ClaimDocument document = documents.save(new ClaimDocument(claim, actor, safeName, key, contentType, bytes.length));
        document.setScanResult(scanner.scan(bytes));
        history.save(new com.smartinsurance.entity.ClaimHistory(claim, actor, "DOCUMENT_UPLOADED", claim.getStatus(),
                claim.getStatus(), "Supporting document uploaded"));
        return toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(AppUser actor, Long claimId) {
        authorizedClaim(actor, claimId);
        return documents.findByClaimIdOrderByUploadedAtAsc(claimId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Download download(AppUser actor, Long claimId, Long documentId) {
        authorizedClaim(actor, claimId);
        ClaimDocument document = documents.findById(documentId)
                .filter(d -> d.getClaim().getId().equals(claimId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        if (document.getScanStatus() != DocumentScanStatus.CLEAN) {
            throw new BusinessRuleException("Document is not available until its scan status is CLEAN");
        }
        return new Download(document.getOriginalFilename(), document.getContentType(), storage.load(document.getObjectKey()));
    }

    private Claim authorizedClaim(AppUser actor, Long claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        authorizeClaim(actor, claim);
        return claim;
    }

    private void authorizeClaim(AppUser actor, Claim claim) {
        if (actor.getRole() == Role.ADMIN || claim.getCustomer().getId().equals(actor.getId())) return;
        if (actor.getRole() == Role.AGENT && assignments.existsByAgentIdAndCustomerId(actor.getId(), claim.getCustomer().getId())) return;
        if (actor.getRole() == Role.CLAIMS_OFFICER && claim.getAssignedOfficer() != null
                && claim.getAssignedOfficer().getId().equals(actor.getId())) return;
        if (actor.getRole() == Role.CLAIMS_OFFICER && claim.getAssignedOfficer() == null
                && claim.getStatus() == ClaimStatus.SUBMITTED
                && queueMemberships.existsByOfficerIdAndQueueId(actor.getId(), claim.getQueue().getId())) return;
        throw new AccessDeniedException("You cannot access this claim's documents");
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) throw new BusinessRuleException("Only PDF, JPG, JPEG, PNG, and DOCX files are allowed");
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!List.of("pdf", "jpg", "jpeg", "png", "docx").contains(extension)) {
            throw new BusinessRuleException("Only PDF, JPG, JPEG, PNG, and DOCX files are allowed");
        }
        return extension;
    }

    private String allowedContentType(String extension) {
        return switch (extension) {
            case "pdf" -> "application/pdf";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            default -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        };
    }

    private boolean signatureMatches(String extension, byte[] bytes) {
        return switch (extension) {
            case "pdf" -> bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case "jpg", "jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "docx" -> bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K' && bytes[2] == 3 && bytes[3] == 4;
            default -> false;
        };
    }

    private void inspectDocx(byte[] bytes) {
        Set<String> required = new HashSet<>(List.of("[Content_Types].xml", "word/document.xml"));
        long expanded = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 1000) throw new BusinessRuleException("DOCX archive contains too many entries");
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || List.of(name.split("/")).contains("..")) {
                    throw new BusinessRuleException("DOCX archive contains an unsafe path");
                }
                if (entry.isDirectory()) continue;
                if (entry.getCompressedSize() == 0 && entry.getSize() > 0) {
                    throw new BusinessRuleException("DOCX archive has an invalid compression ratio");
                }
                long entryExpanded = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    entryExpanded += read;
                    expanded += read;
                    if (expanded > 50L * 1024 * 1024 || entryExpanded > 20L * 1024 * 1024) {
                        throw new BusinessRuleException("DOCX archive expands beyond the permitted safety limit");
                    }
                    long compressed = entry.getCompressedSize();
                    if (compressed > 0 && entryExpanded > compressed * 100L) {
                        throw new BusinessRuleException("DOCX archive exceeds the permitted compression ratio");
                    }
                }
                required.remove(name);
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new BusinessRuleException("DOCX archive is malformed");
        }
        if (!required.isEmpty()) throw new BusinessRuleException("DOCX archive is missing required document parts");
    }

    private DocumentResponse toResponse(ClaimDocument d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getContentType(), d.getSizeBytes(),
                d.getScanStatus(), d.getScanEngine(), d.getUploadedAt());
    }

    public record Download(String filename, String contentType, byte[] content) { }
}
