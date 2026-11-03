package com.smartinsurance.repository;

import com.smartinsurance.entity.OfficerQueueMembership;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.smartinsurance.enums.Role;

public interface OfficerQueueMembershipRepository extends JpaRepository<OfficerQueueMembership, Long> {
    boolean existsByOfficerIdAndQueueId(Long officerId, Long queueId);
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM officer_queue_memberships m
                JOIN users u ON u.id = m.officer_id
                JOIN claim_queues q ON q.id = m.queue_id
                WHERE m.officer_id = :officerId AND m.queue_id = :queueId
                  AND u.role = 'CLAIMS_OFFICER' AND u.active = TRUE AND q.active = TRUE
            )
            """, nativeQuery = true)
    boolean existsActiveMembership(@Param("officerId") Long officerId, @Param("queueId") Long queueId);
    Optional<OfficerQueueMembership> findByOfficerIdAndQueueId(Long officerId, Long queueId);
    @EntityGraph(attributePaths = {"officer", "assignedBy", "queue"})
    @Query("select m from OfficerQueueMembership m where m.queue.code = :code "
            + "and m.officer.active = true and m.officer.role = :role order by m.officer.fullName asc")
    Page<OfficerQueueMembership> findActiveMembers(@Param("code") String code, @Param("role") Role role,
                                                   Pageable pageable);
    @EntityGraph(attributePaths = {"officer", "assignedBy", "queue"})
    Page<OfficerQueueMembership> findAllByQueueCodeOrderByOfficerFullNameAsc(String code, Pageable pageable);
    @EntityGraph(attributePaths = {"queue"})
    java.util.List<OfficerQueueMembership> findByOfficerIdAndQueueActiveTrue(Long officerId);
}
