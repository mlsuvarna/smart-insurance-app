package com.smartinsurance.dto;

import com.smartinsurance.enums.PolicyStatus;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

public final class PolicyDtos {
    private PolicyDtos() { }
    public record PurchaseRequest(@NotNull Long productId) { }
    public record PolicyResponse(Long id, String policyNumber, Long productId, String productName,
                                 BigDecimal coverageAmount, BigDecimal premiumAmount,
                                 LocalDate startDate, LocalDate endDate, PolicyStatus status,
                                 Instant createdAt, Instant updatedAt) { }
}
