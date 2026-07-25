package com.reliablewebhooks.delivery.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** See docs/adr/0005-state-machine for the legal Delivery transitions this covers. */
class DeliveryTest {

    @Test
    void scheduleStartsScheduledWithZeroAttemptsAndNoNextAttempt() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());

        assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void startDeliveringMovesToDelivering() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());

        delivery.startDelivering();

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERING);
    }

    @Test
    void markDeliveredIsTerminalAndClearsNextAttemptAt() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        delivery.startDelivering();

        delivery.markDelivered();

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DELIVERED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void scheduleRetryReturnsToScheduledAndIncrementsAttemptCount() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        delivery.startDelivering();
        OffsetDateTime nextAttemptAt = OffsetDateTime.now().plusSeconds(10);

        delivery.scheduleRetry(nextAttemptAt);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isEqualTo(nextAttemptAt);
    }

    @Test
    void markDeadIsTerminalAndClearsNextAttemptAt() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        delivery.startDelivering();

        delivery.markDead();

        assertThat(delivery.getState()).isEqualTo(DeliveryState.DEAD);
        assertThat(delivery.getAttemptCount()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void scheduleRetryAfterDeadReenters() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        delivery.startDelivering();
        delivery.markDead();

        OffsetDateTime nextAttemptAt = OffsetDateTime.now();
        delivery.scheduleRetry(nextAttemptAt);

        assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(delivery.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void retryManuallyResetsAttemptCountAndSchedulesImmediately() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        delivery.startDelivering();
        delivery.markDead();

        delivery.retryManually();

        assertThat(delivery.getState()).isEqualTo(DeliveryState.SCHEDULED);
        assertThat(delivery.getAttemptCount()).isZero();
        assertThat(delivery.getNextAttemptAt()).isNotNull();
    }
}
