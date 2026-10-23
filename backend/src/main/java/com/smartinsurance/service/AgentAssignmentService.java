package com.smartinsurance.service;

import com.smartinsurance.entity.AgentAssignmentHistory;
import com.smartinsurance.entity.AgentCustomerAssignment;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.AgentAssignmentHistoryRepository;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;

@Service
public class AgentAssignmentService {
    private final UserRepository users;
    private final AssignmentRepository assignments;
    private final AgentAssignmentHistoryRepository history;
    private final AuditLogService audit;

    public AgentAssignmentService(UserRepository users, AssignmentRepository assignments,
                                  AgentAssignmentHistoryRepository history, AuditLogService audit) {
        this.users = users;
        this.assignments = assignments;
        this.history = history;
        this.audit = audit;
    }

    @Transactional
    public void assign(AppUser actor, Long agentId, Long customerId) {
        requireAdmin(actor);
        AppUser agent = users.findById(agentId).orElseThrow(() -> new ResourceNotFoundException("Agent not found"));
        AppUser customer = users.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
        if (agent.getRole() != Role.AGENT || customer.getRole() != Role.CUSTOMER
                || !agent.isActive() || !customer.isActive()) {
            throw new BusinessRuleException("Assignment requires active AGENT and CUSTOMER accounts");
        }
        if (!assignments.existsByAgentIdAndCustomerId(agent.getId(), customer.getId())) {
            assignments.save(new AgentCustomerAssignment(agent, customer));
            history.save(new AgentAssignmentHistory(actor, agent, customer, "ASSIGNED"));
            audit.record(actor, "AGENT_CUSTOMER_ASSIGNED", "CUSTOMER", customer.getId().toString());
        }
    }

    @Transactional
    public void remove(AppUser actor, Long agentId, Long customerId) {
        requireAdmin(actor);
        AgentCustomerAssignment assignment = assignments.findByAgentIdAndCustomerId(agentId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent/customer assignment not found"));
        history.save(new AgentAssignmentHistory(actor, assignment.getAgent(), assignment.getCustomer(), "REMOVED"));
        assignments.delete(assignment);
        audit.record(actor, "AGENT_CUSTOMER_ASSIGNMENT_REMOVED", "CUSTOMER", customerId.toString());
    }

    private void requireAdmin(AppUser actor) {
        if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Administrator authorization required");
    }
}
