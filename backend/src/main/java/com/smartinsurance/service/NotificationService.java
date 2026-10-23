package com.smartinsurance.service;

import com.smartinsurance.entity.Claim;
import com.smartinsurance.entity.Notification;
import com.smartinsurance.exception.ResourceNotFoundException;
import com.smartinsurance.repository.NotificationRepository;
import com.smartinsurance.entity.AppUser;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final EmailNotifier email;
    public NotificationService(NotificationRepository notifications, EmailNotifier email) {
        this.notifications = notifications; this.email = email;
    }
    @Transactional
    public void claimEvent(Claim claim, String event) {
        String message = "Update available for claim " + claim.getClaimNumber();
        notifyUser(claim.getCustomer(), claim, event, message);
    }
    @Transactional
    public void notifyUser(AppUser recipient, Claim claim, String event, String message) {
        notifications.save(new Notification(recipient, claim, event, message));
        String emailAddress = recipient.getEmail();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { email.notify(emailAddress, event); }
            });
        } else {
            email.notify(emailAddress, event);
        }
    }
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(AppUser user) {
        return notifications.findTop100ByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getEventType(), n.getMessage(),
                        n.getClaim() == null ? null : n.getClaim().getId(), n.isRead(), n.getCreatedAt())).toList();
    }
    @Transactional(readOnly = true)
    public long unread(AppUser user) { return notifications.countByUser_IdAndReadFalse(user.getId()); }
    @Transactional
    public void markRead(AppUser user, Long id) {
        Notification notification = notifications.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (!notification.getUserId().equals(user.getId())) {
            throw new org.springframework.security.access.AccessDeniedException("Notification is not accessible");
        }
        notification.setRead(true);
    }
    public record NotificationResponse(Long id, String eventType, String message, Long claimId, boolean read,
                                       java.time.Instant createdAt) { }
}
