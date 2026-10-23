package com.smartinsurance.dto;

import com.smartinsurance.enums.DocumentScanStatus;
import java.time.Instant;

public final class DocumentDtos {
    private DocumentDtos() { }
    public record DocumentResponse(Long id, String filename, String contentType, long sizeBytes,
                                   DocumentScanStatus scanStatus, String scanEngine, Instant uploadedAt) { }
}
