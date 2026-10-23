package com.smartinsurance.controller;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.AgentAssignmentHistoryRepository;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.repository.UserRepository;
import com.smartinsurance.repository.AuditLogRepository;
import com.smartinsurance.service.AuditLogService;
import com.smartinsurance.service.ClaimQueueService;
import com.smartinsurance.service.AgentAssignmentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserRepository users;
    private final AssignmentRepository assignments;
    private final AuditLogService audit;
    private final AuditLogRepository auditRepository;
    private final AgentAssignmentHistoryRepository assignmentHistory;
    private final ClaimQueueRepository queues;
    private final OfficerQueueMembershipRepository queueMemberships;
    private final ClaimQueueService claimQueueService;
    private final AgentAssignmentService agentAssignmentService;
    public AdminController(UserRepository users, AssignmentRepository assignments, AuditLogService audit,
                           AuditLogRepository auditRepository, AgentAssignmentHistoryRepository assignmentHistory,
                           ClaimQueueRepository queues, OfficerQueueMembershipRepository queueMemberships,
                           ClaimQueueService claimQueueService, AgentAssignmentService agentAssignmentService) {
        this.users = users;
        this.assignments = assignments;
        this.audit = audit;
        this.auditRepository = auditRepository;
        this.assignmentHistory = assignmentHistory;
        this.queues = queues;
        this.queueMemberships = queueMemberships;
        this.claimQueueService = claimQueueService;
        this.agentAssignmentService = agentAssignmentService;
    }
    @GetMapping("/users")
    public Page<UserView> users(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                @RequestParam(required = false) Role role) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.ASC, "id"));
        Page<AppUser> pageOfUsers = role == null ? users.findAll(pageable)
                : users.findByRoleAndActiveTrueOrderByFullNameAscIdAsc(role, pageable);
        return pageOfUsers.map(u ->
                new UserView(u.getId(), u.getEmail(), u.getFullName(), u.getRole().name(), u.isActive()));
    }
    @PutMapping("/users/{id}/role")
    @Transactional
    public UserView role(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                         @Valid @RequestBody RoleRequest request) {
        AppUser user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (actor.getId().equals(id)) throw new BusinessRuleException("Administrators cannot change their own role");
        user.setRole(request.role());
        users.save(user);
        audit.record(actor, "USER_ROLE_CHANGED", "USER", user.getId().toString());
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.isActive());
    }
    @PatchMapping("/users/{id}/activation")
    @Transactional
    public UserView activation(@AuthenticationPrincipal AppUser actor, @PathVariable Long id,
                               @RequestParam boolean active) {
        AppUser user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (actor.getId().equals(id) && !active) throw new BusinessRuleException("Administrators cannot deactivate their own account");
        user.setActive(active);
        users.save(user);
        audit.record(actor, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", user.getId().toString());
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole().name(), user.isActive());
    }
    @PostMapping("/assignments")
    public void assign(@AuthenticationPrincipal AppUser actor, @Valid @RequestBody AssignmentRequest request) {
        agentAssignmentService.assign(actor, request.agentId(), request.customerId());
    }
    @GetMapping("/assignments")
    public Page<AssignmentView> assignmentList(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return assignments.findAllByOrderByIdAsc(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(a -> new AssignmentView(a.getAgent().getId(), a.getAgent().getFullName(),
                        a.getCustomer().getId(), a.getCustomer().getFullName(), a.getCustomer().getEmail()));
    }
    @GetMapping("/assignment-history")
    @Transactional(readOnly = true)
    public Page<AssignmentHistoryView> assignmentHistory(@RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return assignmentHistory.findAllByOrderByCreatedAtDescIdDesc(
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(event -> new AssignmentHistoryView(event.getActor().getId(), event.getAgent().getId(),
                        event.getAgent().getFullName(), event.getCustomer().getId(), event.getCustomer().getFullName(),
                        event.getAction(), event.getCreatedAt()));
    }
    @GetMapping("/queues")
    @Transactional(readOnly = true)
    public List<QueueView> queues(@RequestParam(defaultValue = "false") boolean activeOnly) {
        return (activeOnly ? queues.findByActiveTrueOrderByNameAsc() : queues.findAllByOrderByNameAsc())
                .stream().map(queue -> new QueueView(queue.getId(), queue.getCode(), queue.getName(),
                        queue.isActive(), queue.getCreatedAt(), queue.getUpdatedAt())).toList();
    }
    @PostMapping("/queues")
    @Transactional
    public QueueView createQueue(@AuthenticationPrincipal AppUser actor, @Valid @RequestBody QueueRequest request) {
        ClaimQueue queue = claimQueueService.create(actor, request.code(), request.name());
        return queueView(queue);
    }
    @PutMapping("/queues/{code}")
    @Transactional
    public QueueView updateQueue(@AuthenticationPrincipal AppUser actor, @PathVariable String code,
                                 @Valid @RequestBody QueueUpdateRequest request) {
        ClaimQueue queue = claimQueueService.update(actor, code, request.name(), request.active());
        return queueView(queue);
    }
    @GetMapping("/queues/{code}/members")
    @Transactional(readOnly = true)
    public Page<QueueMemberView> queueMembers(@PathVariable String code,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        queues.findByCodeIgnoreCase(code).orElseThrow(() -> new ResourceNotFoundException("Claims queue not found"));
        return queueMemberships.findAllByQueueCodeOrderByOfficerFullNameAsc(code.toUpperCase(java.util.Locale.ROOT),
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(member -> new QueueMemberView(member.getOfficer().getId(), member.getOfficer().getFullName(),
                        member.getOfficer().getEmail(), member.getAssignedBy().getId(), member.getCreatedAt(),
                        member.getQueue().getCode(), member.getQueue().getName()));
    }
    @PostMapping("/queues/{code}/members")
    @Transactional
    public void addQueueMember(@AuthenticationPrincipal AppUser actor, @PathVariable String code,
                               @Valid @RequestBody QueueMemberRequest request) {
        claimQueueService.addOfficer(actor, code, request.officerId());
    }
    @org.springframework.web.bind.annotation.DeleteMapping("/queues/{code}/members/{officerId}")
    @Transactional
    public void removeQueueMember(@AuthenticationPrincipal AppUser actor, @PathVariable String code,
                                  @PathVariable Long officerId) {
        claimQueueService.removeOfficer(actor, code, officerId);
    }
    @org.springframework.web.bind.annotation.DeleteMapping("/assignments")
    public void removeAssignment(@AuthenticationPrincipal AppUser actor, @RequestParam Long agentId,
                                 @RequestParam Long customerId) {
        agentAssignmentService.remove(actor, agentId, customerId);
    }
    @GetMapping("/audit")
    @Transactional(readOnly = true)
    public Page<AuditView> audit(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "50") int size) {
        return auditRepository.findAllByOrderByCreatedAtDescIdDesc(
                        PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100))))
                .map(entry -> new AuditView(entry.getActorId(), entry.getEventType(), entry.getResourceType(),
                        entry.getResourceId(), entry.getCorrelationId(), entry.getCreatedAt()));
    }
    public record RoleRequest(@NotNull Role role) { }
    public record AssignmentRequest(@NotNull Long agentId, @NotNull Long customerId) { }
    public record AssignmentView(Long agentId, String agentName, Long customerId, String customerName, String customerEmail) { }
    public record AssignmentHistoryView(Long actorId, Long agentId, String agentName, Long customerId,
                                        String customerName, String action, java.time.Instant createdAt) { }
    public record QueueMemberRequest(@NotNull Long officerId) { }
    public record QueueMemberView(Long officerId, String officerName, String email, Long assignedBy,
                                  java.time.Instant createdAt, String queueCode, String queueName) { }
    public record QueueRequest(@NotNull @jakarta.validation.constraints.Size(max = 40) String code,
                               @NotNull @jakarta.validation.constraints.Size(max = 100) String name) { }
    public record QueueUpdateRequest(@NotNull @jakarta.validation.constraints.Size(max = 100) String name,
                                     boolean active) { }
    public record QueueView(Long id, String code, String name, boolean active, java.time.Instant createdAt,
                            java.time.Instant updatedAt) { }
    public record UserView(Long id, String email, String fullName, String role, boolean active) { }
    public record AuditView(Long actorId, String eventType, String resourceType, String resourceId,
                            String correlationId, java.time.Instant createdAt) { }
    private QueueView queueView(ClaimQueue queue) {
        return new QueueView(queue.getId(), queue.getCode(), queue.getName(), queue.isActive(),
                queue.getCreatedAt(), queue.getUpdatedAt());
    }
}
