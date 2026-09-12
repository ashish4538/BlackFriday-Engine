package com.example.flashsale.consumer;

import com.example.flashsale.model.OrderEvent;
import com.example.flashsale.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
public class OrderConsumer {
    private final OrderRepository orders;
    private final MeterRegistry metrics;

    public OrderConsumer(OrderRepository orders, MeterRegistry metrics) {
        this.orders = orders;
        this.metrics = metrics;
    }

    @Transactional
    @KafkaListener(topics = "${orders.topic:flash-sale-orders}",
            groupId = "${orders.consumer-group:flash-sale-orders}")
    public void consume(OrderEvent event) {
        if (event == null || event.eventId() == null) throw new IllegalArgumentException("Missing event ID");
        var order = orders.findByEventId(event.eventId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown order event"));
        if (!order.getUserId().equals(event.userId()) || !order.getItemId().equals(event.itemId())
                || event.price() == null || order.getPrice().compareTo(event.price()) != 0) {
            throw new IllegalArgumentException("Order event does not match persisted order");
        }
        int changed = orders.markDelivered(event.eventId(), LocalDateTime.now());
        metrics.counter("orders.consumption", "result", changed == 1 ? "delivered" : "duplicate").increment();
    }
}
