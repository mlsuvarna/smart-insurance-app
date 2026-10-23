package com.smartinsurance.service;

import com.smartinsurance.dto.CustomerDtos.ProfileResponse;
import com.smartinsurance.dto.CustomerDtos.ProfileUpdate;
import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.CustomerProfile;
import com.smartinsurance.enums.Role;
import com.smartinsurance.enums.KycStatus;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.exception.BusinessRuleException;
import com.smartinsurance.repository.CustomerProfileRepository;
import com.smartinsurance.repository.AssignmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class CustomerProfileService {
    private final CustomerProfileRepository profiles;
    private final PiiCipher cipher;
    private final AssignmentRepository assignments;
    private final AuditLogService audit;
    public CustomerProfileService(CustomerProfileRepository profiles, PiiCipher cipher, AssignmentRepository assignments,
                                  AuditLogService audit) {
        this.profiles = profiles; this.cipher = cipher; this.assignments = assignments; this.audit = audit;
    }
    @Transactional
    public ProfileResponse update(AppUser user, ProfileUpdate request) {
        CustomerProfile profile = profiles.findById(user.getId()).orElseGet(() -> new CustomerProfile(user));
        profile.update(request.dateOfBirth(), cipher.encrypt(request.address()), cipher.encrypt(request.phone()),
                cipher.encrypt(request.nationalId()));
        CustomerProfile saved = profiles.save(profile);
        audit.record(user, "CUSTOMER_PROFILE_UPDATED", "CUSTOMER_PROFILE", user.getId().toString());
        return toResponse(saved, user, true);
    }
    @Transactional(readOnly = true)
    public ProfileResponse get(AppUser actor, Long customerId) {
        boolean owns = actor.getId().equals(customerId);
        boolean assignedAgent = actor.getRole() == Role.AGENT
                && assignments.existsByAgentIdAndCustomerId(actor.getId(), customerId);
        if (!owns && actor.getRole() != Role.ADMIN && !assignedAgent) {
            throw new AccessDeniedException("Customer profile is not accessible");
        }
        CustomerProfile profile = profiles.findById(customerId).orElseThrow(() ->
                new ResourceNotFoundException("Customer profile not found"));
        return toResponse(profile, owns ? actor : profile.getUser(), owns);
    }

    @Transactional(readOnly = true)
    public Page<ProfileResponse> listForAdmin(Pageable pageable) {
        return profiles.findAllByOrderByUserFullNameAscUserIdAsc(pageable)
                .map(profile -> toResponse(profile, profile.getUser(), false));
    }

    @Transactional
    public ProfileResponse review(AppUser actor, Long customerId, KycStatus status) {
        if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Administrator access required");
        if (status == null || status == KycStatus.PENDING) {
            throw new BusinessRuleException("KYC review must set VERIFIED or REJECTED");
        }
        CustomerProfile profile = profiles.findById(customerId).orElseThrow(() ->
                new ResourceNotFoundException("Customer profile not found"));
        profile.setKycStatus(status);
        audit.record(actor, "KYC_" + status.name(), "CUSTOMER_PROFILE", customerId.toString());
        return toResponse(profiles.save(profile), profile.getUser(), false);
    }
    private ProfileResponse toResponse(CustomerProfile p, AppUser user, boolean includeAddress) {
        String phone = cipher.decrypt(p.getPhoneEncrypted());
        String nationalId = cipher.decrypt(p.getNationalIdEncrypted());
        return new ProfileResponse(p.getUserId(), user.getFullName(), user.getEmail(), p.getDateOfBirth(),
                includeAddress ? cipher.decrypt(p.getAddressEncrypted()) : null, mask(phone), mask(nationalId), p.getKycStatus());
    }
    private String mask(String value) {
        if (value == null || value.isBlank()) return null;
        return "*".repeat(Math.max(4, value.length() - 4)) + value.substring(Math.max(0, value.length() - 4));
    }
}
