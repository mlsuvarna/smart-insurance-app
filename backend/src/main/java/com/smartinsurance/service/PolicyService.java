package com.smartinsurance.service;

import com.smartinsurance.dto.PolicyDtos.PolicyResponse;
import com.smartinsurance.dto.PolicyDtos.PurchaseRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.enums.PolicyStatus;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.ProductRepository;
import com.smartinsurance.repository.AssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyService {
    private final PolicyRepository policies;
    private final ProductRepository products;
    private final AssignmentRepository assignments;
    private final AuditLogService audit;
    public PolicyService(PolicyRepository policies, ProductRepository products, AssignmentRepository assignments, AuditLogService audit) {
        this.policies = policies; this.products = products; this.assignments = assignments; this.audit = audit;
    }
    @Transactional
    public PolicyResponse purchase(AppUser customer, PurchaseRequest request) {
        InsuranceProduct product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        if (!product.isActive()) throw new BusinessRuleException("Product is not available for purchase");
        LocalDate start = LocalDate.now(java.time.ZoneOffset.UTC);
        Policy policy = policies.save(new Policy("POL-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                customer, product, start, start.plusYears(1)));
        audit.record(customer, "POLICY_PURCHASED", "POLICY", policy.getPolicyNumber());
        return toResponse(policy);
    }
    @Transactional
    public Page<PolicyResponse> own(AppUser customer, Pageable pageable) {
        return policies.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), pageable).map(this::toResponse);
    }
    @Transactional
    public Page<PolicyResponse> assignedCustomerPolicies(AppUser agent, Long customerId, Pageable pageable) {
        if (agent.getRole() != com.smartinsurance.enums.Role.AGENT
                || !assignments.existsByAgentIdAndCustomerId(agent.getId(), customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Customer is not assigned to this agent");
        }
        return policies.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable).map(this::toResponse);
    }
    @Transactional
    public PolicyResponse get(AppUser actor, Long id) {
        Policy policy = policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomer().getId().equals(actor.getId())
                && actor.getRole() != com.smartinsurance.enums.Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Policy is not accessible");
        }
        return toResponse(policy);
    }
    @Transactional
    public PolicyResponse renew(AppUser actor, Long id) {
        Policy policy = owned(actor, id);
        if (policy.getStatus() == PolicyStatus.CANCELLED || policy.getStatus() == PolicyStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("This policy cannot be renewed");
        }
        LocalDate utcToday = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate start = policy.getEndDate().isBefore(utcToday) ? utcToday : policy.getEndDate().plusDays(1);
        policy.setEndDate(start.plusYears(1));
        policy.setStatus(PolicyStatus.ACTIVE);
        audit.record(actor, "POLICY_RENEWED", "POLICY", policy.getPolicyNumber());
        return toResponse(policy);
    }
    @Transactional
    public PolicyResponse cancel(AppUser actor, Long id) {
        Policy policy = owned(actor, id);
        if (policy.getStatus() != PolicyStatus.ACTIVE) throw new BusinessRuleException("Only active policies can be cancelled");
        policy.setStatus(PolicyStatus.CANCELLED);
        audit.record(actor, "POLICY_CANCELLED", "POLICY", policy.getPolicyNumber());
        return toResponse(policy);
    }
    private Policy owned(AppUser actor, Long id) {
        Policy policy = policies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomer().getId().equals(actor.getId()) && actor.getRole() != com.smartinsurance.enums.Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Policy is not accessible");
        }
        expireIfPast(policy);
        return policy;
    }
    private PolicyResponse toResponse(Policy p) {
        expireIfPast(p);
        return new PolicyResponse(p.getId(), p.getPolicyNumber(), p.getProduct().getId(), p.getProduct().getName(),
                p.getCoverageAmount(), p.getPremiumAmount(), p.getStartDate(), p.getEndDate(), p.getStatus(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
    private void expireIfPast(Policy policy) {
        if (policy.getStatus() == PolicyStatus.ACTIVE
                && policy.getEndDate().isBefore(LocalDate.now(java.time.ZoneOffset.UTC))) {
            policy.setStatus(PolicyStatus.EXPIRED);
        }
    }
}
