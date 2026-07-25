package com.reliablewebhooks.endpoint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.endpoint.domain.CircuitBreakerState;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class GetEndpointUseCaseTest {

    @Test
    void throwsNotFoundForAnUnknownEndpointId() {
        GetEndpointUseCase useCase = new GetEndpointUseCase(new EmptyEndpointRepository());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void returnsAViewForAKnownEndpointIncludingTheSecret() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", "test", "top-secret");
        GetEndpointUseCase useCase = new GetEndpointUseCase(new SingleEndpointRepository(endpoint));

        EndpointView view = useCase.execute(endpoint.getId());

        assertThat(view.id()).isEqualTo(endpoint.getId());
        assertThat(view.circuitBreakerState()).isEqualTo(CircuitBreakerState.CLOSED.name());
        // The use-case view still carries the secret (domain truth); presentation decides exposure per DTO.
        assertThat(view.secret()).isEqualTo("top-secret");
    }

    private static class EmptyEndpointRepository implements EndpointRepository {
        @Override
        public Endpoint save(Endpoint endpoint) {
            return endpoint;
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            return Optional.empty();
        }

        @Override
        public boolean existsById(UUID id) {
            return false;
        }

        @Override
        public void deleteById(UUID id) {
        }

        @Override
        public Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable) {
            return new PageImpl<>(List.of());
        }
    }

    private static class SingleEndpointRepository extends EmptyEndpointRepository {
        private final Endpoint endpoint;

        SingleEndpointRepository(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            return id.equals(endpoint.getId()) ? Optional.of(endpoint) : Optional.empty();
        }
    }
}
