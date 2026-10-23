package com.smartinsurance.dto;

import com.smartinsurance.enums.ClaimStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ClaimDtos {
    private ClaimDtos() { }
    public record SubmitRequest(@NotNull Long policyId, @NotBlank @Size(max = 80) String claimType,
                                @NotNull LocalDate incidentDate, @NotBlank @Size(max = 4000) String description,
                                @NotNull @DecimalMin("0.01") BigDecimal claimedAmount) { }
    public record ActionRequest(@Size(max = 2000) String remarks, @DecimalMin("0.00") BigDecimal amount) { }
    public record ClaimResponse(Long id, String claimNumber, Long customerId, String customerName, Long policyId,
                                String claimType, LocalDate incidentDate,
                                String description, BigDecimal claimedAmount, BigDecimal policyCoverage,
                                BigDecimal approvedAmount,
                                BigDecimal settlementAmount, ClaimStatus status, Long assignedOfficerId,
                                String queueCode, String queueName, Instant slaDueAt, boolean overdue, Instant createdAt) { }
    public record HistoryResponse(String action, String actor, String actorRole, ClaimStatus from, ClaimStatus to,
                                  java.math.BigDecimal previousAmount, java.math.BigDecimal newAmount,
                                  Long previousOfficerId, Long newOfficerId, String remarks, Instant createdAt) { }
}
