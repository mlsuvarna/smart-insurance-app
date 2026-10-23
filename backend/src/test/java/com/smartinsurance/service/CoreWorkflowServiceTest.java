package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinsurance.dto.PolicyDtos.PurchaseRequest;
import com.smartinsurance.dto.ProductDtos.ProductRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Claim;
import com.smartinsurance.entity.ClaimDocument;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.entity.Payment;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.enums.DocumentScanStatus;
import com.smartinsurance.enums.PaymentStatus;
import com.smartinsurance.enums.PolicyStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.ClaimDocumentRepository;
import com.smartinsurance.repository.ClaimHistoryRepository;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.PaymentRepository;
import com.smartinsurance.repository.PaymentStatusHistoryRepository;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.ProductRepository;
import com.smartinsurance.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CoreWorkflowServiceTest {
    @Test
    void productCreationPersistsAndReturnsAdminProduct() {
        ProductRepository products = mock(ProductRepository.class);
        AuditLogService audit = mock(AuditLogService.class);
        when(products.save(any(InsuranceProduct.class))).thenAnswer(call -> {
            InsuranceProduct saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 41L);
            return saved;
        });
        ProductService service = new ProductService(products, audit);
        AppUser admin = user(1L, Role.ADMIN);

        var response = service.create(admin, new ProductRequest("Home Cover", "HOME", "Coverage",
                new BigDecimal("120.00"), new BigDecimal("50000.00")));

        assertEquals("Home Cover", response.name());
        assertEquals(new BigDecimal("50000.00"), response.coverageLimit());
        verify(audit).record(admin, "PRODUCT_CREATED", "PRODUCT", "41");
    }

    @Test
    void productCanBeUpdatedAndDeactivatedByManagementService() {
        ProductRepository products = mock(ProductRepository.class);
        AuditLogService audit = mock(AuditLogService.class);
        InsuranceProduct existing = product();
        ReflectionTestUtils.setField(existing, "id", 42L);
        when(products.findById(42L)).thenReturn(Optional.of(existing));
        ProductService service = new ProductService(products, audit);
        AppUser admin = user(1L, Role.ADMIN);

        var updated = service.update(admin, 42L, new ProductRequest("Updated", "HOME", "Updated cover",
                new BigDecimal("130.00"), new BigDecimal("55000.00")));
        var deactivated = service.setActive(admin, 42L, false);

        assertEquals("Updated", updated.name());
        assertFalse(deactivated.active());
        verify(audit).record(admin, "PRODUCT_UPDATED", "PRODUCT", "42");
        verify(audit).record(admin, "PRODUCT_DEACTIVATED", "PRODUCT", "42");
    }

    @Test
    void policyPurchaseRejectsInactiveProduct() {
        ProductRepository products = mock(ProductRepository.class);
        InsuranceProduct product = product();
        product.setActive(false);
        when(products.findById(4L)).thenReturn(Optional.of(product));
        PolicyService service = new PolicyService(mock(PolicyRepository.class), products,
                mock(AssignmentRepository.class), mock(AuditLogService.class));

        assertThrows(BusinessRuleException.class,
                () -> service.purchase(user(2L, Role.CUSTOMER), new PurchaseRequest(4L)));
    }

    @Test
    void policyPurchaseCreatesPendingPaymentPolicy() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findById(4L)).thenReturn(Optional.of(product()));
        PolicyRepository policies = mock(PolicyRepository.class);
        when(policies.save(any(Policy.class))).thenAnswer(call -> call.getArgument(0));
        PolicyService service = new PolicyService(policies, products, mock(AssignmentRepository.class),
                mock(AuditLogService.class));

        var response = service.purchase(user(2L, Role.CUSTOMER), new PurchaseRequest(4L));

        assertEquals(PolicyStatus.PENDING_PAYMENT, response.status());
        verify(policies).save(any(Policy.class));
    }

    @Test
    void agentCannotReadUnassignedCustomerPolicy() {
        PolicyRepository policies = mock(PolicyRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        when(policies.findById(8L)).thenReturn(Optional.of(new Policy("P-1",
                user(5L, Role.CUSTOMER), product(), LocalDate.now(), LocalDate.now().plusYears(1))));
        when(assignments.existsByAgentIdAndCustomerId(9L, 5L)).thenReturn(false);
        PolicyService service = new PolicyService(policies, mock(ProductRepository.class), assignments,
                mock(AuditLogService.class));

        assertThrows(AccessDeniedException.class,
                () -> service.assignedCustomerPolicies(user(9L, Role.AGENT), 5L,
                        org.springframework.data.domain.PageRequest.of(0, 10)));
        verify(policies, never()).findByCustomerIdOrderByCreatedAtDescIdDesc(any(), any());
    }

    @Test
    void paymentStartsPendingAndOnlyPermitsDeclaredTransitions() {
        Policy policy = new Policy("P-2", user(7L, Role.CUSTOMER), product(),
                LocalDate.now(), LocalDate.now().plusYears(1));
        Payment payment = new Payment("PAY-1", policy);
        assertEquals(PaymentStatus.PENDING, payment.getStatus());

        payment.transition(PaymentStatus.SUCCESS, Instant.now());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertThrows(BusinessRuleException.class, () -> payment.transition(PaymentStatus.FAILED, Instant.now()));
    }

    @Test
    void customersCannotMockProcessPayment() {
        PaymentService service = new PaymentService(mock(PaymentRepository.class), mock(PolicyRepository.class),
                mock(AuditLogService.class), mock(PaymentStatusHistoryRepository.class));

        assertThrows(AccessDeniedException.class,
                () -> service.mockProcess(user(3L, Role.CUSTOMER), "PAY-3", PaymentStatus.SUCCESS));
    }

    @Test
    void customerPaymentRequestCreatesPendingRecordOnly() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PolicyRepository policies = mock(PolicyRepository.class);
        AppUser customer = user(7L, Role.CUSTOMER);
        Policy policy = new Policy("P-2", customer, product(), LocalDate.now(), LocalDate.now().plusYears(1));
        when(policies.findById(8L)).thenReturn(Optional.of(policy));
        when(payments.existsByPolicyIdAndStatus(8L, PaymentStatus.PENDING)).thenReturn(false);
        when(payments.save(any(Payment.class))).thenAnswer(call -> call.getArgument(0));
        PaymentStatusHistoryRepository history = mock(PaymentStatusHistoryRepository.class);
        PaymentService service = new PaymentService(payments, policies, mock(AuditLogService.class), history);

        Payment payment = service.record(customer, 8L);

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(history).save(any());
    }

    @Test
    void customerCanViewOwnInvoiceAndPaymentHistoryButNotAnotherCustomersPayment() {
        PaymentRepository payments = mock(PaymentRepository.class);
        PaymentStatusHistoryRepository history = mock(PaymentStatusHistoryRepository.class);
        AppUser customer = user(7L, Role.CUSTOMER);
        Payment payment = new Payment("PAY-OWN", new Policy("P-OWN", customer, product(),
                LocalDate.now(), LocalDate.now().plusYears(1)));
        ReflectionTestUtils.setField(payment, "id", 9L);
        when(payments.findByPaymentReferenceAndCustomerId("PAY-OWN", customer.getId()))
                .thenReturn(Optional.of(payment));
        when(payments.findByPaymentReferenceAndCustomerId("PAY-OWN", 8L)).thenReturn(Optional.empty());
        when(payments.findByPaymentReference("PAY-OWN")).thenReturn(Optional.of(payment));
        when(history.findByPaymentIdOrderByCreatedAtAsc(9L)).thenReturn(List.of());
        PaymentService service = new PaymentService(payments, mock(PolicyRepository.class),
                mock(AuditLogService.class), history);

        assertEquals("PAY-OWN", service.get(customer, "PAY-OWN").getPaymentReference());
        assertEquals(0, service.history(customer, "PAY-OWN").size());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.history(user(8L, Role.CUSTOMER), "PAY-OWN"));
        assertThrows(com.smartinsurance.exception.ResourceNotFoundException.class,
                () -> service.get(user(8L, Role.CUSTOMER), "PAY-OWN"));
    }

    @Test
    void adminMockSuccessActivatesPendingPolicyAndRecordsStatusHistory() {
        PaymentRepository payments = mock(PaymentRepository.class);
        AppUser customer = user(7L, Role.CUSTOMER);
        Policy policy = new Policy("P-2", customer, product(), LocalDate.now(), LocalDate.now().plusYears(1));
        Payment payment = new Payment("PAY-2", policy);
        when(payments.findByReferenceForUpdate("PAY-2")).thenReturn(Optional.of(payment));
        PaymentStatusHistoryRepository history = mock(PaymentStatusHistoryRepository.class);
        PaymentService service = new PaymentService(payments, mock(PolicyRepository.class),
                mock(AuditLogService.class), history);

        service.mockProcess(user(1L, Role.ADMIN), "PAY-2", PaymentStatus.SUCCESS);

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertEquals(PolicyStatus.ACTIVE, policy.getStatus());
        verify(history).save(any());
    }

    @Test
    void inactiveQueueCannotReceiveOfficers() {
        ClaimQueueRepository queues = mock(ClaimQueueRepository.class);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        queue.update("Auto", false);
        when(queues.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.of(queue));
        ClaimQueueService service = new ClaimQueueService(queues, mock(ClaimRepository.class),
                mock(ClaimTypeRepository.class), mock(OfficerQueueMembershipRepository.class),
                mock(UserRepository.class), mock(AuditLogService.class));

        assertThrows(BusinessRuleException.class, () -> service.addOfficer(user(1L, Role.ADMIN), "AUTO", 2L));
    }

    @Test
    void queueCreationNormalizesAndAuditsIdentifier() {
        ClaimQueueRepository queues = mock(ClaimQueueRepository.class);
        when(queues.existsByCodeIgnoreCase("AUTO")).thenReturn(false);
        when(queues.existsByNameIgnoreCase("Auto")).thenReturn(false);
        when(queues.save(any(ClaimQueue.class))).thenAnswer(call -> call.getArgument(0));
        AuditLogService audit = mock(AuditLogService.class);
        ClaimQueueService service = new ClaimQueueService(queues, mock(ClaimRepository.class),
                mock(ClaimTypeRepository.class), mock(OfficerQueueMembershipRepository.class),
                mock(UserRepository.class), audit);
        AppUser admin = user(1L, Role.ADMIN);

        ClaimQueue queue = service.create(admin, " auto ", " Auto ");

        assertEquals("AUTO", queue.getCode());
        assertEquals("Auto", queue.getName());
        verify(audit).record(admin, "CLAIM_QUEUE_CREATED", "CLAIM_QUEUE", "AUTO");
    }

    @Test
    void activeOfficerCanBeAddedToActiveQueue() {
        ClaimQueueRepository queues = mock(ClaimQueueRepository.class);
        OfficerQueueMembershipRepository memberships = mock(OfficerQueueMembershipRepository.class);
        UserRepository users = mock(UserRepository.class);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        ReflectionTestUtils.setField(queue, "id", 12L);
        when(queues.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.of(queue));
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, Role.CLAIMS_OFFICER)));
        when(memberships.existsByOfficerIdAndQueueId(2L, 12L)).thenReturn(false);
        ClaimQueueService service = new ClaimQueueService(queues, mock(ClaimRepository.class),
                mock(ClaimTypeRepository.class), memberships, users, mock(AuditLogService.class));

        service.addOfficer(user(1L, Role.ADMIN), "AUTO", 2L);

        verify(memberships).save(any(com.smartinsurance.entity.OfficerQueueMembership.class));
    }

    @Test
    void openQueueClaimPreventsOfficerMembershipRemoval() {
        ClaimQueueRepository queues = mock(ClaimQueueRepository.class);
        ClaimRepository claims = mock(ClaimRepository.class);
        OfficerQueueMembershipRepository memberships = mock(OfficerQueueMembershipRepository.class);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        ReflectionTestUtils.setField(queue, "id", 12L);
        when(queues.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.of(queue));
        var membership = new com.smartinsurance.entity.OfficerQueueMembership(queue, user(2L, Role.CLAIMS_OFFICER),
                user(1L, Role.ADMIN));
        when(memberships.findByOfficerIdAndQueueId(2L, 12L)).thenReturn(Optional.of(membership));
        when(claims.existsByQueueIdAndAssignedOfficerIdAndStatusNotIn(any(), any(), any())).thenReturn(true);
        ClaimQueueService service = new ClaimQueueService(queues, claims, mock(ClaimTypeRepository.class), memberships,
                mock(UserRepository.class), mock(AuditLogService.class));

        assertThrows(BusinessRuleException.class, () -> service.removeOfficer(user(1L, Role.ADMIN), "AUTO", 2L));
        verify(memberships, never()).delete(membership);
    }

    @Test
    void deactivationIsBlockedWhileActiveClaimTypeUsesQueue() {
        ClaimQueueRepository queues = mock(ClaimQueueRepository.class);
        ClaimTypeRepository types = mock(ClaimTypeRepository.class);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        ReflectionTestUtils.setField(queue, "id", 12L);
        when(queues.findByCodeIgnoreCase("AUTO")).thenReturn(Optional.of(queue));
        when(queues.existsByNameIgnoreCase("Renamed")).thenReturn(false);
        when(types.existsByQueueIdAndActiveTrue(12L)).thenReturn(true);
        ClaimQueueService service = new ClaimQueueService(queues, mock(ClaimRepository.class), types,
                mock(OfficerQueueMembershipRepository.class), mock(UserRepository.class), mock(AuditLogService.class));

        assertThrows(BusinessRuleException.class,
                () -> service.update(user(1L, Role.ADMIN), "AUTO", "Renamed", false));
    }

    @Test
    void dirtyOrPendingDocumentIsNeverDownloaded() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
        Claim claim = mock(Claim.class);
        ReflectionTestUtils.setField(claim, "id", 10L);
        when(claim.getCustomer()).thenReturn(user(6L, Role.CUSTOMER));
        ClaimDocument document = new ClaimDocument(claim, user(6L, Role.CUSTOMER), "evidence.pdf",
                "claims/10/key", "application/pdf", 10);
        when(documents.findById(21L)).thenReturn(Optional.of(document));
        when(claims.findById(10L)).thenReturn(Optional.of(claim));
        DocumentService service = new DocumentService(documents, claims, mock(AssignmentRepository.class),
                mock(ClaimHistoryRepository.class), mock(DocumentStorage.class), mock(DocumentScanner.class),
                mock(OfficerQueueMembershipRepository.class));

        assertEquals(DocumentScanStatus.PENDING, document.getScanStatus());
        assertThrows(BusinessRuleException.class, () -> service.download(user(6L, Role.CUSTOMER), 10L, 21L));
    }

    @Test
    void validPdfIsAcceptedAndCleanAuthorizedDocumentCanBeDownloaded() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        DocumentScanner scanner = mock(DocumentScanner.class);
        AppUser customer = user(6L, Role.CUSTOMER);
        Claim claim = mock(Claim.class);
        ReflectionTestUtils.setField(claim, "id", 10L);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getStatus()).thenReturn(com.smartinsurance.enums.ClaimStatus.SUBMITTED);
        when(claims.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(claims.findById(10L)).thenReturn(Optional.of(claim));
        when(documents.countByClaimId(10L)).thenReturn(0L);
        when(scanner.scan(any())).thenReturn(new DocumentScanner.ScanResult(DocumentScanStatus.CLEAN, "TEST_SCANNER"));
        byte[] content = "%PDF-1.4 safe evidence".getBytes(StandardCharsets.US_ASCII);
        when(storage.load(any())).thenReturn(content);
        AtomicReference<ClaimDocument> savedDocument = new AtomicReference<>();
        when(documents.save(any(ClaimDocument.class))).thenAnswer(call -> {
            ClaimDocument saved = call.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 21L);
            savedDocument.set(saved);
            return saved;
        });
        DocumentService service = new DocumentService(documents, claims, mock(AssignmentRepository.class),
                mock(ClaimHistoryRepository.class), storage, scanner,
                mock(OfficerQueueMembershipRepository.class));
        ReflectionTestUtils.setField(service, "maxDocuments", 10L);
        ReflectionTestUtils.setField(service, "maxSizeMb", 10L);
        MockMultipartFile file = new MockMultipartFile("file", "evidence.pdf", "application/pdf", content);
        TransactionSynchronizationManager.initSynchronization();
        try {
            var response = service.upload(customer, 10L, file);
            assertEquals(DocumentScanStatus.CLEAN, response.scanStatus());
            when(documents.findById(21L)).thenReturn(Optional.of(savedDocument.get()));

            DocumentService.Download download = service.download(customer, 10L, 21L);

            assertEquals("evidence.pdf", download.filename());
            assertEquals(new String(content, StandardCharsets.US_ASCII),
                    new String(download.content(), StandardCharsets.US_ASCII));
            verify(storage).store(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void developmentScannerDoesNotClaimMalwareCleanliness() {
        DocumentScanner.ScanResult result = new DevelopmentDocumentScanner().scan(new byte[] {1, 2, 3});

        assertEquals(DocumentScanStatus.FAILED, result.status());
        assertEquals("DEVELOPMENT_ONLY_NO_MALWARE_SCAN", result.engine());
    }

    @Test
    void malformedDocxArchiveIsRejectedBeforeStorage() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        AppUser customer = user(6L, Role.CUSTOMER);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getStatus()).thenReturn(com.smartinsurance.enums.ClaimStatus.SUBMITTED);
        when(claims.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(documents.countByClaimId(10L)).thenReturn(0L);
        DocumentService service = new DocumentService(documents, claims, mock(AssignmentRepository.class),
                mock(ClaimHistoryRepository.class), storage, mock(DocumentScanner.class),
                mock(OfficerQueueMembershipRepository.class));
        ReflectionTestUtils.setField(service, "maxDocuments", 10L);
        ReflectionTestUtils.setField(service, "maxSizeMb", 10L);
        MockMultipartFile malformedDocx = new MockMultipartFile("file", "evidence.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                new byte[] {0x50, 0x4b, 0x03, 0x04});

        assertThrows(BusinessRuleException.class, () -> service.upload(customer, 10L, malformedDocx));
        verify(storage, never()).store(any(), any());
        verify(documents, never()).save(any(ClaimDocument.class));
    }

    @Test
    void documentUploadRejectsUnsupportedOversizedAndOverCountFiles() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimDocumentRepository documents = mock(ClaimDocumentRepository.class);
        DocumentStorage storage = mock(DocumentStorage.class);
        AppUser customer = user(6L, Role.CUSTOMER);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getStatus()).thenReturn(com.smartinsurance.enums.ClaimStatus.SUBMITTED);
        when(claims.findByIdForUpdate(10L)).thenReturn(Optional.of(claim));
        when(documents.countByClaimId(10L)).thenReturn(0L, 0L, 10L);
        DocumentService service = new DocumentService(documents, claims, mock(AssignmentRepository.class),
                mock(ClaimHistoryRepository.class), storage, mock(DocumentScanner.class),
                mock(OfficerQueueMembershipRepository.class));
        ReflectionTestUtils.setField(service, "maxDocuments", 10L);
        ReflectionTestUtils.setField(service, "maxSizeMb", 1L);

        MockMultipartFile unsupported = new MockMultipartFile("file", "evidence.exe",
                "application/octet-stream", new byte[] {1});
        MockMultipartFile invalidPngSignature = new MockMultipartFile("file", "evidence.png", "image/png",
                new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0});
        byte[] oversizedBytes = new byte[1024 * 1024 + 1];
        MockMultipartFile oversized = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                oversizedBytes);
        MockMultipartFile overCount = new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.US_ASCII));

        assertThrows(BusinessRuleException.class, () -> service.upload(customer, 10L, unsupported));
        assertThrows(BusinessRuleException.class, () -> service.upload(customer, 10L, oversized));
        assertThrows(BusinessRuleException.class, () -> service.upload(customer, 10L, invalidPngSignature));
        assertThrows(BusinessRuleException.class, () -> service.upload(customer, 10L, overCount));
        verify(storage, never()).store(any(), any());
        verify(documents, never()).save(any(ClaimDocument.class));
    }

    private static AppUser user(long id, Role role) {
        AppUser user = new AppUser("user" + id + "@example.test", "hash", role, "User " + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static InsuranceProduct product() {
        return new InsuranceProduct("Home", "HOME", "Coverage", new BigDecimal("100.00"),
                new BigDecimal("10000.00"));
    }
}
