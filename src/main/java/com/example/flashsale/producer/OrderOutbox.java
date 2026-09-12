package com.example.flashsale.producer;

import com.example.flashsale.model.OrderEvent;
import com.example.flashsale.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderOutbox {
    private static final Logger log = LoggerFactory.getLogger(OrderOutbox.class);
    private final OrderRepository orders;
    private final KafkaOrderProducer producer;
    private final MeterRegistry metrics;

    public OrderOutbox(OrderRepository orders, KafkaOrderProducer producer, MeterRegistry metrics) {
        this.orders = orders;
        this.producer = producer;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${orders.publish-delay-ms:1000}",
            initialDelayString = "${orders.publish-initial-delay-ms:1000}")
    public void publishPending() {
        for (var order : orders.findTop20ByPublishedFalseOrderByIdAsc()) {
            try {
                producer.sendOrderEvent(OrderEvent.from(order));
                orders.markPublished(order.getEventId());
                metrics.counter("orders.publication", "result", "success").increment();
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                metrics.counter("orders.publication", "result", "retry").increment();
                log.warn("Order {} publication deferred ({})", order.getEventId(), e.getClass().getSimpleName());
                // Bound work during outages. Retry durable rows on the next scheduled poll.
                return;
            }
        }
    }
}
