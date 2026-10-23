package com.smartinsurance.repository;

import com.smartinsurance.entity.Policy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Page<Policy> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
}
