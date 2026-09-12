package com.example.flashsale;

import com.example.flashsale.consumer.OrderConsumer;
import com.example.flashsale.model.Order;
import com.example.flashsale.model.OrderEvent;
import com.example.flashsale.producer.KafkaOrderProducer;
import com.example.flashsale.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"orders.publish-initial-delay-ms=3600000",
        "orders.topic=flash-sale-orders-it", "orders.consumer-group=flash-sale-orders-it",
        "spring.kafka.listener.auto-startup=false"})
class KafkaDeliveryIT {
    @Autowired KafkaOrderProducer producer;
    @Autowired KafkaListenerEndpointRegistry listeners;
    @Autowired MeterRegistry metrics;
    @Autowired OrderRepository orders;
    @SpyBean OrderConsumer consumer;

    @BeforeEach
    void startConsumer() { listeners.start(); }

    Order newOrder() {
        String id = UUID.randomUUID().toString();
        return orders.saveAndFlush(new Order(id, "kafka-" + id, "item", new BigDecimal("12.34")));
    }

    @Test
    void brokerDuplicateDeliveryHasOneDurableEffect() throws Exception {
        var order = newOrder();
        double duplicatesBefore = count("orders.consumption", "result", "duplicate");
        producer.sendOrderEvent(OrderEvent.from(order));
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                orders.findByEventId(order.getEventId()).orElseThrow().getDeliveredAt() != null);
        var deliveredAt = orders.findByEventId(order.getEventId()).orElseThrow().getDeliveredAt();
        producer.sendOrderEvent(OrderEvent.from(order));
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                count("orders.consumption", "result", "duplicate") > duplicatesBefore);
        assertEquals(deliveredAt, orders.findByEventId(order.getEventId()).orElseThrow().getDeliveredAt());
    }

    @Test
    void consumerProcessingFailureIsRetriedBeforeOffsetAdvances() throws Exception {
        var order = newOrder();
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("injected transient failure"))
                .doCallRealMethod().when(consumer).consume(OrderEvent.from(order));
        producer.sendOrderEvent(OrderEvent.from(order));
        await().atMost(30, TimeUnit.SECONDS).until(() ->
                orders.findByEventId(order.getEventId()).orElseThrow().getDeliveredAt() != null);
        verify(consumer, atLeast(2)).consume(OrderEvent.from(order));
    }

    @Test
    void poisonEventReachesDeadLetterTopicWithoutMutatingOrder() throws Exception {
        var order = newOrder();
        double before = count("orders.dlq", "result", "published");
        producer.sendOrderEvent(new OrderEvent(order.getEventId(), order.getUserId(), "wrong-item", BigDecimal.ONE));
        await().atMost(30, TimeUnit.SECONDS).until(() -> count("orders.dlq", "result", "published") > before);
        assertNull(orders.findByEventId(order.getEventId()).orElseThrow().getDeliveredAt());
    }

    private double count(String name, String tag, String value) {
        var counter = metrics.find(name).tag(tag, value).counter();
        return counter == null ? 0 : counter.count();
    }
}
