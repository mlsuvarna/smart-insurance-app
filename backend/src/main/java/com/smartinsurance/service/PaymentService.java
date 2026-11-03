package com.smartinsurance.service;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Payment;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.entity.PaymentStatusHistory;
import com.smartinsurance.enums.PaymentStatus;
import com.smartinsurance.enums.PolicyStatus;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.PaymentRepository;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.PaymentStatusHistoryRepository;
import com.smartinsurance.dto.PaymentDtos.AdminPaymentResponse;
import java.util.List;
import java.util.UUID;
import java.time.ZoneOffset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final PaymentRepository payments;
    private final PolicyRepository policies;
    private final AuditLogService audit;
    private final PaymentStatusHistoryRepository statusHistory;
    private final NotificationService notifications;
    public PaymentService(PaymentRepository payments, PolicyRepository policies, AuditLogService audit,
                          PaymentStatusHistoryRepository statusHistory, NotificationService notifications) {
        this.payments = payments; this.policies = policies; this.audit = audit;
        this.statusHistory = statusHistory; this.notifications = notifications;
    }
    @Transactional
    public Payment record(AppUser customer, Long policyId) {
        if (customer.getRole() != com.smartinsurance.enums.Role.CUSTOMER) {
            throw new AccessDeniedException("Only customers can initiate premium payments");
        }
        Policy policy = policies.findByIdForPayment(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomer().getId().equals(customer.getId())) throw new AccessDeniedException("Policy is not accessible");
        if (policy.getStatus() != PolicyStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only pending-payment policies can be paid");
        }
        java.time.Instant periodStart = policy.getStartDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        if (payments.existsByPolicyIdAndStatusAndCreatedAtGreaterThanEqual(policyId, PaymentStatus.PENDING, periodStart)
                || payments.existsByPolicyIdAndStatusAndCreatedAtGreaterThanEqual(
                        policyId, PaymentStatus.SUCCESS, periodStart)) {
            throw new BusinessRuleException("A pending or successful payment already exists for the current policy period");
        }
        Payment payment = payments.save(new Payment("PAY-" + UUID.randomUUID().toString().toUpperCase(), policy));
        statusHistory.save(new PaymentStatusHistory(payment, customer, null, PaymentStatus.PENDING, "MOCK"));
        audit.record(customer, "PREMIUM_PAYMENT_RECORDED", "PAYMENT", payment.getPaymentReference());
        return payment;
    }
    @Transactional
    public Payment mockProcess(AppUser actor, String reference, PaymentStatus outcome) {
        if (actor.getRole() != com.smartinsurance.enums.Role.ADMIN) {
            throw new AccessDeniedException("Administrator authorization required for mock payment processing");
        }
        if (outcome != PaymentStatus.SUCCESS && outcome != PaymentStatus.FAILED) {
            throw new BusinessRuleException("Mock processing outcome must be SUCCESS or FAILED");
        }
        Payment payment = payments.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (outcome == PaymentStatus.SUCCESS && payment.getPolicy().getStatus() != PolicyStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only pending-payment policies can be activated by payment");
        }
        PaymentStatus previous = payment.getStatus();
        payment.transition(outcome, java.time.Instant.now());
        if (outcome == PaymentStatus.SUCCESS) {
            payment.getPolicy().setStatus(PolicyStatus.ACTIVE);
            audit.record(actor, "POLICY_ACTIVATED_BY_PAYMENT", "POLICY", payment.getPolicy().getPolicyNumber());
            notifications.paymentEvent(payment.getCustomer(), "PREMIUM_PAYMENT_SUCCESS",
                    "Premium payment for policy " + payment.getPolicy().getPolicyNumber() + " was recorded successfully.");
        }
        statusHistory.save(new PaymentStatusHistory(payment, actor, previous, outcome, "MOCK"));
        audit.record(actor, "MOCK_PAYMENT_" + outcome.name(), "PAYMENT", payment.getPaymentReference());
        return payment;
    }
    @Transactional
    public Payment refund(AppUser actor, String reference) {
        if (actor.getRole() != com.smartinsurance.enums.Role.ADMIN) {
            throw new AccessDeniedException("Administrator authorization required to refund a payment");
        }
        Payment payment = payments.findByReferenceForUpdate(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        PaymentStatus previous = payment.getStatus();
        payment.transition(PaymentStatus.REFUNDED, java.time.Instant.now());
        statusHistory.save(new PaymentStatusHistory(payment, actor, previous, PaymentStatus.REFUNDED, "MOCK"));
        audit.record(actor, "MOCK_PAYMENT_REFUNDED", "PAYMENT", payment.getPaymentReference());
        return payment;
    }
    @Transactional(readOnly = true)
    public Page<Payment> own(AppUser customer, Pageable pageable) {
        return payments.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId(), pageable);
    }
    @Transactional(readOnly = true)
    public Payment get(AppUser customer, String reference) {
        return payments.findByPaymentReferenceAndCustomerId(reference, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }
    @Transactional(readOnly = true)
    public List<PaymentStatusHistory> history(AppUser actor, String reference) {
        Payment payment = payments.findByPaymentReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (actor.getRole() != com.smartinsurance.enums.Role.ADMIN
                && !payment.getCustomer().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Payment is not accessible");
        }
        return statusHistory.findByPaymentIdOrderByCreatedAtAsc(payment.getId());
    }
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> all(Pageable pageable) {
        return payments.findAllByOrderByCreatedAtDescIdDesc(pageable).map(this::adminResponse);
    }
    @Transactional(readOnly = true)
    public AdminPaymentResponse getAdmin(String reference) {
        return payments.findByPaymentReference(reference).map(this::adminResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }
    private AdminPaymentResponse adminResponse(Payment payment) {
        return new AdminPaymentResponse(payment.getPaymentReference(), payment.getPolicy().getPolicyNumber(),
                payment.getCustomer().getFullName(), payment.getCustomer().getEmail(), payment.getAmount(),
                payment.getStatus().name(), payment.getCreatedAt(), payment.getUpdatedAt(),
                payment.getProcessedAt(), payment.getRefundedAt(), "MOCK - NOT A REAL PAYMENT");
    }
}
