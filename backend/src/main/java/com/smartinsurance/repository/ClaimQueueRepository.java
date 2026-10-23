package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimQueue;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimQueueRepository extends JpaRepository<ClaimQueue, Long> {
    Optional<ClaimQueue> findByCode(String code);
    boolean existsByCodeIgnoreCase(String code);
    boolean existsByNameIgnoreCase(String name);
    java.util.List<ClaimQueue> findAllByOrderByNameAsc();
    java.util.List<ClaimQueue> findByActiveTrueOrderByNameAsc();
    java.util.Optional<ClaimQueue> findByCodeIgnoreCase(String code);
}
