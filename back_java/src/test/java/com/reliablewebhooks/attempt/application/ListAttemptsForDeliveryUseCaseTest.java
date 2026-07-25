package com.reliablewebhooks.attempt.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.attempt.domain.Attempt;
import com.reliablewebhooks.attempt.domain.AttemptOutcome;
import com.reliablewebhooks.attempt.domain.AttemptRepository;
import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListAttemptsForDeliveryUseCaseTest {

    @Test
    void throwsNotFoundWhenTheDeliveryDoesNotExist() {
        ListAttemptsForDeliveryUseCase useCase = new ListAttemptsForDeliveryUseCase(
                new DeliveryRepositoryStub(false), new EmptyAttemptRepository());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsTheAttemptTimelineWhenTheDeliveryExists() {
        UUID deliveryId = UUID.randomUUID();
        Attempt attempt = Attempt.start(deliveryId, 1, "webhook.delivery.main");
        attempt.resolve(AttemptOutcome.SUCCESS, 200);
        ListAttemptsForDeliveryUseCase useCase = new ListAttemptsForDeliveryUseCase(
                new DeliveryRepositoryStub(true), new FixedAttemptRepository(List.of(attempt)));

        Page<AttemptView> page = useCase.execute(deliveryId, PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AttemptView::id).containsExactly(attempt.getId());
        assertThat(page.getContent().get(0).outcome()).isEqualTo(AttemptOutcome.SUCCESS.name());
    }

    private static class DeliveryRepositoryStub implements DeliveryRepository {
        private final boolean exists;

        DeliveryRepositoryStub(boolean exists) {
            this.exists = exists;
        }

        @Override
        public Delivery save(Delivery delivery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Delivery> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            return exists;
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

    private static class EmptyAttemptRepository implements AttemptRepository {
        @Override
        public Attempt save(Attempt attempt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Attempt> findByDeliveryId(UUID deliveryId, Pageable pageable) {
            return new PageImpl<>(List.of());
        }
    }

    private static class FixedAttemptRepository implements AttemptRepository {
        private final List<Attempt> attempts;

        FixedAttemptRepository(List<Attempt> attempts) {
            this.attempts = attempts;
        }

        @Override
        public Attempt save(Attempt attempt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Attempt> findByDeliveryId(UUID deliveryId, Pageable pageable) {
            return new PageImpl<>(attempts, pageable, attempts.size());
        }
    }
}
