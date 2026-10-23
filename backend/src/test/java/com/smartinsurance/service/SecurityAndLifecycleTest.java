package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentCaptor.forClass;

import com.smartinsurance.dto.ClaimDtos.ActionRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Claim;
import com.smartinsurance.entity.ClaimHistory;
import com.smartinsurance.entity.ClaimReview;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.ClaimCommentRepository;
import com.smartinsurance.repository.ClaimHistoryRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimReviewRepository;
import com.smartinsurance.repository.ClaimSettlementRepository;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.UserRepository;
import com.smartinsurance.security.JwtService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class SecurityAndLifecycleTest {
    @Test
    void passwordChangeInvalidatesExistingJwt() {
        JwtService jwt = new JwtService();
        ReflectionTestUtils.setField(jwt, "secret", "test-only-signing-secret-at-least-32-bytes");
        AppUser user = user(14L, Role.CUSTOMER);
        String oldToken = jwt.createToken(user);

        user.setPasswordHash("replacement-hash");

        assertFalse(jwt.isValid(oldToken, user));
    }

    @Test
    void customerCannotWithdrawClaimAfterSubmissionState() {
        ClaimRepository claims = mock(ClaimRepository.class);
        AppUser customer = user(3L, Role.CUSTOMER);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getStatus()).thenReturn(ClaimStatus.UNDER_REVIEW);
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        ClaimService service = claimService(claims, mock(ClaimReviewRepository.class));

        assertThrows(BusinessRuleException.class, () -> service.withdraw(customer, 22L));
        verify(claim, never()).transition(ClaimStatus.WITHDRAWN);
    }

    @Test
    void customerCanWithdrawOwnSubmittedClaimAndHistoryRecordsTheReason() {
        AppUser customer = user(3L, Role.CUSTOMER);
        Claim claim = new Claim("CLM-WITHDRAW", new Policy("POL-1", customer,
                new InsuranceProduct("Home", "HOME", "Coverage", BigDecimal.TEN, BigDecimal.TEN),
                LocalDate.now(), LocalDate.now().plusYears(1)), new ClaimQueue("GENERAL", "General"),
                "HOME", LocalDate.now(), "Incident", BigDecimal.ONE, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(claim, "id", 22L);
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimHistoryRepository history = mock(ClaimHistoryRepository.class);
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        ClaimService service = new ClaimService(claims, history, mock(PolicyRepository.class),
                mock(AssignmentRepository.class), mock(NotificationService.class), mock(SlaService.class),
                mock(ClaimTypeRepository.class), new ClaimTransitionValidator(), mock(ClaimCommentRepository.class),
                mock(UserRepository.class), mock(ClaimReviewRepository.class), mock(ClaimSettlementRepository.class),
                mock(OfficerQueueMembershipRepository.class), mock(AuditLogService.class));

        var response = service.withdraw(customer, 22L);

        assertEquals(ClaimStatus.WITHDRAWN, response.status());
        var captured = forClass(ClaimHistory.class);
        verify(history).save(captured.capture());
        assertEquals(ClaimStatus.SUBMITTED, captured.getValue().getPreviousStatus());
        assertEquals(ClaimStatus.WITHDRAWN, captured.getValue().getNewStatus());
        assertEquals("CUSTOMER", captured.getValue().getActorRole());
        assertEquals("Withdrawn by customer", captured.getValue().getRemarks());
        assertNotNull(captured.getValue().getCreatedAt());
    }

    @Test
    void rejectionRequiresAReasonBeforeReviewIsChanged() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimReviewRepository reviews = mock(ClaimReviewRepository.class);
        AppUser officer = user(5L, Role.CLAIMS_OFFICER);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(user(3L, Role.CUSTOMER));
        when(claim.getAssignedOfficer()).thenReturn(officer);
        when(claim.getStatus()).thenReturn(ClaimStatus.UNDER_REVIEW);
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        ClaimService service = claimService(claims, reviews);

        assertThrows(BusinessRuleException.class,
                () -> service.reject(officer, 22L, new ActionRequest("  ", null)));
        verify(reviews, never()).findFirstByClaimIdAndClosedAtIsNullOrderByCreatedAtDesc(22L);
    }

    @Test
    void validRejectionClosesReviewAndRecordsCustomerVisibleReason() {
        AppUser customer = user(3L, Role.CUSTOMER);
        AppUser officer = user(5L, Role.CLAIMS_OFFICER);
        ClaimQueue queue = new ClaimQueue("GENERAL", "General");
        Claim claim = new Claim("CLM-REJECT", new Policy("POL-1", customer,
                new InsuranceProduct("Home", "HOME", "Coverage", BigDecimal.TEN, BigDecimal.TEN),
                LocalDate.now(), LocalDate.now().plusYears(1)), queue, "HOME", LocalDate.now(), "Incident",
                BigDecimal.ONE, Instant.now().plusSeconds(3600));
        ReflectionTestUtils.setField(claim, "id", 22L);
        ReflectionTestUtils.setField(claim, "status", ClaimStatus.UNDER_REVIEW);
        claim.setAssignedOfficer(officer);
        ClaimReview review = new ClaimReview(claim, officer);
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimReviewRepository reviews = mock(ClaimReviewRepository.class);
        ClaimHistoryRepository history = mock(ClaimHistoryRepository.class);
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        when(reviews.findFirstByClaimIdAndClosedAtIsNullOrderByCreatedAtDesc(22L)).thenReturn(Optional.of(review));
        ClaimService service = new ClaimService(claims, history, mock(PolicyRepository.class),
                mock(AssignmentRepository.class), mock(NotificationService.class), mock(SlaService.class),
                mock(ClaimTypeRepository.class), new ClaimTransitionValidator(), mock(ClaimCommentRepository.class),
                mock(UserRepository.class), reviews, mock(ClaimSettlementRepository.class),
                mock(OfficerQueueMembershipRepository.class), mock(AuditLogService.class));

        var response = service.reject(officer, 22L, new ActionRequest("Incident is outside coverage", null));

        assertEquals(ClaimStatus.REJECTED, response.status());
        assertEquals("REJECTED", review.getOutcome());
        var captured = forClass(ClaimHistory.class);
        verify(history).save(captured.capture());
        assertEquals("Incident is outside coverage", captured.getValue().getRemarks());
        assertEquals(ClaimStatus.UNDER_REVIEW, captured.getValue().getPreviousStatus());
        assertEquals(ClaimStatus.REJECTED, captured.getValue().getNewStatus());
        assertEquals("CLAIMS_OFFICER", captured.getValue().getActorRole());
    }

    @Test
    void customerResponseResumesSlaAndNotifiesAssignedOfficer() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimReviewRepository reviews = mock(ClaimReviewRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AppUser customer = user(3L, Role.CUSTOMER);
        AppUser officer = user(5L, Role.CLAIMS_OFFICER);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getAssignedOfficer()).thenReturn(officer);
        when(claim.getStatus()).thenReturn(ClaimStatus.INFO_REQUESTED);
        when(claim.getClaimNumber()).thenReturn("CLM-22");
        when(claim.getPolicy()).thenReturn(new Policy("POL-1", customer,
                new InsuranceProduct("Home", "HOME", "Coverage", BigDecimal.TEN, BigDecimal.TEN),
                LocalDate.now(), LocalDate.now().plusYears(1)));
        when(claim.getQueue()).thenReturn(new ClaimQueue("GENERAL", "General"));
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        when(reviews.findFirstByClaimIdAndClosedAtIsNullOrderByCreatedAtDesc(22L))
                .thenReturn(Optional.of(mock(com.smartinsurance.entity.ClaimReview.class)));
        ClaimService service = new ClaimService(claims, mock(ClaimHistoryRepository.class), mock(PolicyRepository.class),
                mock(AssignmentRepository.class), notifications, mock(SlaService.class),
                mock(ClaimTypeRepository.class), new ClaimTransitionValidator(), mock(ClaimCommentRepository.class),
                mock(UserRepository.class), reviews, mock(ClaimSettlementRepository.class),
                mock(OfficerQueueMembershipRepository.class), mock(AuditLogService.class));

        service.respond(customer, 22L, new ActionRequest("Additional details", null));

        verify(claim).resumeSla();
        verify(notifications).notifyUser(officer, claim, "CUSTOMER_RESPONSE",
                "Customer response received for claim CLM-22");
    }

    @Test
    void officerCannotStartReviewOutsideAssignedQueue() {
        ClaimRepository claims = mock(ClaimRepository.class);
        AppUser officer = user(5L, Role.CLAIMS_OFFICER);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        ReflectionTestUtils.setField(queue, "id", 4L);
        Claim claim = mock(Claim.class);
        when(claim.getCustomer()).thenReturn(user(3L, Role.CUSTOMER));
        when(claim.getAssignedOfficer()).thenReturn(null);
        when(claim.getStatus()).thenReturn(ClaimStatus.SUBMITTED);
        when(claim.getQueue()).thenReturn(queue);
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        ClaimService service = claimService(claims, mock(ClaimReviewRepository.class));

        assertThrows(AccessDeniedException.class, () -> service.startReview(officer, 22L));
        verify(claim, never()).transition(ClaimStatus.UNDER_REVIEW);
    }

    @Test
    void reassignmentRecordsSnapshotAndNotifiesCustomerAndBothOfficers() {
        ClaimRepository claims = mock(ClaimRepository.class);
        ClaimHistoryRepository history = mock(ClaimHistoryRepository.class);
        UserRepository users = mock(UserRepository.class);
        OfficerQueueMembershipRepository memberships = mock(OfficerQueueMembershipRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        AppUser oldOfficer = user(5L, Role.CLAIMS_OFFICER);
        AppUser newOfficer = user(6L, Role.CLAIMS_OFFICER);
        AppUser customer = user(3L, Role.CUSTOMER);
        ClaimQueue queue = new ClaimQueue("AUTO", "Auto");
        ReflectionTestUtils.setField(queue, "id", 4L);
        Claim claim = mock(Claim.class);
        when(claim.getId()).thenReturn(22L);
        when(claim.getCustomer()).thenReturn(customer);
        when(claim.getAssignedOfficer()).thenReturn(oldOfficer);
        when(claim.getQueue()).thenReturn(queue);
        when(claim.getStatus()).thenReturn(ClaimStatus.UNDER_REVIEW);
        when(claim.getClaimNumber()).thenReturn("CLM-22");
        when(claim.getPolicy()).thenReturn(new Policy("POL-1", customer,
                new InsuranceProduct("Home", "HOME", "Coverage", BigDecimal.TEN, BigDecimal.TEN),
                LocalDate.now(), LocalDate.now().plusYears(1)));
        when(claims.findByIdForUpdate(22L)).thenReturn(Optional.of(claim));
        when(users.findById(5L)).thenReturn(Optional.of(oldOfficer));
        when(users.findById(6L)).thenReturn(Optional.of(newOfficer));
        when(memberships.existsByOfficerIdAndQueueId(6L, 4L)).thenReturn(true);
        ClaimService service = new ClaimService(claims, history, mock(PolicyRepository.class),
                mock(AssignmentRepository.class), notifications, mock(SlaService.class),
                mock(ClaimTypeRepository.class), new ClaimTransitionValidator(), mock(ClaimCommentRepository.class),
                users, mock(ClaimReviewRepository.class), mock(ClaimSettlementRepository.class), memberships,
                mock(AuditLogService.class));

        service.reassign(oldOfficer, 22L, 6L);

        var historyCaptor = forClass(com.smartinsurance.entity.ClaimHistory.class);
        verify(history).save(historyCaptor.capture());
        assertEquals(5L, historyCaptor.getValue().getPreviousOfficerId());
        assertEquals(6L, historyCaptor.getValue().getNewOfficerId());
        verify(notifications).claimEvent(claim, "CLAIM_REASSIGNED");
        verify(notifications).notifyUser(oldOfficer, claim, "CLAIM_REASSIGNED", "Claim CLM-22 was reassigned.");
        verify(notifications).notifyUser(newOfficer, claim, "CLAIM_ASSIGNED", "Claim CLM-22 was assigned to you.");
    }

    @Test
    void slaPauseAndResumeAccumulatesPauseWithoutLosingDeadline() throws Exception {
        AppUser customer = user(2L, Role.CUSTOMER);
        Policy policy = new Policy("POL-1", customer,
                new InsuranceProduct("Home", "HOME", "Coverage", BigDecimal.TEN, BigDecimal.TEN),
                LocalDate.now(), LocalDate.now().plusYears(1));
        Claim claim = new Claim("CLM-1", policy, new ClaimQueue("GENERAL", "General"), "HOME",
                LocalDate.now(), "incident", BigDecimal.ONE, Instant.now().plusSeconds(3600));
        Instant deadline = claim.getSlaDueAt();
        ReflectionTestUtils.setField(claim, "slaPausedAt", Instant.now().minusSeconds(3));

        claim.resumeSla();

        assertNull(claim.getSlaPausedAt());
        assertTrue(claim.getSlaPausedSeconds() >= 3);
        assertTrue(claim.getEffectiveSlaDueAt().isAfter(deadline));
    }

    @Test
    void invalidClaimTransitionIsRejected() {
        ClaimTransitionValidator validator = new ClaimTransitionValidator();
        assertThrows(BusinessRuleException.class,
                () -> validator.validate(ClaimStatus.SUBMITTED, ClaimStatus.APPROVED));
    }

    private static ClaimService claimService(ClaimRepository claims, ClaimReviewRepository reviews) {
        return new ClaimService(claims, mock(ClaimHistoryRepository.class), mock(PolicyRepository.class),
                mock(AssignmentRepository.class), mock(NotificationService.class), mock(SlaService.class),
                mock(ClaimTypeRepository.class), new ClaimTransitionValidator(), mock(ClaimCommentRepository.class),
                mock(UserRepository.class), reviews, mock(ClaimSettlementRepository.class),
                mock(OfficerQueueMembershipRepository.class), mock(AuditLogService.class));
    }

    private static AppUser user(long id, Role role) {
        AppUser user = new AppUser("user" + id + "@example.test", "hash", role, "User " + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
