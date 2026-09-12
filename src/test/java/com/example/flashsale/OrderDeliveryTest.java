package com.example.flashsale;

import com.example.flashsale.model.Order;
import com.example.flashsale.model.OrderEvent;
import com.example.flashsale.producer.KafkaOrderProducer;
import com.example.flashsale.producer.OrderOutbox;
import com.example.flashsale.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderDeliveryTest {
    @Test
    @SuppressWarnings("unchecked")
    void knownProducerFailureIsObservedAndSameEventCanBeRetried() throws Exception {
        KafkaTemplate<String, OrderEvent> kafka = mock(KafkaTemplate.class);
        var event = new OrderEvent("id", "user", "item", BigDecimal.ONE);
        when(kafka.send(KafkaOrderProducer.TOPIC, "item", event))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")))
                .thenReturn(CompletableFuture.completedFuture(null));
        var producer = new KafkaOrderProducer(kafka, KafkaOrderProducer.TOPIC);
        assertThrows(ExecutionException.class, () -> producer.sendOrderEvent(event));
        producer.sendOrderEvent(event);
        verify(kafka, times(2)).send(KafkaOrderProducer.TOPIC, "item", event);
    }

    @Test
    void failedPublicationRemainsPendingAndRetryMarksItOnlyAfterAcknowledgement() throws Exception {
        var orders = mock(OrderRepository.class);
        var producer = mock(KafkaOrderProducer.class);
        var order = new Order("stable-id", "user", "item", BigDecimal.ONE);
        when(orders.findTop20ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(order));
        doThrow(new java.util.concurrent.TimeoutException()).doNothing()
                .when(producer).sendOrderEvent(OrderEvent.from(order));
        var outbox = new OrderOutbox(orders, producer, new SimpleMeterRegistry());
        outbox.publishPending();
        verify(orders, never()).markPublished(anyString());
        outbox.publishPending();
        verify(orders).markPublished("stable-id");
        verify(producer, times(2)).sendOrderEvent(OrderEvent.from(order));
    }

    @Test
    void lostPublicationMarkerRetriesSameEvent() throws Exception {
        var orders = mock(OrderRepository.class);
        var producer = mock(KafkaOrderProducer.class);
        var order = new Order("stable-id", "user", "item", BigDecimal.ONE);
        when(orders.findTop20ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(order));
        when(orders.markPublished("stable-id")).thenThrow(new IllegalStateException("lost commit reply")).thenReturn(1);
        var outbox = new OrderOutbox(orders, producer, new SimpleMeterRegistry());
        outbox.publishPending();
        outbox.publishPending();
        verify(producer, times(2)).sendOrderEvent(OrderEvent.from(order));
    }
}
