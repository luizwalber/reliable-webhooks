package com.reliablewebhooks.endpoint.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.endpoint.domain.CircuitBreakerState;
import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.endpoint.domain.SecretGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class RegisterEndpointUseCaseTest {

    private final Map<UUID, Endpoint> endpoints = new HashMap<>();

    @Test
    void registersAnEndpointWithAGeneratedSecretAndClosedCircuit() {
        RegisterEndpointUseCase useCase = new RegisterEndpointUseCase(new FakeEndpointRepository(), () -> "generated-secret");

        EndpointView view = useCase.execute(new RegisterEndpointCommand("https://example.com/hook", "test"));

        assertThat(view.secret()).isEqualTo("generated-secret");
        assertThat(view.circuitBreakerState()).isEqualTo(CircuitBreakerState.CLOSED.name());
        assertThat(view.successCount()).isZero();
        assertThat(view.deadCount()).isZero();
        assertThat(view.successRate()).isNull();
        assertThat(endpoints).containsKey(view.id());
    }

    private class FakeEndpointRepository implements EndpointRepository {
        @Override
        public Endpoint save(Endpoint endpoint) {
            endpoints.put(endpoint.getId(), endpoint);
            return endpoint;
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            return Optional.ofNullable(endpoints.get(id));
        }

        @Override
        public boolean existsById(UUID id) {
            return endpoints.containsKey(id);
        }

        @Override
        public void deleteById(UUID id) {
            endpoints.remove(id);
        }

        @Override
        public Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable) {
            return new PageImpl<>(List.copyOf(endpoints.values()));
        }
    }
}
