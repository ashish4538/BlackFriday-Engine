package com.example.flashsale.config;

import com.example.flashsale.producer.KafkaOrderProducer;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableScheduling
public class KafkaConfig {
    @Bean
    org.springframework.kafka.support.converter.RecordMessageConverter kafkaMessageConverter(
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return new org.springframework.kafka.support.converter.StringJsonMessageConverter(mapper);
    }

    @Bean
    NewTopic ordersTopic(@Value("${orders.topic:flash-sale-orders}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic deadLetterTopic(@Value("${orders.topic:flash-sale-orders}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafka, MeterRegistry metrics) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler((record, error) -> {
            recoverer.accept(record, error);
            metrics.counter("orders.dlq", "result", "published").increment();
        }, new FixedBackOff(1000, 2));
        handler.setRetryListeners((record, error, attempt) ->
                metrics.counter("orders.consumer.failures").increment());
        return handler;
    }
}
