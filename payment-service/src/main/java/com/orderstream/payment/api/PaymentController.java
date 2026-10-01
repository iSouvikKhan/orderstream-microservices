package com.orderstream.payment.api;

import com.orderstream.payment.domain.Payment;
import com.orderstream.payment.domain.PaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public record PaymentResponse(UUID id, UUID orderId, BigDecimal amount, Payment.Status status,
                                  String failureReason, Instant createdAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(),
                    p.getFailureReason(), p.getCreatedAt());
        }
    }

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse byOrder(@RequestHeader("X-User-Id") String customerId, @PathVariable UUID orderId) {
        return payments.findByOrderId(orderId)
                .filter(p -> p.getCustomerId().equals(customerId))
                .map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order " + orderId));
    }
}
