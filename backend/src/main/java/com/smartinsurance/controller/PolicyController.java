package com.smartinsurance.controller;

import com.smartinsurance.dto.PolicyDtos.PolicyResponse;
import com.smartinsurance.dto.PolicyDtos.PurchaseRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.PolicyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;
import com.smartinsurance.util.PageRequests;

@RestController
@RequestMapping("/api/v1/policies")
@PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
public class PolicyController {
    private final PolicyService policies;
    public PolicyController(PolicyService policies) { this.policies = policies; }
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public PolicyResponse purchase(@AuthenticationPrincipal AppUser user, @Valid @RequestBody PurchaseRequest request) {
        return policies.purchase(user, request);
    }
    @GetMapping
    public Page<PolicyResponse> own(@AuthenticationPrincipal AppUser user,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return policies.own(user, PageRequests.of(page, size));
    }
    @GetMapping("/{id}")
    public PolicyResponse get(@AuthenticationPrincipal AppUser user, @PathVariable Long id) { return policies.get(user, id); }
    @PostMapping("/{id}/renew")
    public PolicyResponse renew(@AuthenticationPrincipal AppUser user, @PathVariable Long id) { return policies.renew(user, id); }
    @PostMapping("/{id}/cancel")
    public PolicyResponse cancel(@AuthenticationPrincipal AppUser user, @PathVariable Long id) { return policies.cancel(user, id); }
}
