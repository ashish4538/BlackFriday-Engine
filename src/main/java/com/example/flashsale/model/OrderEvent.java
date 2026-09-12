package com.example.flashsale.model;

import java.math.BigDecimal;

public record OrderEvent(String eventId, String userId, String itemId, BigDecimal price) {
    public static OrderEvent from(Order order) {
        return new OrderEvent(order.getEventId(), order.getUserId(), order.getItemId(), order.getPrice());
    }
}
