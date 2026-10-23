package com.smartinsurance.repository;

import com.smartinsurance.entity.Claim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface ClaimRepository extends JpaRepository<Claim, Long> {
    Page<Claim> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
    boolean existsByAssignedOfficerIdAndStatusNotIn(Long officerId, java.util.Collection<com.smartinsurance.enums.ClaimStatus> statuses);
    boolean existsByQueueIdAndStatusNotIn(Long queueId, java.util.Collection<com.smartinsurance.enums.ClaimStatus> statuses);
    boolean existsByQueueIdAndAssignedOfficerIdAndStatusNotIn(Long queueId, Long officerId,
            java.util.Collection<com.smartinsurance.enums.ClaimStatus> statuses);
    @Query(value = "SELECT c.* FROM claims c WHERE (c.assigned_officer_id = :officerId "
            + "OR (c.assigned_officer_id IS NULL AND c.status = 'SUBMITTED' AND EXISTS "
            + "(SELECT 1 FROM officer_queue_memberships m WHERE m.officer_id = :officerId AND m.queue_id = c.queue_id))) "
            + "AND (:status IS NULL OR c.status = :status) "
            + "AND (:queueCode IS NULL OR EXISTS (SELECT 1 FROM claim_queues q WHERE q.id = c.queue_id AND q.code = :queueCode)) "
            + "AND (:overdue IS NULL OR (:overdue = TRUE AND c.status NOT IN ('REJECTED','SETTLED','WITHDRAWN') "
            + "AND c.sla_paused_at IS NULL AND TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) < UTC_TIMESTAMP(6)) "
            + "OR (:overdue = FALSE AND (c.status IN ('REJECTED','SETTLED','WITHDRAWN') "
            + "OR c.sla_paused_at IS NOT NULL OR TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) >= UTC_TIMESTAMP(6)))) "
            + "ORDER BY c.created_at ASC, c.id ASC",
            countQuery = "SELECT COUNT(*) FROM claims c WHERE (c.assigned_officer_id = :officerId "
                    + "OR (c.assigned_officer_id IS NULL AND c.status = 'SUBMITTED' AND EXISTS "
                    + "(SELECT 1 FROM officer_queue_memberships m WHERE m.officer_id = :officerId AND m.queue_id = c.queue_id))) "
                    + "AND (:status IS NULL OR c.status = :status) "
                    + "AND (:queueCode IS NULL OR EXISTS (SELECT 1 FROM claim_queues q WHERE q.id = c.queue_id AND q.code = :queueCode)) "
                    + "AND (:overdue IS NULL OR (:overdue = TRUE AND c.status NOT IN ('REJECTED','SETTLED','WITHDRAWN') "
                    + "AND c.sla_paused_at IS NULL AND TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) < UTC_TIMESTAMP(6)) "
                    + "OR (:overdue = FALSE AND (c.status IN ('REJECTED','SETTLED','WITHDRAWN') "
                    + "OR c.sla_paused_at IS NOT NULL OR TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) >= UTC_TIMESTAMP(6))))",
            nativeQuery = true)
    Page<Claim> findOfficerQueue(@Param("officerId") Long officerId, @Param("status") String status,
                                 @Param("overdue") Boolean overdue, @Param("queueCode") String queueCode,
                                 Pageable pageable);
    @Query(value = "SELECT c.* FROM claims c WHERE (:status IS NULL OR c.status = :status) "
            + "AND (:queueCode IS NULL OR EXISTS (SELECT 1 FROM claim_queues q WHERE q.id = c.queue_id AND q.code = :queueCode)) "
            + "AND (:overdue IS NULL OR (:overdue = TRUE AND c.status NOT IN ('REJECTED','SETTLED','WITHDRAWN') "
            + "AND c.sla_paused_at IS NULL AND TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) < UTC_TIMESTAMP(6)) "
            + "OR (:overdue = FALSE AND (c.status IN ('REJECTED','SETTLED','WITHDRAWN') "
            + "OR c.sla_paused_at IS NOT NULL OR TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) >= UTC_TIMESTAMP(6)))) "
            + "ORDER BY c.created_at ASC, c.id ASC",
            countQuery = "SELECT COUNT(*) FROM claims c WHERE (:status IS NULL OR c.status = :status) "
                    + "AND (:queueCode IS NULL OR EXISTS (SELECT 1 FROM claim_queues q WHERE q.id = c.queue_id AND q.code = :queueCode)) "
                    + "AND (:overdue IS NULL OR (:overdue = TRUE AND c.status NOT IN ('REJECTED','SETTLED','WITHDRAWN') "
                    + "AND c.sla_paused_at IS NULL AND TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) < UTC_TIMESTAMP(6)) "
                    + "OR (:overdue = FALSE AND (c.status IN ('REJECTED','SETTLED','WITHDRAWN') "
                    + "OR c.sla_paused_at IS NOT NULL OR TIMESTAMPADD(SECOND, c.sla_paused_seconds, c.sla_due_at) >= UTC_TIMESTAMP(6))))",
            nativeQuery = true)
    Page<Claim> findAdminQueue(@Param("status") String status, @Param("overdue") Boolean overdue,
                               @Param("queueCode") String queueCode, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Claim c where c.id = :claimId")
    java.util.Optional<Claim> findByIdForUpdate(@Param("claimId") Long claimId);
    @Query(value = "SELECT c.* FROM claims c JOIN agent_customer_assignments a ON a.customer_id = c.customer_id "
            + "WHERE a.agent_id = :agentId ORDER BY c.created_at DESC, c.id DESC",
            countQuery = "SELECT COUNT(c.id) FROM claims c JOIN agent_customer_assignments a ON a.customer_id = c.customer_id "
                    + "WHERE a.agent_id = :agentId", nativeQuery = true)
    Page<Claim> findClaimsForAgent(@Param("agentId") Long agentId, Pageable pageable);
}
