package com.reliablewebhooks.endpoint.presentation;

import com.reliablewebhooks.endpoint.application.DeleteEndpointUseCase;
import com.reliablewebhooks.endpoint.application.EndpointView;
import com.reliablewebhooks.endpoint.application.GetEndpointUseCase;
import com.reliablewebhooks.endpoint.application.ListEndpointsUseCase;
import com.reliablewebhooks.endpoint.application.RegisterEndpointCommand;
import com.reliablewebhooks.endpoint.application.RegisterEndpointUseCase;
import com.reliablewebhooks.endpoint.presentation.dto.EndpointCreateRequest;
import com.reliablewebhooks.endpoint.presentation.dto.EndpointCreatedResponse;
import com.reliablewebhooks.endpoint.presentation.dto.EndpointResponse;
import com.reliablewebhooks.shared.presentation.PagedResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/endpoints")
public class EndpointController {

    private final RegisterEndpointUseCase registerEndpointUseCase;
    private final GetEndpointUseCase getEndpointUseCase;
    private final ListEndpointsUseCase listEndpointsUseCase;
    private final DeleteEndpointUseCase deleteEndpointUseCase;

    public EndpointController(
            RegisterEndpointUseCase registerEndpointUseCase,
            GetEndpointUseCase getEndpointUseCase,
            ListEndpointsUseCase listEndpointsUseCase,
            DeleteEndpointUseCase deleteEndpointUseCase) {
        this.registerEndpointUseCase = registerEndpointUseCase;
        this.getEndpointUseCase = getEndpointUseCase;
        this.listEndpointsUseCase = listEndpointsUseCase;
        this.deleteEndpointUseCase = deleteEndpointUseCase;
    }

    @PostMapping
    public ResponseEntity<EndpointCreatedResponse> create(@Valid @RequestBody EndpointCreateRequest request) {
        EndpointView view = registerEndpointUseCase.execute(new RegisterEndpointCommand(request.url(), request.description()));
        return ResponseEntity.status(HttpStatus.CREATED).body(EndpointCreatedResponse.from(view));
    }

    @GetMapping
    public PagedResponse<EndpointResponse> list(Pageable pageable) {
        Page<EndpointView> page = listEndpointsUseCase.execute(pageable);
        return PagedResponse.from(page, EndpointResponse::from);
    }

    @GetMapping("/{endpointId}")
    public EndpointResponse getById(@PathVariable UUID endpointId) {
        return EndpointResponse.from(getEndpointUseCase.execute(endpointId));
    }

    @DeleteMapping("/{endpointId}")
    public ResponseEntity<Void> delete(@PathVariable UUID endpointId) {
        deleteEndpointUseCase.execute(endpointId);
        return ResponseEntity.noContent().build();
    }
}
