package com.smartinsurance.repository;

import com.smartinsurance.entity.AuditLog;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findTop500ByOrderByCreatedAtDesc();
    @EntityGraph(attributePaths = "actor")
    List<AuditLog> findTop500ByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);
    @EntityGraph(attributePaths = "actor")
    Page<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
