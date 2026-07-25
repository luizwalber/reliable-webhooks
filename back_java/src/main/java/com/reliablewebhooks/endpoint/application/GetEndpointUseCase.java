package com.reliablewebhooks.endpoint.application;

import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEndpointUseCase {

    private final EndpointRepository endpointRepository;

    public GetEndpointUseCase(EndpointRepository endpointRepository) {
        this.endpointRepository = endpointRepository;
    }

    @Transactional(readOnly = true)
    public EndpointView execute(UUID endpointId) {
        return endpointRepository.findById(endpointId)
                .map(EndpointView::from)
                .orElseThrow(() -> new NotFoundException("No endpoint with id " + endpointId));
    }
}
