package com.reliablewebhooks.delivery.presentation;

import com.reliablewebhooks.attempt.application.AttemptView;
import com.reliablewebhooks.attempt.application.ListAttemptsForDeliveryUseCase;
import com.reliablewebhooks.attempt.presentation.dto.AttemptResponse;
import com.reliablewebhooks.delivery.application.DeliveryView;
import com.reliablewebhooks.delivery.application.GetDeliveryUseCase;
import com.reliablewebhooks.delivery.application.ListDeliveriesUseCase;
import com.reliablewebhooks.delivery.application.RetryDeliveryUseCase;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.delivery.presentation.dto.DeliveryResponse;
import com.reliablewebhooks.shared.presentation.PagedResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deliveries")
@RequiredArgsConstructor
public class DeliveryController {

    private final GetDeliveryUseCase getDeliveryUseCase;
    private final ListDeliveriesUseCase listDeliveriesUseCase;
    private final RetryDeliveryUseCase retryDeliveryUseCase;
    private final ListAttemptsForDeliveryUseCase listAttemptsForDeliveryUseCase;

    /** state=DEAD is the DLQ view — no separate DLQ resource (docs/adr/0005-state-machine). */
    @GetMapping
    public PagedResponse<DeliveryResponse> list(
            @RequestParam(required = false) DeliveryState state,
            @RequestParam(required = false) UUID endpointId,
            Pageable pageable) {
        Page<DeliveryView> page = listDeliveriesUseCase.execute(state, endpointId, pageable);
        return PagedResponse.from(page, DeliveryResponse::from);
    }

    @GetMapping("/{deliveryId}")
    public DeliveryResponse getById(@PathVariable UUID deliveryId) {
        return DeliveryResponse.from(getDeliveryUseCase.execute(deliveryId));
    }

    @GetMapping("/{deliveryId}/attempts")
    public PagedResponse<AttemptResponse> listAttempts(@PathVariable UUID deliveryId, Pageable pageable) {
        Page<AttemptView> page = listAttemptsForDeliveryUseCase.execute(deliveryId, pageable);
        return PagedResponse.from(page, AttemptResponse::from);
    }

    @PostMapping("/{deliveryId}/retry")
    public DeliveryResponse retry(@PathVariable UUID deliveryId) {
        return DeliveryResponse.from(retryDeliveryUseCase.execute(deliveryId));
    }
}
