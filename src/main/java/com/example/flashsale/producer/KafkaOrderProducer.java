package com.example.flashsale.producer;

import com.example.flashsale.model.OrderEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaOrderProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    private static final String TOPIC = "flash-sale-orders";

    public KafkaOrderProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }
    public void sendOrderEvent(OrderEvent event) {
        kafkaTemplate.send(TOPIC, event.getItemId(), event); // Use itemId as key for ordering if needed
    }
}