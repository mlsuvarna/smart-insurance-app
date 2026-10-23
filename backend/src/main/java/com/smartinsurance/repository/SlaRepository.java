package com.smartinsurance.repository;

import com.smartinsurance.entity.SlaConfiguration;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlaRepository extends JpaRepository<SlaConfiguration, Long> {
    Optional<SlaConfiguration> findByClaimTypeIgnoreCaseAndActiveTrue(String claimType);
    Optional<SlaConfiguration> findByClaimTypeIgnoreCase(String claimType);
}
