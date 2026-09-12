package com.example.flashsale.producer;

import com.example.flashsale.model.OrderEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.TimeUnit;

@Service
public class KafkaOrderProducer {
    public static final String TOPIC = "flash-sale-orders";
    private final KafkaTemplate<String, OrderEvent> kafka;
    private final String topic;

    public KafkaOrderProducer(KafkaTemplate<String, OrderEvent> kafka,
                              @Value("${orders.topic:flash-sale-orders}") String topic) {
        this.kafka = kafka;
        this.topic = topic;
    }

    public void sendOrderEvent(OrderEvent event) throws Exception {
        // Timeout is ambiguous. The outbox retains the same ID for subsequent attempts.
        kafka.send(topic, event.itemId(), event).get(15, TimeUnit.SECONDS);
    }
}
