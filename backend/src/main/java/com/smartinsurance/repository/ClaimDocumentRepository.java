package com.smartinsurance.repository;

import com.smartinsurance.entity.ClaimDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClaimDocumentRepository extends JpaRepository<ClaimDocument, Long> {
    List<ClaimDocument> findByClaimIdOrderByUploadedAtAsc(Long claimId);
    long countByClaimId(Long claimId);
}
