package com.smartinsurance.repository;

import com.smartinsurance.entity.PaymentStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, Long> {
    List<PaymentStatusHistory> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
