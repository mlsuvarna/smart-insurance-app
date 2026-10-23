package com.smartinsurance.controller;

import com.smartinsurance.dto.ClaimDtos.ActionRequest;
import com.smartinsurance.dto.ClaimDtos.ClaimResponse;
import com.smartinsurance.dto.ClaimDtos.HistoryResponse;
import com.smartinsurance.dto.ClaimDtos.SubmitRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.service.ClaimService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import com.smartinsurance.service.PdfService;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.enums.Role;
import com.smartinsurance.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/claims")
public class ClaimController {
    private final ClaimService claims;
    private final PdfService pdf;
    private final OfficerQueueMembershipRepository queueMemberships;
    private final ClaimQueueRepository queues;
    public ClaimController(ClaimService claims, PdfService pdf, OfficerQueueMembershipRepository queueMemberships,
                           ClaimQueueRepository queues) {
        this.claims = claims; this.pdf = pdf; this.queueMemberships = queueMemberships; this.queues = queues;
    }
    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ClaimResponse submit(@AuthenticationPrincipal AppUser user, @Valid @RequestBody SubmitRequest request) {
        return claims.submit(user, request);
    }
    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public Page<ClaimResponse> own(@AuthenticationPrincipal AppUser user,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return claims.listOwn(user, PageRequests.of(page, size));
    }
    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public Page<ClaimResponse> queue(@AuthenticationPrincipal AppUser user,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Boolean overdue,
                                     @RequestParam(required = false) String queueCode) {
        return claims.listOfficer(user, status, overdue, queueCode, PageRequests.of(page, size));
    }
    @GetMapping("/officers")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public Page<OfficerResponse> officers(@AuthenticationPrincipal AppUser user,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size,
                                          @RequestParam String queueCode) {
        ClaimQueue queue = queues.findByCodeIgnoreCase(queueCode)
                .filter(ClaimQueue::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Active claims queue not found"));
        if (user.getRole() == Role.CLAIMS_OFFICER
                && !queueMemberships.existsByOfficerIdAndQueueId(user.getId(), queue.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Officer is not a member of this claims queue");
        }
        return queueMemberships.findActiveMembers(queue.getCode(), Role.CLAIMS_OFFICER,
                        PageRequests.of(page, size))
                .map(member -> new OfficerResponse(member.getOfficer().getId(), member.getOfficer().getFullName()));
    }
    @GetMapping("/queues")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public List<QueueResponse> queues(@AuthenticationPrincipal AppUser user) {
        if (user.getRole() == Role.ADMIN) return queues.findByActiveTrueOrderByNameAsc().stream()
                .map(queue -> new QueueResponse(queue.getCode(), queue.getName())).toList();
        return queueMemberships.findByOfficerIdAndQueueActiveTrue(user.getId()).stream()
                .map(member -> new QueueResponse(member.getQueue().getCode(), member.getQueue().getName())).toList();
    }
    @GetMapping("/{id}")
    public ClaimResponse get(@AuthenticationPrincipal AppUser user, @PathVariable Long id) { return claims.get(user, id); }
    @GetMapping("/{id}/summary.pdf")
    public ResponseEntity<byte[]> summary(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        byte[] content = pdf.create("Insurance claim summary", claims.summaryLines(user, id));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=claim-summary.pdf").body(content);
    }
    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return claims.timeline(user, id);
    }
    @PostMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse startReview(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return claims.startReview(user, id);
    }
    @PostMapping("/{id}/information-request")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse information(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                     @Valid @RequestBody ActionRequest request) {
        return claims.requestInformation(user, id, request);
    }
    @PostMapping("/{id}/response")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ClaimResponse respond(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                 @Valid @RequestBody ActionRequest request) {
        return claims.respond(user, id, request);
    }
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse approve(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                @Valid @RequestBody ActionRequest request) {
        return claims.approve(user, id, request);
    }
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse reject(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                @Valid @RequestBody ActionRequest request) {
        return claims.reject(user, id, request);
    }
    @PostMapping("/{id}/settle")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse settle(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                @Valid @RequestBody ActionRequest request) {
        return claims.settle(user, id, request);
    }
    @PostMapping("/{id}/reassign")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public ClaimResponse reassign(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                 @Valid @RequestBody ReassignRequest request) {
        return claims.reassign(user, id, request.officerId());
    }
    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ClaimResponse withdraw(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return claims.withdraw(user, id);
    }
    @GetMapping("/{id}/notes")
    public List<ClaimService.ClaimNoteResponse> notes(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return claims.internalNotes(user, id);
    }
    @GetMapping("/{id}/settlements")
    public List<ClaimService.SettlementResponse> settlements(@AuthenticationPrincipal AppUser user, @PathVariable Long id) {
        return claims.settlements(user, id);
    }
    @PostMapping("/{id}/notes")
    @PreAuthorize("hasAnyRole('CLAIMS_OFFICER','ADMIN')")
    public void reviewNote(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                           @Valid @RequestBody NoteRequest request) {
        claims.addOfficerNote(user, id, request.note());
    }
    @GetMapping("/{id}/comments")
    public List<ClaimService.ClaimCommentResponse> comments(@AuthenticationPrincipal AppUser user,
                                                            @PathVariable Long id) {
        return claims.customerComments(user, id);
    }
    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('CUSTOMER')")
    public void customerComment(@AuthenticationPrincipal AppUser user, @PathVariable Long id,
                                @Valid @RequestBody CommentRequest request) {
        claims.addCustomerComment(user, id, request.comment());
    }
    public record CommentRequest(@jakarta.validation.constraints.NotBlank
                                 @jakarta.validation.constraints.Size(max = 4000) String comment) { }
    public record NoteRequest(@jakarta.validation.constraints.NotBlank
                              @jakarta.validation.constraints.Size(max = 4000) String note) { }
    public record ReassignRequest(@jakarta.validation.constraints.NotNull Long officerId) { }
    public record OfficerResponse(Long id, String fullName) { }
    public record QueueResponse(String code, String name) { }
}
