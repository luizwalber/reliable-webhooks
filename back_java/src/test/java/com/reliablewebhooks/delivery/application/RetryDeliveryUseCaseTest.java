package com.reliablewebhooks.delivery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.shared.domain.ConflictException;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class RetryDeliveryUseCaseTest {

    private final Map<UUID, Delivery> deliveries = new HashMap<>();
    private final List<Integer> publishedAttemptNumbers = new ArrayList<>();
    private final RetryDeliveryUseCase useCase = new RetryDeliveryUseCase(new FakeDeliveryRepository(), new FakeDeliveryPublisher());

    @Test
    void throwsNotFoundForAnUnknownDeliveryId() {
        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsConflictWhenTheDeliveryIsNotDead() {
        Delivery delivery = givenADelivery();

        assertThatThrownBy(() -> useCase.execute(delivery.getId())).isInstanceOf(ConflictException.class);
    }

    @Test
    void resetsAndRepublishesADeadDelivery() {
        Delivery delivery = givenADelivery();
        delivery.startDelivering();
        delivery.markDead();

        DeliveryView view = useCase.execute(delivery.getId());

        assertThat(view.state()).isEqualTo(DeliveryState.SCHEDULED.name());
        assertThat(view.attemptCount()).isZero();
        assertThat(view.nextAttemptAt()).isNotNull();
        assertThat(publishedAttemptNumbers).containsExactly(1);
    }

    private Delivery givenADelivery() {
        Delivery delivery = Delivery.schedule(UUID.randomUUID(), UUID.randomUUID());
        deliveries.put(delivery.getId(), delivery);
        return delivery;
    }

    private class FakeDeliveryRepository implements DeliveryRepository {
        @Override
        public Delivery save(Delivery delivery) {
            deliveries.put(delivery.getId(), delivery);
            return delivery;
        }

        @Override
        public Optional<Delivery> findById(UUID id) {
            return Optional.ofNullable(deliveries.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Delivery> findByEventId(UUID eventId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Delivery> search(DeliveryState state, UUID endpointId, Pageable pageable) {
            throw new UnsupportedOperationException();
        }
    }

    private class FakeDeliveryPublisher implements DeliveryPublisher {
        @Override
        public void publish(Delivery delivery, int attemptNumber) {
            publishedAttemptNumbers.add(attemptNumber);
        }

        @Override
        public boolean hasRetryBandFor(int attemptNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OffsetDateTime scheduleRetry(Delivery delivery, int attemptNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void publishToDeadLetter(Delivery delivery, int attemptNumber) {
            throw new UnsupportedOperationException();
        }
    }
}
