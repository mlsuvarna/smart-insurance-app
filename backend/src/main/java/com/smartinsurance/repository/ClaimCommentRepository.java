package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimComment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimCommentRepository extends JpaRepository<ClaimComment, Long> {
    List<ClaimComment> findByClaimIdAndVisibilityOrderByCreatedAtAsc(Long claimId, String visibility);
}
