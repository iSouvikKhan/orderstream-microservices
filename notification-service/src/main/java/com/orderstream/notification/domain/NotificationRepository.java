package com.orderstream.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Notification> findByOrderId(UUID orderId);
}
