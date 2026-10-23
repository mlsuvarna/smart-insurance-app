package com.smartinsurance.controller;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Payment;
import com.smartinsurance.enums.PaymentStatus;
import com.smartinsurance.service.PaymentService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.smartinsurance.service.PdfService;
import com.smartinsurance.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('CUSTOMER')")
public class PaymentController {
    private final PaymentService payments;
    private final PdfService pdf;
    public PaymentController(PaymentService payments, PdfService pdf) { this.payments = payments; this.pdf = pdf; }
    @PostMapping
    public PaymentResponse record(@AuthenticationPrincipal AppUser user, @RequestParam Long policyId) {
        return toResponse(payments.record(user, policyId));
    }
    @GetMapping
    public Page<PaymentResponse> own(@AuthenticationPrincipal AppUser user,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return payments.own(user, PageRequests.of(page, size)).map(this::toResponse);
    }
    @GetMapping("/{reference}")
    public PaymentResponse get(@AuthenticationPrincipal AppUser user, @PathVariable String reference) {
        return toResponse(payments.get(user, reference));
    }
    @GetMapping("/{reference}/history")
    public List<PaymentStatusHistoryResponse> history(@AuthenticationPrincipal AppUser user,
                                                      @PathVariable String reference) {
        return payments.history(user, reference).stream().map(item -> new PaymentStatusHistoryResponse(
                item.getPreviousStatus(), item.getNewStatus(), item.getProcessor(), item.getCreatedAt())).toList();
    }
    @GetMapping("/{reference}/receipt")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal AppUser user, @PathVariable String reference) {
        Payment payment = payments.get(user, reference);
        byte[] content = pdf.create("Premium payment receipt", List.of("Reference: " + payment.getPaymentReference(),
                "Policy number: " + payment.getPolicy().getPolicyNumber(), "Amount: " + payment.getAmount(),
                "Status: " + payment.getStatus(), "Date: " + payment.getCreatedAt()));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payment-receipt.pdf").body(content);
    }
    private PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getPaymentReference(), p.getPolicy().getPolicyNumber(), p.getAmount(),
                p.getStatus().name(), p.getCreatedAt(), p.getUpdatedAt(), p.getProcessedAt(), p.getRefundedAt(),
                "MOCK - NOT A REAL PAYMENT");
    }
    public record PaymentResponse(String reference, String policyNumber, java.math.BigDecimal amount, String status,
                                  java.time.Instant createdAt, java.time.Instant updatedAt,
                                  java.time.Instant processedAt, java.time.Instant refundedAt, String processingMode) { }
    public record PaymentStatusHistoryResponse(PaymentStatus previousStatus, PaymentStatus newStatus,
                                               String processor, java.time.Instant createdAt) { }
}
