package com.smartinsurance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.CustomerProfile;
import com.smartinsurance.entity.Notification;
import com.smartinsurance.entity.SlaConfiguration;
import com.smartinsurance.enums.KycStatus;
import com.smartinsurance.enums.Role;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.AssignmentRepository;
import com.smartinsurance.repository.CustomerProfileRepository;
import com.smartinsurance.repository.NotificationRepository;
import com.smartinsurance.repository.SlaRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerNotificationReportTest {
    @Test
    void customerProfileResponseMasksContactAndNationalId() {
        CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
        PiiCipher cipher = mock(PiiCipher.class);
        AppUser user = user(1L, Role.CUSTOMER);
        var profile = new com.smartinsurance.entity.CustomerProfile(user);
        profile.update(LocalDate.of(1990, 1, 1), "enc-address", "enc-phone", "enc-id");
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(cipher.decrypt("enc-phone")).thenReturn("5551234567");
        when(cipher.decrypt("enc-id")).thenReturn("123456789");
        when(cipher.decrypt("enc-address")).thenReturn("1 Main Street");
        CustomerProfileService service = new CustomerProfileService(profiles, cipher,
                mock(AssignmentRepository.class), mock(AuditLogService.class));

        var response = service.get(user, 1L);

        assertEquals("1 Main Street", response.address());
        assertEquals("******4567", response.maskedPhone());
        assertEquals("*****6789", response.maskedNationalId());
    }

    @Test
    void onlyAdministratorCanReviewKyc() {
        CustomerProfileService service = new CustomerProfileService(mock(CustomerProfileRepository.class),
                mock(PiiCipher.class), mock(AssignmentRepository.class), mock(AuditLogService.class));

        assertThrows(AccessDeniedException.class,
                () -> service.review(user(2L, Role.AGENT), 1L, KycStatus.VERIFIED));
    }

    @Test
    void administratorCanVerifyCustomerKycAndResponseRemainsMasked() {
        CustomerProfileRepository profiles = mock(CustomerProfileRepository.class);
        PiiCipher cipher = mock(PiiCipher.class);
        AppUser customer = user(1L, Role.CUSTOMER);
        AppUser admin = user(2L, Role.ADMIN);
        CustomerProfile profile = new CustomerProfile(customer);
        profile.update(LocalDate.of(1990, 1, 1), "address", "phone", "national");
        ReflectionTestUtils.setField(profile, "userId", 1L);
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
        when(profiles.save(profile)).thenReturn(profile);
        when(cipher.decrypt("phone")).thenReturn("12345678");
        when(cipher.decrypt("national")).thenReturn("123456789");
        CustomerProfileService service = new CustomerProfileService(profiles, cipher,
                mock(AssignmentRepository.class), mock(AuditLogService.class));

        var response = service.review(admin, 1L, KycStatus.VERIFIED);

        assertEquals(KycStatus.VERIFIED, response.kycStatus());
        assertNull(response.address());
        assertEquals("****5678", response.maskedPhone());
    }

    @Test
    void notificationCannotBeMarkedReadByAnotherUser() {
        NotificationRepository notifications = mock(NotificationRepository.class);
        AppUser recipient = user(3L, Role.CUSTOMER);
        Notification notification = new Notification(recipient, null, "POLICY", "Update");
        when(notifications.findById(5L)).thenReturn(Optional.of(notification));
        NotificationService service = new NotificationService(notifications, mock(EmailNotifier.class));

        assertThrows(AccessDeniedException.class, () -> service.markRead(user(4L, Role.CUSTOMER), 5L));
        verify(notifications, never()).save(notification);
    }

    @Test
    void slaUsesConfiguredTypeAndFallsBackToDefault() {
        SlaRepository repository = mock(SlaRepository.class);
        when(repository.findByClaimTypeIgnoreCaseAndActiveTrue("HOME"))
                .thenReturn(Optional.of(new SlaConfiguration("HOME", 6)));
        when(repository.findByClaimTypeIgnoreCaseAndActiveTrue("AUTO")).thenReturn(Optional.empty());
        SlaService service = new SlaService(repository, mock(AuditLogService.class));
        ReflectionTestUtils.setField(service, "defaultDays", 10);

        assertEquals(6, service.daysFor("HOME"));
        assertEquals(10, service.daysFor("AUTO"));
    }

    @Test
    void invalidReportFiltersFailBeforeDatabaseQuery() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ReportService reports = new ReportService(jdbc);

        assertThrows(BusinessRuleException.class,
                () -> reports.summary(LocalDate.now().plusDays(1), LocalDate.now().plusDays(1), null, null, null));
        verify(jdbc, never()).queryForMap(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class));
    }

    @Test
    void reportSummaryPassesRequiredDateProductTypeAndStatusFiltersToDatabaseQuery() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForMap(anyString(), any(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class)))
                .thenReturn(Map.of("claim_volume", 2L));
        ReportService reports = new ReportService(jdbc);
        LocalDate from = LocalDate.now().minusDays(10);
        LocalDate to = LocalDate.now().minusDays(1);

        assertEquals(2L, reports.summary(from, to, 42L, "HOME", "APPROVED").get("claim_volume"));

        var sql = forClass(String.class);
        var params = forClass(org.springframework.jdbc.core.namedparam.MapSqlParameterSource.class);
        verify(jdbc).queryForMap(sql.capture(), params.capture());
        assertTrue(sql.getValue().contains("c.created_at >= :fromDate AND c.created_at < :toDate"));
        assertTrue(sql.getValue().contains("AS claim_volume"));
        assertTrue(sql.getValue().contains("AS pending_count"));
        assertTrue(sql.getValue().contains("AS approved_count"));
        assertTrue(sql.getValue().contains("AS rejected_count"));
        assertTrue(sql.getValue().contains("AS settled_count"));
        assertTrue(sql.getValue().contains("AS settlement_total"));
        assertTrue(sql.getValue().contains("AS settlement_ratio"));
        assertTrue(sql.getValue().contains("AS approval_rate"));
        assertTrue(sql.getValue().contains("AS rejection_rate"));
        assertTrue(sql.getValue().contains("AS avg_processing_seconds"));
        assertTrue(sql.getValue().contains("p.product_id = :productId"));
        assertTrue(sql.getValue().contains("c.claim_type = :claimType"));
        assertTrue(sql.getValue().contains("c.status = :claimStatus"));
        assertEquals(42L, params.getValue().getValue("productId"));
        assertEquals("HOME", params.getValue().getValue("claimType"));
        assertEquals("APPROVED", params.getValue().getValue("claimStatus"));
        assertEquals(from.atStartOfDay(), params.getValue().getValue("fromDate"));
        assertEquals(to.plusDays(1).atStartOfDay(), params.getValue().getValue("toDate"));
    }

    private static AppUser user(long id, Role role) {
        AppUser user = new AppUser("user" + id + "@example.test", "hash", role, "User " + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
