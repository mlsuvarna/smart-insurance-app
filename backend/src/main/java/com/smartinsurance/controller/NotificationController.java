package com.smartinsurance.controller;

import com.smartinsurance.entity.AppUser;
import com.smartinsurance.service.NotificationService;
import com.smartinsurance.service.NotificationService.NotificationResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }
    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal AppUser user) { return notifications.list(user); }
    @GetMapping("/unread-count")
    public long unread(@AuthenticationPrincipal AppUser user) { return notifications.unread(user); }
    @PatchMapping("/{id}/read")
    public void read(@AuthenticationPrincipal AppUser user, @PathVariable Long id) { notifications.markRead(user, id); }
}
