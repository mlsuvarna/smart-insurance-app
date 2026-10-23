package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinsurance.entity.AgentCustomerAssignment;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.AgentAssignmentHistoryRepository;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AgentAssignmentServiceTest {
    @Test
    void assignmentRequiresActiveAgentAndCustomerAndRecordsHistory() {
        UserRepository users = mock(UserRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AgentAssignmentHistoryRepository history = mock(AgentAssignmentHistoryRepository.class);
        AppUser admin = user(1L, Role.ADMIN);
        AppUser agent = user(2L, Role.AGENT);
        AppUser customer = user(3L, Role.CUSTOMER);
        when(users.findById(2L)).thenReturn(Optional.of(agent));
        when(users.findById(3L)).thenReturn(Optional.of(customer));
        when(assignments.existsByAgentIdAndCustomerId(2L, 3L)).thenReturn(false);
        AgentAssignmentService service = new AgentAssignmentService(users, assignments, history,
                mock(AuditLogService.class));

        service.assign(admin, 2L, 3L);

        verify(assignments).save(any(AgentCustomerAssignment.class));
        verify(history).save(any());
    }

    @Test
    void duplicateAssignmentDoesNotCreateDuplicateHistory() {
        UserRepository users = mock(UserRepository.class);
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AgentAssignmentHistoryRepository history = mock(AgentAssignmentHistoryRepository.class);
        when(users.findById(2L)).thenReturn(Optional.of(user(2L, Role.AGENT)));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, Role.CUSTOMER)));
        when(assignments.existsByAgentIdAndCustomerId(2L, 3L)).thenReturn(true);
        AgentAssignmentService service = new AgentAssignmentService(users, assignments, history,
                mock(AuditLogService.class));

        service.assign(user(1L, Role.ADMIN), 2L, 3L);

        verify(assignments, never()).save(any(AgentCustomerAssignment.class));
        verify(history, never()).save(any());
    }

    @Test
    void inactiveOrWrongRoleParticipantsAreRejected() {
        UserRepository users = mock(UserRepository.class);
        AppUser inactiveAgent = user(2L, Role.AGENT);
        inactiveAgent.setActive(false);
        when(users.findById(2L)).thenReturn(Optional.of(inactiveAgent));
        when(users.findById(3L)).thenReturn(Optional.of(user(3L, Role.CUSTOMER)));
        AgentAssignmentService service = new AgentAssignmentService(users, mock(AssignmentRepository.class),
                mock(AgentAssignmentHistoryRepository.class), mock(AuditLogService.class));

        assertThrows(BusinessRuleException.class, () -> service.assign(user(1L, Role.ADMIN), 2L, 3L));
    }

    @Test
    void assignmentRemovalWritesRemovalHistoryBeforeDeleting() {
        AssignmentRepository assignments = mock(AssignmentRepository.class);
        AgentAssignmentHistoryRepository history = mock(AgentAssignmentHistoryRepository.class);
        AgentCustomerAssignment assignment = new AgentCustomerAssignment(user(2L, Role.AGENT),
                user(3L, Role.CUSTOMER));
        when(assignments.findByAgentIdAndCustomerId(2L, 3L)).thenReturn(Optional.of(assignment));
        AgentAssignmentService service = new AgentAssignmentService(mock(UserRepository.class), assignments, history,
                mock(AuditLogService.class));

        service.remove(user(1L, Role.ADMIN), 2L, 3L);

        verify(history).save(any());
        verify(assignments).delete(assignment);
    }

    private static AppUser user(long id, Role role) {
        AppUser user = new AppUser("user" + id + "@example.test", "hash", role, "User " + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
