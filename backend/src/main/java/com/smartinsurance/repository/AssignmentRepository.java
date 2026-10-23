package com.smartinsurance.repository;

import com.smartinsurance.entity.AgentCustomerAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Sort;

public interface AssignmentRepository extends JpaRepository<AgentCustomerAssignment, Long> {
    boolean existsByAgentIdAndCustomerId(Long agentId, Long customerId);
    java.util.Optional<AgentCustomerAssignment> findByAgentIdAndCustomerId(Long agentId, Long customerId);
    @EntityGraph(attributePaths = "customer")
    @Query("select a from AgentCustomerAssignment a where a.agent.id = :agentId "
            + "order by a.customer.fullName asc, a.customer.id asc")
    Page<AgentCustomerAssignment> findByAgentId(@Param("agentId") Long agentId, Pageable pageable);
    @EntityGraph(attributePaths = {"customer", "agent"})
    Page<AgentCustomerAssignment> findAllByOrderByIdAsc(Pageable pageable);
}
