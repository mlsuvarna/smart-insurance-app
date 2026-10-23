package com.smartinsurance.repository;

import com.smartinsurance.entity.Notification;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop100ByUser_IdOrderByCreatedAtDesc(Long userId);
    long countByUser_IdAndReadFalse(Long userId);
}
