package com.smartinsurance.controller;

import com.smartinsurance.dto.DocumentDtos.DocumentResponse;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.DocumentService;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/claims/{claimId}/documents")
public class DocumentController {
    private final DocumentService documents;
    public DocumentController(DocumentService documents) { this.documents = documents; }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentResponse upload(@AuthenticationPrincipal AppUser user, @PathVariable Long claimId,
                                   @RequestPart("file") @NotNull MultipartFile file) {
        return documents.upload(user, claimId, file);
    }
    @GetMapping
    public List<DocumentResponse> list(@AuthenticationPrincipal AppUser user, @PathVariable Long claimId) {
        return documents.list(user, claimId);
    }
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AppUser user, @PathVariable Long claimId,
                                           @PathVariable Long documentId) {
        DocumentService.Download download = documents.download(user, claimId, documentId);
        ContentDisposition disposition = ContentDisposition.attachment().filename(download.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(download.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff").body(download.content());
    }
}
