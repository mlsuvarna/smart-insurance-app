package com.smartinsurance.controller;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.dto.PaymentDtos.AdminPaymentResponse;
import com.smartinsurance.enums.PaymentStatus;
import com.smartinsurance.service.PaymentService;
import com.smartinsurance.util.PageRequests;
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
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {
    private final PaymentService payments;

    public AdminPaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @GetMapping
    public Page<AdminPaymentResponse> all(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return payments.all(PageRequests.of(page, size));
    }

    @PostMapping("/{reference}/mock-processing")
    public AdminPaymentResponse mockProcess(@AuthenticationPrincipal AppUser admin, @PathVariable String reference,
                                            @RequestParam PaymentStatus outcome) {
        payments.mockProcess(admin, reference, outcome);
        return payments.getAdmin(reference);
    }

    @PostMapping("/{reference}/refund")
    public AdminPaymentResponse refund(@AuthenticationPrincipal AppUser admin, @PathVariable String reference) {
        payments.refund(admin, reference);
        return payments.getAdmin(reference);
    }
}
