package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimReview;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimReviewRepository extends JpaRepository<ClaimReview, Long> {
    Optional<ClaimReview> findFirstByClaimIdAndClosedAtIsNullOrderByCreatedAtDesc(Long claimId);
}
