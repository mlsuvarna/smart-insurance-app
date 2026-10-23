package com.smartinsurance.service;

import com.smartinsurance.entity.SlaConfiguration;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.SlaRepository;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaService {
    private final SlaRepository repository;
    private final AuditLogService audit;
    @Value("${app.sla.default-days:10}")
    private int defaultDays;
    public SlaService(SlaRepository repository, AuditLogService audit) { this.repository = repository; this.audit = audit; }
    @Transactional(readOnly = true)
    public int daysFor(String claimType) {
        return repository.findByClaimTypeIgnoreCaseAndActiveTrue(claimType)
                .map(SlaConfiguration::getDurationDays).orElse(defaultDays);
    }
    @Transactional(readOnly = true)
    public List<SlaConfiguration> all() { return repository.findAll(); }
    @Transactional
    public SlaConfiguration save(com.smartinsurance.entity.AppUser actor, String claimType, int days, boolean active) {
        SlaConfiguration config = repository.findByClaimTypeIgnoreCase(claimType)
                .orElseGet(() -> new SlaConfiguration(claimType, days));
        config.update(days, active);
        SlaConfiguration saved = repository.save(config);
        audit.record(actor, "SLA_CONFIGURED", "SLA", saved.getClaimType());
        return saved;
    }
    @Transactional
    public void remove(com.smartinsurance.entity.AppUser actor, Long id) {
        SlaConfiguration config = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SLA configuration not found"));
        config.update(config.getDurationDays(), false);
        audit.record(actor, "SLA_DEACTIVATED", "SLA", config.getClaimType());
    }
    public record SlaRequest(@NotBlank String claimType, @Min(1) int durationDays, boolean active) { }
}
