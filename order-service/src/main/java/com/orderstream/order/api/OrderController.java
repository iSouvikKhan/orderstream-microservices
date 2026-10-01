package com.orderstream.order.api;

import com.orderstream.order.api.OrderDtos.CreateOrderRequest;
import com.orderstream.order.api.OrderDtos.OrderResponse;
import com.orderstream.order.domain.Order;
import com.orderstream.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The customer id comes from the {@code X-User-Id} header, which the API gateway sets from the
 * verified JWT subject (and strips from incoming requests).
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    static final String USER_HEADER = "X-User-Id";

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestHeader(USER_HEADER) String customerId,
                                                @Valid @RequestBody CreateOrderRequest request) {
        Order order = service.placeOrder(customerId, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@RequestHeader(USER_HEADER) String customerId, @PathVariable UUID id) {
        Order order = service.get(id);
        if (!order.getCustomerId().equals(customerId)) {
            // Don't reveal that someone else's order exists.
            throw new com.orderstream.order.service.OrderNotFoundException(id);
        }
        return OrderResponse.from(order);
    }

    @GetMapping
    public List<OrderResponse> mine(@RequestHeader(USER_HEADER) String customerId) {
        return service.forCustomer(customerId).stream().map(OrderResponse::from).toList();
    }
}
