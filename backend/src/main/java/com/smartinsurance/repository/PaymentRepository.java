package com.smartinsurance.repository;

import com.smartinsurance.entity.Payment;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.smartinsurance.enums.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @EntityGraph(attributePaths = "policy")
    Page<Payment> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId, Pageable pageable);
    @EntityGraph(attributePaths = "policy")
    Optional<Payment> findByPaymentReferenceAndCustomerId(String paymentReference, Long customerId);
    boolean existsByPolicyIdAndStatus(Long policyId, PaymentStatus status);
    Optional<Payment> findByPaymentReference(String paymentReference);
    Page<Payment> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"policy", "customer"})
    @Query("select p from Payment p where p.paymentReference = :reference")
    Optional<Payment> findByReferenceForUpdate(@Param("reference") String reference);
}
