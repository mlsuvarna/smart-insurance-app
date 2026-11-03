package com.smartinsurance.dto;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {
    private PaymentDtos() { }

    public record AdminPaymentResponse(String reference, String policyNumber, String customerName,
                                       String customerEmail, BigDecimal amount, String status,
                                       Instant createdAt, Instant updatedAt, Instant processedAt,
                                       Instant refundedAt, String processingMode) { }
}
