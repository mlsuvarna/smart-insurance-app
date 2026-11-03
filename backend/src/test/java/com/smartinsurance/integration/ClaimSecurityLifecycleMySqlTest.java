package com.smartinsurance.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.smartinsurance.dto.ClaimDtos.ActionRequest;
import com.smartinsurance.dto.ClaimDtos.SubmitRequest;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.ClaimDocument;
import com.smartinsurance.entity.ClaimQueue;
import com.smartinsurance.entity.ClaimType;
import com.smartinsurance.entity.InsuranceProduct;
import com.smartinsurance.entity.OfficerQueueMembership;
import com.smartinsurance.entity.Payment;
import com.smartinsurance.entity.Policy;
import com.smartinsurance.enums.ClaimStatus;
import com.smartinsurance.enums.DocumentScanStatus;
import com.smartinsurance.enums.PolicyStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.repository.ClaimDocumentRepository;
import com.smartinsurance.repository.ClaimHistoryRepository;
import com.smartinsurance.repository.ClaimQueueRepository;
import com.smartinsurance.repository.ClaimRepository;
import com.smartinsurance.repository.ClaimTypeRepository;
import com.smartinsurance.repository.OfficerQueueMembershipRepository;
import com.smartinsurance.repository.PaymentRepository;
import com.smartinsurance.repository.PolicyRepository;
import com.smartinsurance.repository.ProductRepository;
import com.smartinsurance.repository.UserRepository;
import com.smartinsurance.security.JwtService;
import com.smartinsurance.service.ClaimService;
import com.smartinsurance.service.DocumentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "app.scanner.mode=development",
        "app.jwt.secret=test-only-jwt-secret-long-enough-for-hmac-signing",
        "PII_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ClaimSecurityLifecycleMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("smart_insurance")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired PolicyRepository policies;
    @Autowired PaymentRepository paymentRepository;
    @Autowired ClaimTypeRepository claimTypes;
    @Autowired ClaimQueueRepository queues;
    @Autowired OfficerQueueMembershipRepository memberships;
    @Autowired ClaimHistoryRepository history;
    @Autowired ClaimRepository claimRepository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ClaimDocumentRepository documents;
    @Autowired ClaimService claims;
    @Autowired DocumentService documentService;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;

    @Test
    @Transactional
    void validLifecyclePersistsHistoryAndKeepsOwnershipAndQueueBoundaries() throws Exception {
        Fixture fixture = fixture();
        memberships.save(new OfficerQueueMembership(fixture.queue(), fixture.officer(), fixture.admin()));
        var submitted = claims.submit(fixture.customer(), new SubmitRequest(fixture.policy().getId(), "HOME",
                LocalDate.now(), "Water damage", new BigDecimal("250.00")));
        var initialDocument = documentService.upload(fixture.customer(), submitted.id(),
                new MockMultipartFile("file", "evidence.pdf", "application/pdf",
                        "%PDF-1.4 initial evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals(DocumentScanStatus.FAILED, initialDocument.scanStatus());

        assertThrows(AccessDeniedException.class, () -> claims.get(fixture.otherCustomer(), submitted.id()));
        assertThrows(AccessDeniedException.class,
                () -> claims.listAssignedCustomerClaims(fixture.agent(), fixture.customer().getId(),
                        org.springframework.data.domain.PageRequest.of(0, 10)));

        mvc.perform(get("/api/v1/admin/queues").header("Authorization", "Bearer " + jwt.createToken(fixture.customer())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/queues").header("Authorization", "Bearer " + jwt.createToken(fixture.admin())))
                .andExpect(status().isOk());

        claims.startReview(fixture.officer(), submitted.id());
        assertEquals(ClaimStatus.UNDER_REVIEW, claims.get(fixture.customer(), submitted.id()).status());
        assertEquals(ClaimStatus.UNDER_REVIEW, claims.get(fixture.officer(), submitted.id()).status());
        claims.requestInformation(fixture.officer(), submitted.id(), new ActionRequest("Upload proof", null));
        assertEquals(ClaimStatus.INFO_REQUESTED, claims.get(fixture.customer(), submitted.id()).status());
        assertNotNull(claimRepository.findById(submitted.id()).orElseThrow().getSlaPausedAt());
        var responseDocument = documentService.upload(fixture.customer(), submitted.id(),
                new MockMultipartFile("file", "response.pdf", "application/pdf",
                        "%PDF-1.4 response evidence".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertEquals(DocumentScanStatus.FAILED, responseDocument.scanStatus());
        claims.respond(fixture.customer(), submitted.id(), new ActionRequest("Proof supplied", null));
        assertEquals(ClaimStatus.UNDER_REVIEW, claims.get(fixture.customer(), submitted.id()).status());
        assertNull(claimRepository.findById(submitted.id()).orElseThrow().getSlaPausedAt());
        claims.approve(fixture.officer(), submitted.id(), new ActionRequest("Approved", new BigDecimal("200.00")));
        assertEquals(ClaimStatus.APPROVED, claims.get(fixture.customer(), submitted.id()).status());
        claims.settle(fixture.officer(), submitted.id(), new ActionRequest("SET-TEST-1", new BigDecimal("200.00")));
        assertEquals(ClaimStatus.SETTLED, claims.get(fixture.customer(), submitted.id()).status());
        var historyEntries = history.findByClaimIdOrderByCreatedAtAsc(submitted.id());
        assertEquals(10, historyEntries.size());
        assertEquals("CLAIMS_OFFICER", historyEntries.get(2).getActorRole());
        assertEquals("Review started", historyEntries.get(2).getRemarks());
        assertNotNull(historyEntries.get(2).getCreatedAt());
        Long historyId = jdbc.queryForObject("SELECT id FROM claim_history WHERE claim_id = ? ORDER BY id LIMIT 1",
                Long.class, submitted.id());
        assertThrows(DataAccessException.class,
                () -> jdbc.update("UPDATE claim_history SET remarks = 'tampered' WHERE id = ?", historyId));
    }

    @Test
    @Transactional
    void pendingDocumentCannotBeDownloadedAndUnassignedOfficerCannotStartReview() {
        Fixture fixture = fixture();
        var submitted = claims.submit(fixture.customer(), new SubmitRequest(fixture.policy().getId(), "HOME",
                LocalDate.now(), "Water damage", new BigDecimal("100.00")));
        ClaimDocument document = documents.save(new ClaimDocument(claimRepository.findById(submitted.id()).orElseThrow(),
                fixture.customer(), "evidence.pdf", "claims/test/evidence.pdf", "application/pdf", 10));
        document.setScanResult(new com.smartinsurance.service.DocumentScanner.ScanResult(
                DocumentScanStatus.PENDING, "DEVELOPMENT_ONLY_NO_MALWARE_SCAN"));
        documents.flush();

        assertThrows(AccessDeniedException.class, () -> claims.startReview(fixture.officer(), submitted.id()));
        assertThrows(AccessDeniedException.class, () -> claims.get(fixture.officer(), submitted.id()));
        assertThrows(com.smartinsurance.exception.BusinessRuleException.class,
                () -> documentService.download(fixture.customer(), submitted.id(), document.getId()));
    }

    @Test
    @Transactional
    void officersSeeUnassignedClaimsOnlyInTheirActiveQueue() {
        Fixture fixture = fixture();
        AppUser autoOfficer = saveUser("auto-officer-" + UUID.randomUUID().toString().substring(0, 8),
                Role.CLAIMS_OFFICER);
        ClaimQueue autoQueue = queues.saveAndFlush(new ClaimQueue(
                "AUTO-" + UUID.randomUUID().toString().substring(0, 8), "Auto claims"));
        memberships.saveAndFlush(new OfficerQueueMembership(fixture.queue(), fixture.officer(), fixture.admin()));
        memberships.saveAndFlush(new OfficerQueueMembership(autoQueue, autoOfficer, fixture.admin()));
        claimTypes.saveAndFlush(new ClaimType(fixture.policy().getProduct(), autoQueue, "AUTO"));

        var generalClaim = claims.submit(fixture.customer(), new SubmitRequest(fixture.policy().getId(), "HOME",
                LocalDate.now(), "General queue incident", new BigDecimal("100.00")));
        var autoClaim = claims.submit(fixture.customer(), new SubmitRequest(fixture.policy().getId(), "AUTO",
                LocalDate.now(), "Auto queue incident", new BigDecimal("100.00")));

        var generalQueue = claims.listOfficer(fixture.officer(), null, null, null,
                false, false, false, PageRequest.of(0, 20));
        var autoOfficerQueue = claims.listOfficer(autoOfficer, null, null, null,
                false, false, false, PageRequest.of(0, 20));

        assertEquals("GENERAL", generalClaim.queueCode());
        assertTrue(generalQueue.getContent().stream().anyMatch(claim -> claim.id().equals(generalClaim.id())));
        assertFalse(generalQueue.getContent().stream().anyMatch(claim -> claim.id().equals(autoClaim.id())));
        assertTrue(autoOfficerQueue.getContent().stream().anyMatch(claim -> claim.id().equals(autoClaim.id())));
        assertFalse(autoOfficerQueue.getContent().stream().anyMatch(claim -> claim.id().equals(generalClaim.id())));
        assertEquals(0, claims.listOfficer(fixture.officer(), null, null, null,
                true, false, false, PageRequest.of(0, 1)).getTotalElements());
        assertEquals(1, claims.listOfficer(fixture.officer(), null, null, null,
                false, true, false, PageRequest.of(0, 1)).getTotalElements());

        claims.startReview(fixture.officer(), generalClaim.id());
        assertEquals(1, claims.listOfficer(fixture.officer(), null, null, null,
                true, false, false, PageRequest.of(0, 1)).getTotalElements());
        claims.requestInformation(fixture.officer(), generalClaim.id(), new ActionRequest("Additional evidence", null));
        assertEquals(1, claims.listOfficer(fixture.officer(), null, null, null,
                false, false, true, PageRequest.of(0, 1)).getTotalElements());
    }

    @Test
    void protectedEndpointsRequireJwtWhileOpenApiIsAvailable() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().is4xxClientError());
        mvc.perform(get("/api/v1/products").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().is4xxClientError());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @Transactional
    void customerCannotAccessAdminReports() throws Exception {
        Fixture fixture = fixture();
        mvc.perform(get("/api/v1/admin/reports/summary")
                        .param("from", LocalDate.now().minusDays(7).toString())
                        .param("to", LocalDate.now().toString())
                        .header("Authorization", "Bearer " + jwt.createToken(fixture.customer())))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    void adminCanListPaymentsWhileCustomerIsForbidden() throws Exception {
        Fixture fixture = fixture();
        Payment payment = paymentRepository.saveAndFlush(new Payment("PAY-LIST-" + UUID.randomUUID(),
                fixture.policy()));

        mvc.perform(get("/api/v1/admin/payments").param("page", "0").param("size", "20")
                        .header("Authorization", "Bearer " + jwt.createToken(fixture.admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reference").value(payment.getPaymentReference()))
                .andExpect(jsonPath("$.content[0].customerName").value(fixture.customer().getFullName()));
        mvc.perform(get("/api/v1/admin/payments")
                        .header("Authorization", "Bearer " + jwt.createToken(fixture.customer())))
                .andExpect(status().isForbidden());
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser admin = saveUser("admin-" + suffix, Role.ADMIN);
        AppUser customer = saveUser("customer-" + suffix, Role.CUSTOMER);
        AppUser otherCustomer = saveUser("other-" + suffix, Role.CUSTOMER);
        AppUser agent = saveUser("agent-" + suffix, Role.AGENT);
        AppUser officer = saveUser("officer-" + suffix, Role.CLAIMS_OFFICER);
        ClaimQueue queue = queues.findByCodeIgnoreCase("GENERAL").orElseThrow();
        InsuranceProduct product = products.save(new InsuranceProduct("Home " + suffix, "HOME", "Home cover",
                new BigDecimal("100.00"), new BigDecimal("10000.00")));
        claimTypes.save(new ClaimType(product, queue, "HOME"));
        Policy policy = new Policy("POL-" + suffix, customer, product, LocalDate.now().minusDays(1),
                LocalDate.now().plusYears(1));
        policy.setStatus(PolicyStatus.ACTIVE);
        policies.saveAndFlush(policy);
        return new Fixture(admin, customer, otherCustomer, agent, officer, queue, policy);
    }

    private AppUser saveUser(String prefix, Role role) {
        return users.saveAndFlush(new AppUser(prefix + "@example.test", "not-a-login-hash", role, prefix));
    }

    private record Fixture(AppUser admin, AppUser customer, AppUser otherCustomer, AppUser agent,
                           AppUser officer, ClaimQueue queue, Policy policy) { }
}
