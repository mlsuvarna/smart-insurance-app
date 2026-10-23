package com.smartinsurance.service;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.entity.OfficerQueueMembership;
import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class ClaimQueueService {
    private static final List<ClaimStatus> CLOSED = List.of(ClaimStatus.REJECTED, ClaimStatus.SETTLED, ClaimStatus.WITHDRAWN);
    private final ClaimQueueRepository queues;
    private final ClaimRepository claims;
    private final ClaimTypeRepository claimTypes;
    private final OfficerQueueMembershipRepository memberships;
    private final UserRepository users;
    private final AuditLogService audit;

    public ClaimQueueService(ClaimQueueRepository queues, ClaimRepository claims, ClaimTypeRepository claimTypes,
                             OfficerQueueMembershipRepository memberships, UserRepository users, AuditLogService audit) {
        this.queues = queues;
        this.claims = claims;
        this.claimTypes = claimTypes;
        this.memberships = memberships;
        this.users = users;
        this.audit = audit;
    }

    @Transactional
    public ClaimQueue create(AppUser actor, String rawCode, String rawName) {
        requireAdmin(actor);
        String code = rawCode.trim().toUpperCase(Locale.ROOT);
        String name = rawName.trim();
        if (!code.matches("[A-Z][A-Z0-9_-]{1,39}") || name.isBlank()) {
            throw new BusinessRuleException("Queue code or name is invalid");
        }
        if (queues.existsByCodeIgnoreCase(code) || queues.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Queue code and name must be unique");
        }
        ClaimQueue queue = queues.save(new ClaimQueue(code, name));
        audit.record(actor, "CLAIM_QUEUE_CREATED", "CLAIM_QUEUE", code);
        return queue;
    }

    @Transactional
    public ClaimQueue update(AppUser actor, String code, String rawName, boolean active) {
        requireAdmin(actor);
        ClaimQueue queue = queues.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Claims queue not found"));
        String name = rawName.trim();
        if (name.isBlank() || (queues.existsByNameIgnoreCase(name) && !queue.getName().equalsIgnoreCase(name))) {
            throw new BusinessRuleException("Queue name must be non-empty and unique");
        }
        if (!active && (claimTypes.existsByQueueIdAndActiveTrue(queue.getId())
                || claims.existsByQueueIdAndStatusNotIn(queue.getId(), CLOSED))) {
            throw new BusinessRuleException("Reroute active claim types and resolve open claims before deactivating this queue");
        }
        queue.update(name, active);
        audit.record(actor, active ? "CLAIM_QUEUE_UPDATED" : "CLAIM_QUEUE_DEACTIVATED",
                "CLAIM_QUEUE", queue.getCode());
        return queue;
    }

    @Transactional
    public void addOfficer(AppUser actor, String code, Long officerId) {
        requireAdmin(actor);
        ClaimQueue queue = findQueue(code);
        if (!queue.isActive()) throw new BusinessRuleException("Cannot add officers to an inactive queue");
        AppUser officer = users.findById(officerId)
                .orElseThrow(() -> new ResourceNotFoundException("Claims officer not found"));
        if (officer.getRole() != Role.CLAIMS_OFFICER || !officer.isActive()) {
            throw new BusinessRuleException("Queue membership requires an active claims officer");
        }
        if (!memberships.existsByOfficerIdAndQueueId(officer.getId(), queue.getId())) {
            memberships.save(new OfficerQueueMembership(queue, officer, actor));
            audit.record(actor, "CLAIMS_OFFICER_ADDED_TO_QUEUE", "CLAIM_QUEUE", queue.getCode() + ":" + officer.getId());
        }
    }

    @Transactional
    public void removeOfficer(AppUser actor, String code, Long officerId) {
        requireAdmin(actor);
        ClaimQueue queue = findQueue(code);
        OfficerQueueMembership membership = memberships.findByOfficerIdAndQueueId(officerId, queue.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Claims officer is not in the queue"));
        if (claims.existsByQueueIdAndAssignedOfficerIdAndStatusNotIn(queue.getId(), officerId, CLOSED)) {
            throw new BusinessRuleException("Reassign open claims in this queue before removing membership");
        }
        memberships.delete(membership);
        audit.record(actor, "CLAIMS_OFFICER_REMOVED_FROM_QUEUE", "CLAIM_QUEUE", queue.getCode() + ":" + officerId);
    }

    private ClaimQueue findQueue(String code) {
        return queues.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Claims queue not found"));
    }

    private void requireAdmin(AppUser actor) {
        if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Administrator authorization required");
    }
}
