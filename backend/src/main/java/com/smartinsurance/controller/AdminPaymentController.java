package com.smartinsurance.controller;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Payment;
import com.smartinsurance.enums.PaymentStatus;
import com.smartinsurance.service.PaymentService;
import com.smartinsurance.util.PageRequests;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {
    private final PaymentService payments;

    public AdminPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public Page<PaymentResponse> all(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return payments.all(PageRequests.of(page, size)).map(this::toResponse);
    }

    @PostMapping("/{reference}/mock-processing")
    public PaymentResponse mockProcess(@AuthenticationPrincipal AppUser admin, @PathVariable String reference,
                                       @RequestParam PaymentStatus outcome) {
        return toResponse(payments.mockProcess(admin, reference, outcome));
    }

    @PostMapping("/{reference}/refund")
    public PaymentResponse refund(@AuthenticationPrincipal AppUser admin, @PathVariable String reference) {
        return toResponse(payments.refund(admin, reference));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getPaymentReference(), payment.getPolicy().getPolicyNumber(),
                payment.getAmount(), payment.getStatus().name(), payment.getCreatedAt(), payment.getUpdatedAt(),
                payment.getProcessedAt(), payment.getRefundedAt(), "MOCK - NOT A REAL PAYMENT");
    }

    public record PaymentResponse(String reference, String policyNumber, BigDecimal amount, String status,
                                  Instant createdAt, Instant updatedAt, Instant processedAt, Instant refundedAt,
                                  String processingMode) { }
}
