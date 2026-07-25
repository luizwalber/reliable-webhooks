package com.reliablewebhooks.endpoint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

class DeleteEndpointUseCaseTest {

    private final Map<UUID, Endpoint> endpoints = new HashMap<>();

    @Test
    void throwsNotFoundForAnUnknownEndpointId() {
        DeleteEndpointUseCase useCase = new DeleteEndpointUseCase(new FakeEndpointRepository());

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void removesAKnownEndpoint() {
        Endpoint endpoint = Endpoint.register("https://example.com/hook", null, "secret");
        endpoints.put(endpoint.getId(), endpoint);
        DeleteEndpointUseCase useCase = new DeleteEndpointUseCase(new FakeEndpointRepository());

        useCase.execute(endpoint.getId());

        assertThat(endpoints).doesNotContainKey(endpoint.getId());
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

        @Override
        public List<Endpoint> findAll() {
            return List.copyOf(endpoints.values());
        }
    }
}
