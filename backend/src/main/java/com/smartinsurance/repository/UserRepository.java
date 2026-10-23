package com.smartinsurance.repository;

import com.smartinsurance.entity.AppUser;
import java.util.Optional;
import com.smartinsurance.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    org.springframework.data.domain.Page<AppUser> findByRoleAndActiveTrueOrderByFullNameAscIdAsc(
            Role role, org.springframework.data.domain.Pageable pageable);
}
