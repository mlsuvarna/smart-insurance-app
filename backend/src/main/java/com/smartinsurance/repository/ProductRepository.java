package com.smartinsurance.repository;

import com.smartinsurance.entity.InsuranceProduct;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<InsuranceProduct, Long> {
    List<InsuranceProduct> findByActiveTrue();
}
