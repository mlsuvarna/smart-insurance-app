package com.smartinsurance.controller;

import com.smartinsurance.dto.CustomerDtos.ProfileResponse;
import com.smartinsurance.dto.CustomerDtos.ProfileUpdate;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.CustomerProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smartinsurance.enums.KycStatus;
import com.smartinsurance.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {
    private final CustomerProfileService profiles;
    public CustomerController(CustomerProfileService profiles) { this.profiles = profiles; }
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CUSTOMER','AGENT','ADMIN')")
    public ProfileResponse get(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return profiles.get(user, id);
    }
    @PutMapping("/me/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ProfileResponse update(@AuthenticationPrincipal AppUser user, @Valid @RequestBody ProfileUpdate request) {
        return profiles.update(user, request);
    }
    @GetMapping("/admin/kyc")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ProfileResponse> kycQueue(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return profiles.listForAdmin(PageRequests.of(page, size));
    }
    @PostMapping("/admin/{id}/kyc-review")
    @PreAuthorize("hasRole('ADMIN')")
    public ProfileResponse reviewKyc(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                                     @RequestParam KycStatus status) {
        return profiles.review(actor, id, status);
    }
}
