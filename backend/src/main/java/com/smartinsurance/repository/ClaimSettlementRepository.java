package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimSettlement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimSettlementRepository extends JpaRepository<ClaimSettlement, Long> {
    List<ClaimSettlement> findByClaimIdOrderByCreatedAtAsc(Long claimId);
    boolean existsByReference(String reference);
}
