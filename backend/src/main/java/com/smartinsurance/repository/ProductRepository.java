package com.smartinsurance.repository;

import com.smartinsurance.entity.InsuranceProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProductRepository extends JpaRepository<InsuranceProduct, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from InsuranceProduct product where product.id = :id")
    Optional<InsuranceProduct> findByIdForPurchase(@Param("id") Long id);
    List<InsuranceProduct> findByActiveTrue();
}
