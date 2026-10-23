package com.smartinsurance.service;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.entity.AuditLog;
import com.smartinsurance.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {
    private final AuditLogRepository audit;
    public AuditLogService(AuditLogRepository audit) { this.audit = audit; }
    @Transactional
    public void record(AppUser actor, String event, String resourceType, String resourceId) {
        audit.save(new AuditLog(actor, event, resourceType, resourceId, MDC.get("correlationId")));
    }
}
