package com.orderstream.order;

import com.orderstream.order.domain.Order;
import com.orderstream.order.domain.OrderItem;
import com.orderstream.order.domain.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private Order newOrder() {
        return Order.place("u1", List.of(new OrderItem("SKU-1", 3, new BigDecimal("2.50"))));
    }

    @Test
    void totalIsSumOfLines() {
        assertThat(newOrder().getTotalAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void emptyOrderIsRejected() {
        assertThatThrownBy(() -> Order.place("u1", List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalStatesAreFinal() {
        Order order = newOrder();
        assertThat(order.confirm()).isTrue();
        assertThat(order.cancel("late failure")).isFalse();
        assertThat(order.markInventoryReserved()).isFalse();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentMayOvertakeInventoryEvent() {
        Order order = newOrder();
        assertThat(order.confirm()).isTrue();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }
}
