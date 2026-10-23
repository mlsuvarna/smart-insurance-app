package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimHistoryRepository extends JpaRepository<ClaimHistory, Long> {
    List<ClaimHistory> findByClaimIdOrderByCreatedAtAsc(Long claimId);
}
