package com.smartinsurance.service;

import com.smartinsurance.enums.DocumentScanStatus;

public interface DocumentScanner {
    ScanResult scan(byte[] content);
    record ScanResult(DocumentScanStatus status, String engine) { }
}
