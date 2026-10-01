package com.orderstream.order.api;

import com.orderstream.order.domain.Order;
import com.orderstream.order.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record CreateOrderRequest(@NotEmpty @Valid List<Item> items) {
    }

    public record Item(@NotBlank String productId,
                       @Min(1) int quantity,
                       @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice) {
    }

    public record OrderResponse(UUID id, String customerId, OrderStatus status, BigDecimal totalAmount,
                                String failureReason, List<Item> items, Instant createdAt, Instant updatedAt) {

        public static OrderResponse from(Order o) {
            List<Item> items = o.getItems().stream()
                    .map(i -> new Item(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
                    .toList();
            return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(),
                    o.getFailureReason(), items, o.getCreatedAt(), o.getUpdatedAt());
        }
    }
}
