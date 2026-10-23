package com.smartinsurance.service;

import com.smartinsurance.dto.ClaimDtos.ActionRequest;
import com.smartinsurance.dto.ClaimDtos.ClaimResponse;
import com.smartinsurance.dto.ClaimDtos.HistoryResponse;
import com.smartinsurance.dto.ClaimDtos.SubmitRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.Claim;
import com.smartinsurance.entity.ClaimHistory;
import com.smartinsurance.entity.ClaimReview;
import com.smartinsurance.entity.ClaimSettlement;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.enums.PolicyStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.ClaimHistoryRepository;
import com.smartinsurance.repository.ClaimCommentRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimReviewRepository;
import com.smartinsurance.repository.ClaimSettlementRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClaimService {
    private final ClaimRepository claims;
    private final ClaimHistoryRepository history;
    private final PolicyRepository policies;
    private final AssignmentRepository assignments;
    private final NotificationService notifications;
    private final SlaService slas;
    private final ClaimTypeRepository claimTypes;
    private final ClaimTransitionValidator transitionValidator;
    private final ClaimCommentRepository comments;
    private final UserRepository users;
    private final ClaimReviewRepository reviews;
    private final ClaimSettlementRepository settlements;
    private final OfficerQueueMembershipRepository queueMemberships;
    private final AuditLogService audit;

    public ClaimService(ClaimRepository claims, ClaimHistoryRepository history, PolicyRepository policies,
                       AssignmentRepository assignments, NotificationService notifications, SlaService slas,
                       ClaimTypeRepository claimTypes, ClaimTransitionValidator transitionValidator,
                       ClaimCommentRepository comments, UserRepository users, ClaimReviewRepository reviews,
                       ClaimSettlementRepository settlements, OfficerQueueMembershipRepository queueMemberships,
                       AuditLogService audit) {
        this.claims = claims;
        this.history = history;
        this.policies = policies;
        this.assignments = assignments;
        this.notifications = notifications;
        this.slas = slas;
        this.claimTypes = claimTypes;
        this.transitionValidator = transitionValidator;
        this.comments = comments;
        this.users = users;
        this.reviews = reviews;
        this.settlements = settlements;
        this.queueMemberships = queueMemberships;
        this.audit = audit;
    }

    @Transactional
    public ClaimResponse submit(AppUser actor, SubmitRequest request) {
        return submitForOwner(actor, actor.getId(), request);
    }

    @Transactional
    public ClaimResponse submitAssisted(AppUser agent, Long customerId, SubmitRequest request) {
        if (agent.getRole() != Role.AGENT || !assignments.existsByAgentIdAndCustomerId(agent.getId(), customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Customer is not assigned to this agent");
        }
        return submitForOwner(agent, customerId, request);
    }

    private ClaimResponse submitForOwner(AppUser actor, Long customerId, SubmitRequest request) {
        Policy policy = policies.findById(request.policyId())
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
        if (!policy.getCustomer().getId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Policy does not belong to the claim owner");
        }
        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        if (policy.getStatus() != PolicyStatus.ACTIVE || today.isBefore(policy.getStartDate()) || today.isAfter(policy.getEndDate())) {
            throw new BusinessRuleException("Claims require an active policy within its coverage dates");
        }
        com.smartinsurance.entity.ClaimType claimType = claimTypes
                .findByProductIdAndNameIgnoreCaseAndActiveTrue(policy.getProduct().getId(), request.claimType())
                .orElseThrow(() -> new BusinessRuleException("Claim type is not available for the selected product"));
        if (!claimType.getQueue().isActive()) throw new BusinessRuleException("Claim type is routed to an inactive queue");
        if (request.incidentDate().isAfter(today) || request.incidentDate().isBefore(policy.getStartDate())
                || request.incidentDate().isAfter(policy.getEndDate())) {
            throw new BusinessRuleException("Incident date must fall within the active policy coverage period");
        }
        if (request.claimedAmount().compareTo(policy.getCoverageAmount()) > 0) {
            throw new BusinessRuleException("Claimed amount exceeds policy coverage");
        }
        com.smartinsurance.entity.ClaimQueue queue = claimType.getQueue();
        String number = "CLM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        Claim claim = claims.save(new Claim(number, policy, queue, request.claimType().trim(), request.incidentDate(),
                request.description().trim(), request.claimedAmount(),
                Instant.now().plus(slas.daysFor(request.claimType()), ChronoUnit.DAYS)));
        history.save(new ClaimHistory(claim, actor, "SUBMITTED", null, ClaimStatus.SUBMITTED, "Claim submitted"));
        notifications.claimEvent(claim, "CLAIM_SUBMITTED");
        return toResponse(claim);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listOwn(AppUser actor, Pageable pageable) {
        return claims.findByCustomerIdOrderByCreatedAtDescIdDesc(actor.getId(), pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listOfficer(AppUser actor, String status, Boolean overdue, String queueCode,
                                           Pageable pageable) {
        if (actor.getRole() != Role.CLAIMS_OFFICER && actor.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Claims officer access required");
        }
        if (status != null) {
            try { ClaimStatus.valueOf(status); }
            catch (IllegalArgumentException ex) { throw new BusinessRuleException("Unknown claim status filter"); }
        }
        if (queueCode != null && !queueCode.matches("[A-Za-z][A-Za-z0-9_-]{1,39}")) {
            throw new BusinessRuleException("Unknown queue filter");
        }
        String normalizedQueue = queueCode == null ? null : queueCode.toUpperCase(java.util.Locale.ROOT);
        Page<Claim> queue = actor.getRole() == Role.ADMIN
                ? claims.findAdminQueue(status, overdue, normalizedQueue, pageable)
                : claims.findOfficerQueue(actor.getId(), status, overdue, normalizedQueue, pageable);
        return queue.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listAssignedCustomerClaims(AppUser agent, Long customerId, Pageable pageable) {
        if (agent.getRole() != Role.AGENT
                || !assignments.existsByAgentIdAndCustomerId(agent.getId(), customerId)) {
            throw new org.springframework.security.access.AccessDeniedException("Customer is not assigned to this agent");
        }
        return claims.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId, pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> listAgentClaims(AppUser agent, Pageable pageable) {
        if (agent.getRole() != Role.AGENT) throw new org.springframework.security.access.AccessDeniedException("Agent access required");
        return claims.findClaimsForAgent(agent.getId(), pageable).map(this::toResponse);
    }

    @Transactional
    public void addAgentNote(AppUser agent, Long claimId, String note) {
        if (agent.getRole() != Role.AGENT || note == null || note.isBlank() || note.length() > 4000) {
            throw new BusinessRuleException("A non-empty agent note of at most 4000 characters is required");
        }
        Claim claim = lockedAccessible(agent, claimId);
        comments.save(new com.smartinsurance.entity.ClaimComment(claim, agent, note.trim()));
        history.save(new ClaimHistory(claim, agent, "AGENT_NOTE", claim.getStatus(), claim.getStatus(), "Internal note added"));
        audit.record(agent, "AGENT_CLAIM_NOTE_ADDED", "CLAIM", claim.getId().toString());
    }

    @Transactional
    public void addOfficerNote(AppUser officer, Long claimId, String note) {
        if ((officer.getRole() != Role.CLAIMS_OFFICER && officer.getRole() != Role.ADMIN)
                || note == null || note.isBlank() || note.length() > 4000) {
            throw new BusinessRuleException("A non-empty internal review note of at most 4000 characters is required");
        }
        Claim claim = officerClaim(officer, claimId);
        comments.save(new com.smartinsurance.entity.ClaimComment(claim, officer, note.trim()));
        history.save(new ClaimHistory(claim, officer, "REVIEW_NOTE", claim.getStatus(), claim.getStatus(), "Internal review note added"));
        audit.record(officer, "CLAIM_REVIEW_NOTE_ADDED", "CLAIM", claim.getId().toString());
    }

    @Transactional(readOnly = true)
    public List<ClaimNoteResponse> internalNotes(AppUser actor, Long claimId) {
        if (actor.getRole() != Role.CLAIMS_OFFICER && actor.getRole() != Role.ADMIN && actor.getRole() != Role.AGENT) {
            throw new org.springframework.security.access.AccessDeniedException("Internal claim notes are not accessible");
        }
        accessible(actor, claimId);
        return comments.findByClaimIdAndVisibilityOrderByCreatedAtAsc(claimId, "INTERNAL").stream()
                .map(note -> new ClaimNoteResponse(note.getAuthor().getFullName(), note.getBody(), note.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public List<ClaimCommentResponse> customerComments(AppUser actor, Long claimId) {
        accessible(actor, claimId);
        return comments.findByClaimIdAndVisibilityOrderByCreatedAtAsc(claimId, "CUSTOMER").stream()
                .map(comment -> new ClaimCommentResponse(comment.getAuthor().getFullName(), comment.getBody(),
                        comment.getCreatedAt())).toList();
    }

    @Transactional
    public void addCustomerComment(AppUser customer, Long claimId, String body) {
        if (customer.getRole() != Role.CUSTOMER || body == null || body.isBlank() || body.length() > 4000) {
            throw new BusinessRuleException("A non-empty customer comment of at most 4000 characters is required");
        }
        Claim claim = lockedAccessible(customer, claimId);
        if (!claim.getCustomer().getId().equals(customer.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the owning customer may comment");
        }
        if (claim.isClosed()) throw new BusinessRuleException("Closed claims do not accept comments");
        comments.save(new com.smartinsurance.entity.ClaimComment(claim, customer, body.trim(), "CUSTOMER"));
        history.save(new ClaimHistory(claim, customer, "CUSTOMER_COMMENT", claim.getStatus(), claim.getStatus(),
                "Customer comment added"));
        audit.record(customer, "CUSTOMER_COMMENT_ADDED", "CLAIM", claim.getId().toString());
        if (claim.getAssignedOfficer() != null) {
            notifications.notifyUser(claim.getAssignedOfficer(), claim, "CUSTOMER_COMMENT",
                    "A customer added a comment to claim " + claim.getClaimNumber());
        }
    }

    @Transactional(readOnly = true)
    public ClaimResponse get(AppUser actor, Long claimId) {
        return toResponse(accessible(actor, claimId));
    }
    @Transactional(readOnly = true)
    public List<SettlementResponse> settlements(AppUser actor, Long claimId) {
        accessible(actor, claimId);
        return settlements.findByClaimIdOrderByCreatedAtAsc(claimId).stream()
                .map(item -> new SettlementResponse(item.getId(), item.getReference(), item.getAmount(), item.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> summaryLines(AppUser actor, Long claimId) {
        Claim claim = accessible(actor, claimId);
        return List.of("Claim number: " + claim.getClaimNumber(), "Status: " + claim.getStatus(),
                "Claim type: " + claim.getClaimType(), "Policy number: " + claim.getPolicy().getPolicyNumber(),
                "Incident date: " + claim.getIncidentDate(), "Claimed amount: " + claim.getClaimedAmount(),
                "Approved amount: " + String.valueOf(claim.getApprovedAmount()),
                "Settlement amount: " + String.valueOf(claim.getSettlementAmount()),
                "Description: " + claim.getDescription(), "Submitted at: " + claim.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<HistoryResponse> timeline(AppUser actor, Long claimId) {
        Claim claim = accessible(actor, claimId);
        return history.findByClaimIdOrderByCreatedAtAsc(claim.getId()).stream()
                .filter(h -> actor.getRole() != Role.CUSTOMER || "CUSTOMER".equals(h.getVisibility()))
                .map(h -> new HistoryResponse(h.getAction(), hActorName(h), h.getActorRole(),
                        h.getPreviousStatus(), h.getNewStatus(),
                        h.getPreviousAmount(), h.getNewAmount(), h.getPreviousOfficerId(), h.getNewOfficerId(),
                        h.getRemarks(), h.getCreatedAt())).toList();
    }

    @Transactional
    public ClaimResponse startReview(AppUser actor, Long claimId) {
        requireOfficer(actor);
        Claim claim = lockedAccessible(actor, claimId);
        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new BusinessRuleException("Only submitted claims can enter review");
        }
        if (claim.getAssignedOfficer() != null && !claim.getAssignedOfficer().getId().equals(actor.getId())
                && actor.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Claim is assigned to another officer");
        }
        boolean assignedToActor = claim.getAssignedOfficer() != null
                && claim.getAssignedOfficer().getId().equals(actor.getId());
        if (actor.getRole() != Role.ADMIN && !assignedToActor
                && !queueMemberships.existsByOfficerIdAndQueueId(actor.getId(), claim.getQueue().getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Officer is not a member of this claims queue");
        }
        Long previousOfficerId = claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId();
        claim.setAssignedOfficer(actor);
        reviews.save(new ClaimReview(claim, actor));
        transition(claim, actor, ClaimStatus.UNDER_REVIEW, "Review started", previousOfficerId, actor.getId());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse requestInformation(AppUser actor, Long claimId, ActionRequest request) {
        requireOfficer(actor);
        Claim claim = officerClaim(actor, claimId);
        requireText(request, "A clear information request is required");
        updateReview(claimId, request.remarks(), null);
        transition(claim, actor, ClaimStatus.INFO_REQUESTED, request.remarks());
        claim.pauseSla();
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse respond(AppUser actor, Long claimId, ActionRequest request) {
        Claim claim = lockedAccessible(actor, claimId);
        if (!claim.getCustomer().getId().equals(actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the owning customer may respond");
        }
        if (claim.getStatus() != ClaimStatus.INFO_REQUESTED) {
            throw new BusinessRuleException("Claim is not waiting for customer information");
        }
        requireText(request, "A response is required");
        updateReview(claimId, "Customer response: " + request.remarks(), null);
        transition(claim, actor, ClaimStatus.UNDER_REVIEW, request.remarks());
        claim.resumeSla();
        if (claim.getAssignedOfficer() != null) {
            notifications.notifyUser(claim.getAssignedOfficer(), claim, "CUSTOMER_RESPONSE",
                    "Customer response received for claim " + claim.getClaimNumber());
        }
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse approve(AppUser actor, Long claimId, ActionRequest request) {
        requireOfficer(actor);
        Claim claim = officerClaim(actor, claimId);
        requireText(request, "Adjudication remarks are required");
        if (request.amount() == null || request.amount().signum() <= 0
                || request.amount().compareTo(claim.getClaimedAmount()) > 0
                || request.amount().compareTo(claim.getPolicy().getCoverageAmount()) > 0) {
            throw new BusinessRuleException("Approved amount must be positive and within the claimed and covered amounts");
        }
        updateReview(claimId, request.remarks(), "APPROVED");
        claim.approve(request.amount());
        history.save(new ClaimHistory(claim, actor, "APPROVAL_AMOUNT", claim.getStatus(), claim.getStatus(),
                request.remarks(), null, request.amount()));
        transition(claim, actor, ClaimStatus.APPROVED, request.remarks());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse reject(AppUser actor, Long claimId, ActionRequest request) {
        requireOfficer(actor);
        Claim claim = officerClaim(actor, claimId);
        requireText(request, "A rejection reason is required");
        updateReview(claimId, request.remarks(), "REJECTED");
        transition(claim, actor, ClaimStatus.REJECTED, request.remarks());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse settle(AppUser actor, Long claimId, ActionRequest request) {
        requireOfficer(actor);
        Claim claim = officerClaim(actor, claimId);
        requireText(request, "A settlement reference is required");
        String reference = request.remarks().trim();
        if (reference.length() > 100) throw new BusinessRuleException("Settlement reference must be at most 100 characters");
        if (settlements.existsByReference(reference)) throw new BusinessRuleException("Settlement reference already exists");
        BigDecimal amount = request.amount() == null ? claim.getApprovedAmount() : request.amount();
        if (amount == null || amount.signum() <= 0 || amount.compareTo(claim.getApprovedAmount()) > 0) {
            throw new BusinessRuleException("Settlement amount must be positive and cannot exceed approved amount");
        }
        settlements.save(new ClaimSettlement(claim, actor, reference, amount));
        claim.settle(amount);
        history.save(new ClaimHistory(claim, actor, "SETTLEMENT_AMOUNT", claim.getStatus(), claim.getStatus(),
                request.remarks(), claim.getApprovedAmount(), amount));
        transition(claim, actor, ClaimStatus.SETTLED, request.remarks());
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse reassign(AppUser actor, Long claimId, Long officerId) {
        requireOfficer(actor);
        Claim claim = officerClaim(actor, claimId);
        if (claim.isClosed()) throw new BusinessRuleException("Closed claims cannot be reassigned");
        AppUser target = users.findById(officerId).orElseThrow(() -> new ResourceNotFoundException("Claims officer not found"));
        if (target.getRole() != Role.CLAIMS_OFFICER || !target.isActive()) {
            throw new BusinessRuleException("Reassignment target must be an active claims officer");
        }
        Long previousId = claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId();
        if (target.getId().equals(previousId)) {
            throw new BusinessRuleException("Claim is already assigned to this officer");
        }
        if (!queueMemberships.existsByOfficerIdAndQueueId(target.getId(), claim.getQueue().getId())) {
            throw new BusinessRuleException("Reassignment target is not a member of this claim's queue");
        }
        claim.setAssignedOfficer(target);
        history.save(new ClaimHistory(claim, actor, "REASSIGNED", claim.getStatus(), claim.getStatus(),
                "Claims officer assignment changed", previousId, target.getId()));
        audit.record(actor, "CLAIM_REASSIGNED", "CLAIM", claim.getId().toString());
        notifications.claimEvent(claim, "CLAIM_REASSIGNED");
        if (previousId != null && !previousId.equals(target.getId())) {
            users.findById(previousId).ifPresent(previous -> notifications.notifyUser(previous, claim,
                    "CLAIM_REASSIGNED", "Claim " + claim.getClaimNumber() + " was reassigned."));
        }
        notifications.notifyUser(target, claim, "CLAIM_ASSIGNED", "Claim " + claim.getClaimNumber() + " was assigned to you.");
        return toResponse(claim);
    }

    @Transactional
    public ClaimResponse withdraw(AppUser actor, Long claimId) {
        Claim claim = lockedAccessible(actor, claimId);
        if (!claim.getCustomer().getId().equals(actor.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Only the owning customer may withdraw");
        }
        transition(claim, actor, ClaimStatus.WITHDRAWN, "Withdrawn by customer");
        return toResponse(claim);
    }

    private void transition(Claim claim, AppUser actor, ClaimStatus next, String remarks) {
        transition(claim, actor, next, remarks, null, null);
    }

    private void transition(Claim claim, AppUser actor, ClaimStatus next, String remarks,
                            Long previousOfficerId, Long newOfficerId) {
        ClaimStatus previous = claim.getStatus();
        transitionValidator.validate(previous, next);
        claim.transition(next);
        if (previousOfficerId != null || newOfficerId != null) {
            history.save(new ClaimHistory(claim, actor, next.name(), previous, next, remarks,
                    previousOfficerId, newOfficerId));
        } else {
            history.save(new ClaimHistory(claim, actor, next.name(), previous, next, remarks));
        }
        notifications.claimEvent(claim, next.name());
    }

    private void updateReview(Long claimId, String notes, String outcome) {
        ClaimReview review = reviews.findFirstByClaimIdAndClosedAtIsNullOrderByCreatedAtDesc(claimId)
                .orElseThrow(() -> new BusinessRuleException("Claim has no active review record"));
        if (outcome == null) review.appendNotes(notes);
        else review.close(outcome, notes);
    }

    private Claim accessible(AppUser actor, Long id) {
        Claim claim = claims.findById(id).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        authorizeAccess(actor, claim);
        return claim;
    }

    private Claim lockedAccessible(AppUser actor, Long id) {
        Claim claim = claims.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        authorizeAccess(actor, claim);
        return claim;
    }

    private void authorizeAccess(AppUser actor, Claim claim) {
        if (actor.getRole() == Role.ADMIN || claim.getCustomer().getId().equals(actor.getId())) {
            return;
        }
        if (actor.getRole() == Role.AGENT && assignments.existsByAgentIdAndCustomerId(actor.getId(), claim.getCustomer().getId())) {
            return;
        }
        if (actor.getRole() == Role.CLAIMS_OFFICER && claim.getAssignedOfficer() != null
                && claim.getAssignedOfficer().getId().equals(actor.getId())) {
            return;
        }
        if (actor.getRole() == Role.CLAIMS_OFFICER && claim.getAssignedOfficer() == null
                && claim.getStatus() == ClaimStatus.SUBMITTED
                && queueMemberships.existsByOfficerIdAndQueueId(actor.getId(), claim.getQueue().getId())) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException("You cannot access this claim");
    }

    private Claim officerClaim(AppUser actor, Long id) {
        Claim claim = lockedAccessible(actor, id);
        if (actor.getRole() != Role.ADMIN && (claim.getAssignedOfficer() == null
                || !claim.getAssignedOfficer().getId().equals(actor.getId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Claim is not assigned to this officer");
        }
        return claim;
    }

    private void requireOfficer(AppUser actor) {
        if (actor.getRole() != Role.CLAIMS_OFFICER && actor.getRole() != Role.ADMIN) {
            throw new org.springframework.security.access.AccessDeniedException("Claims officer access required");
        }
    }

    private void requireText(ActionRequest request, String message) {
        if (request == null || request.remarks() == null || request.remarks().isBlank()) {
            throw new BusinessRuleException(message);
        }
    }

    private String hActorName(ClaimHistory h) { return h.getActor().getFullName(); }

    private ClaimResponse toResponse(Claim claim) {
        return new ClaimResponse(claim.getId(), claim.getClaimNumber(), claim.getCustomer().getId(),
                claim.getCustomer().getFullName(), claim.getPolicy().getId(), claim.getClaimType(),
                claim.getIncidentDate(), claim.getDescription(), claim.getClaimedAmount(), claim.getPolicy().getCoverageAmount(),
                claim.getApprovedAmount(), claim.getSettlementAmount(), claim.getStatus(),
                claim.getAssignedOfficer() == null ? null : claim.getAssignedOfficer().getId(),
                claim.getQueue().getCode(), claim.getQueue().getName(),
                claim.getEffectiveSlaDueAt(), claim.isOverdue(), claim.getCreatedAt());
    }
    public record ClaimNoteResponse(String author, String note, Instant createdAt) { }
    public record ClaimCommentResponse(String author, String comment, Instant createdAt) { }
    public record SettlementResponse(Long id, String reference, BigDecimal amount, Instant createdAt) { }
}
