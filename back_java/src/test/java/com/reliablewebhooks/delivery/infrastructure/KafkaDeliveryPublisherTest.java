package com.reliablewebhooks.delivery.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reliablewebhooks.delivery.domain.Delivery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

/** Kafka-sending mechanics only — band/jitter math is RetryLadder's own, covered by RetryLadderTest. */
@SuppressWarnings("unchecked")
class KafkaDeliveryPublisherTest {

    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final KafkaDeliveryPublisher publisher = new KafkaDeliveryPublisher(
            kafkaTemplate, new ObjectMapper(), "webhook.delivery.main", "webhook.delivery.dlq",
            new RetryTopology(0.2, List.of(new RetryTopology.Band("webhook.delivery.retry.30s", 10_000))));

    @Test
    void publishSendsToTheMainTopicWithNoNextAttemptAtHeader() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());

        publisher.publish(delivery, 1);

        ProducerRecord<String, String> record = captureSentRecord();
        assertThat(record.topic()).isEqualTo("webhook.delivery.main");
        assertThat(record.key()).isEqualTo(delivery.getEndpointId().toString());
        assertThat(record.value()).contains("\"attemptNumber\":1");
        assertThat(record.headers().lastHeader(KafkaDeliveryPublisher.NEXT_ATTEMPT_AT_HEADER)).isNull();
    }

    @Test
    void scheduleRetrySendsToTheRetryBandTopicWithANextAttemptAtHeader() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());

        OffsetDateTime nextAttemptAt = publisher.scheduleRetry(delivery, 2);

        ProducerRecord<String, String> record = captureSentRecord();
        assertThat(record.topic()).isEqualTo("webhook.delivery.retry.30s");
        assertThat(record.value()).contains("\"attemptNumber\":2");
        assertThat(record.headers().lastHeader(KafkaDeliveryPublisher.NEXT_ATTEMPT_AT_HEADER)).isNotNull();
        assertThat(nextAttemptAt).isNotNull();
    }

    @Test
    void publishToDeadLetterSendsToTheDlqTopicWithNoHeader() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());

        publisher.publishToDeadLetter(delivery, 4);

        ProducerRecord<String, String> record = captureSentRecord();
        assertThat(record.topic()).isEqualTo("webhook.delivery.dlq");
        assertThat(record.headers().lastHeader(KafkaDeliveryPublisher.NEXT_ATTEMPT_AT_HEADER)).isNull();
    }

    @Test
    void hasRetryBandForDelegatesToTheRetryLadder() {
        assertThat(publisher.hasRetryBandFor(2)).isTrue();
        assertThat(publisher.hasRetryBandFor(3)).isFalse();
    }

    private ProducerRecord<String, String> captureSentRecord() {
        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }
}
