package com.reliablewebhooks.endpoint.application;

import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEndpointsUseCase {

    private final EndpointRepository endpointRepository;

    @Transactional(readOnly = true)
    public Page<EndpointView> execute(Pageable pageable) {
        return endpointRepository.findAllOrderByCreatedAtDesc(pageable).map(EndpointView::from);
    }
}
