package com.smartinsurance.repository;

import com.smartinsurance.entity.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Page<Policy> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
    boolean existsByCustomerIdAndProductIdAndStatus(Long customerId, Long productId,
                                                    com.smartinsurance.enums.PolicyStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select policy from Policy policy where policy.id = :id")
    Optional<Policy> findByIdForPayment(@Param("id") Long id);
}
