package com.orderstream.notification.api;

import com.orderstream.notification.domain.Notification;
import com.orderstream.notification.domain.NotificationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record NotificationResponse(UUID id, UUID orderId, String channel, String subject, String body,
                                       Instant createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getOrderId(), n.getChannel(), n.getSubject(),
                    n.getBody(), n.getCreatedAt());
        }
    }

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<NotificationResponse> mine(@RequestHeader("X-User-Id") String customerId) {
        return notifications.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(NotificationResponse::from).toList();
    }
}
