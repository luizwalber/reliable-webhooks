package com.reliablewebhooks.endpoint.application;

import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteEndpointUseCase {

    private final EndpointRepository endpointRepository;

    public DeleteEndpointUseCase(EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    @Transactional
    public void execute(UUID endpointId) {
        if (!endpointRepository.existsById(endpointId)) {
            throw new NotFoundException("No endpoint with id " + endpointId);
        }
        endpointRepository.deleteById(endpointId);
    }
}
