package com.reliablewebhooks.endpoint.application;

import com.reliablewebhooks.endpoint.domain.Endpoint;
import com.reliablewebhooks.endpoint.domain.EndpointRepository;
import com.reliablewebhooks.endpoint.domain.SecretGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterEndpointUseCase {

    private final EndpointRepository endpointRepository;
    private final SecretGenerator secretGenerator;

    /**
     * Random secret generated server-side, returned once by the caller's
     * presentation-layer response (docs/adr/0007-hmac-signing). No rotation
     * is supported this phase.
     */
    @Transactional
    public EndpointView execute(RegisterEndpointCommand command) {
        Endpoint endpoint = Endpoint.register(command.url(), command.description(), secretGenerator.generate());
        return EndpointView.from(endpointRepository.save(endpoint));
    }
}
