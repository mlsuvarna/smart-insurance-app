package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface ClaimTypeRepository extends JpaRepository<ClaimType, Long> {
    @EntityGraph(attributePaths = {"product", "queue"})
    Optional<ClaimType> findByProductIdAndNameIgnoreCaseAndActiveTrue(Long productId, String name);
    @EntityGraph(attributePaths = {"product", "queue"})
    List<ClaimType> findByProductIdAndActiveTrue(Long productId);
    @EntityGraph(attributePaths = {"product", "queue"})
    List<ClaimType> findAll();
    boolean existsByQueueIdAndActiveTrue(Long queueId);
}
