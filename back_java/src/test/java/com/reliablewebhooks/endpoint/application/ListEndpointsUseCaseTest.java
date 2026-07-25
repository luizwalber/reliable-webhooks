package com.reliablewebhooks.endpoint.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class ListEndpointsUseCaseTest {

    @Test
    void returnsAPageOfEndpointViewsFromTheRepository() {
        Endpoint first = Endpoint.register("https://example.com/a", null, "secret-a");
        Endpoint second = Endpoint.register("https://example.com/b", null, "secret-b");
        ListEndpointsUseCase useCase = new ListEndpointsUseCase(new FixedEndpointRepository(List.of(first, second)));

        Page<EndpointView> page = useCase.execute(PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(EndpointView::id).containsExactly(first.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private static class FixedEndpointRepository implements EndpointRepository {
        private final List<Endpoint> endpoints;

        FixedEndpointRepository(List<Endpoint> endpoints) {
            this.endpoints = endpoints;
        }

        @Override
        public Endpoint save(Endpoint endpoint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Endpoint> findById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteById(UUID id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Page<Endpoint> findAllOrderByCreatedAtDesc(Pageable pageable) {
            return new PageImpl<>(endpoints, pageable, endpoints.size());
        }
    }
}
