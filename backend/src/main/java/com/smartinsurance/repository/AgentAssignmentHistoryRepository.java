package com.smartinsurance.repository;

import com.smartinsurance.entity.AgentAssignmentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentAssignmentHistoryRepository extends JpaRepository<AgentAssignmentHistory, Long> {
    @EntityGraph(attributePaths = {"actor", "agent", "customer"})
    Page<AgentAssignmentHistory> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
