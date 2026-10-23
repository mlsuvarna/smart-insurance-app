package com.smartinsurance.repository;

import com.smartinsurance.entity.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {
    @EntityGraph(attributePaths = "user")
    Page<CustomerProfile> findAllByOrderByUserFullNameAscUserIdAsc(Pageable pageable); }
