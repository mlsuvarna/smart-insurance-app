package com.smartinsurance.service;

import com.smartinsurance.enums.DocumentScanStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scanner.mode", havingValue = "development", matchIfMissing = true)
@org.springframework.context.annotation.Profile("!prod")
public class DevelopmentDocumentScanner implements DocumentScanner {
    @Override
    public ScanResult scan(byte[] content) {
        return new ScanResult(DocumentScanStatus.FAILED, "DEVELOPMENT_ONLY_NO_MALWARE_SCAN");
    }
}
