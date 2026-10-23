package com.smartinsurance.controller;

import com.smartinsurance.dto.PolicyDtos.PolicyResponse;
import com.smartinsurance.dto.ClaimDtos.ClaimResponse;
import com.smartinsurance.dto.ClaimDtos.SubmitRequest;
import com.smartinsurance.entity.AgentCustomerAssignment;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.service.ClaimService;
import com.smartinsurance.service.PolicyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;
import com.smartinsurance.util.PageRequests;

@RestController
@RequestMapping("/api/v1/agent")
@PreAuthorize("hasRole('AGENT')")
public class AgentController {
    private final AssignmentRepository assignments;
    private final PolicyService policies;
    private final ClaimService claims;
    public AgentController(AssignmentRepository assignments, PolicyService policies, ClaimService claims) {
        this.assignments = assignments; this.policies = policies; this.claims = claims;
    }
    @GetMapping("/customers")
    public Page<CustomerSummary> customers(@AuthenticationPrincipal AppUser agent,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return assignments.findByAgentId(agent.getId(), PageRequests.of(page, size)).map(a -> new CustomerSummary(
                a.getCustomer().getId(), a.getCustomer().getFullName(), a.getCustomer().getEmail()));
    }
    @GetMapping("/claims")
    public Page<ClaimResponse> assignedClaims(@AuthenticationPrincipal AppUser agent,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return claims.listAgentClaims(agent, PageRequests.of(page, size));
    }
    @GetMapping("/customers/{customerId}/policies")
    public Page<PolicyResponse> policies(@AuthenticationPrincipal AppUser agent, @PathVariable Long customerId,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return policies.assignedCustomerPolicies(agent, customerId, PageRequests.of(page, size));
    }
    @GetMapping("/customers/{customerId}/claims")
    public Page<ClaimResponse> claims(@AuthenticationPrincipal AppUser agent, @PathVariable Long customerId,
                                      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return claims.listAssignedCustomerClaims(agent, customerId, PageRequests.of(page, size));
    }
    @PostMapping("/customers/{customerId}/claims")
    public ClaimResponse submitForCustomer(@AuthenticationPrincipal AppUser agent, @PathVariable Long customerId,
                                           @Valid @RequestBody SubmitRequest request) {
        return claims.submitAssisted(agent, customerId, request);
    }
    @PostMapping("/claims/{claimId}/notes")
    public void note(@AuthenticationPrincipal AppUser agent, @PathVariable Long claimId, @Valid @RequestBody NoteRequest request) {
        claims.addAgentNote(agent, claimId, request.note());
    }
    public record CustomerSummary(Long id, String fullName, String email) { }
    public record NoteRequest(@NotBlank @Size(max = 4000) String note) { }
}
