package com.smartinsurance.controller;

import com.smartinsurance.entity.ClaimType;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.ProductRepository;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.service.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.smartinsurance.entity.AppUser;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/claim-types")
public class ClaimTypeController {
    private final ClaimTypeRepository types;
    private final ProductRepository products;
    private final ClaimQueueRepository queues;
    private final AuditLogService audit;
    public ClaimTypeController(ClaimTypeRepository types, ProductRepository products, ClaimQueueRepository queues,
                               AuditLogService audit) {
        this.types = types; this.products = products; this.queues = queues; this.audit = audit;
    }
    @GetMapping
    @Transactional(readOnly = true)
    public List<ClaimTypeResponse> active(@RequestParam Long productId) {
        return types.findByProductIdAndActiveTrue(productId).stream().map(this::response).toList();
    }
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ClaimTypeResponse create(@AuthenticationPrincipal AppUser actor, @Valid @RequestBody ClaimTypeRequest request) {
        InsuranceProduct product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ClaimQueue queue = resolveQueue(request.queueCode());
        ClaimType type = types.save(new ClaimType(product, queue, request.name().trim()));
        audit.record(actor, "CLAIM_TYPE_CREATED", "CLAIM_TYPE", type.getId().toString());
        return response(type);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ClaimTypeResponse update(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                                    @Valid @RequestBody ClaimTypeRequest request) {
        ClaimType type = types.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim type not found"));
        InsuranceProduct product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        ClaimQueue queue = resolveQueue(request.queueCode());
        type.update(product, queue, request.name().trim());
        audit.record(actor, "CLAIM_TYPE_UPDATED", "CLAIM_TYPE", id.toString());
        return response(type);
    }
    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<ClaimTypeResponse> all() { return types.findAll().stream().map(this::response).toList(); }
    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ClaimTypeResponse availability(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                                          @RequestParam boolean active) {
        ClaimType type = types.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim type not found"));
        type.setActive(active);
        types.save(type);
        audit.record(actor, active ? "CLAIM_TYPE_ACTIVATED" : "CLAIM_TYPE_DEACTIVATED", "CLAIM_TYPE", id.toString());
        return response(type);
    }
    private ClaimTypeResponse response(ClaimType type) {
        return new ClaimTypeResponse(type.getId(), type.getProduct().getId(), type.getName(), type.isActive(),
                type.getQueue().getCode());
    }
    private ClaimQueue resolveQueue(String queueCode) {
        String code = queueCode == null || queueCode.isBlank() ? "GENERAL" : queueCode.trim().toUpperCase();
        ClaimQueue queue = queues.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Claims queue not found"));
        if (!queue.isActive()) throw new com.smartinsurance.exception.BusinessRuleException("Claims queue is inactive");
        return queue;
    }
    public record ClaimTypeRequest(@NotNull Long productId, @NotBlank @Size(max = 80) String name,
                                  @Size(max = 40) String queueCode) { }
    public record ClaimTypeResponse(Long id, Long productId, String name, boolean active, String queueCode) { }
}
