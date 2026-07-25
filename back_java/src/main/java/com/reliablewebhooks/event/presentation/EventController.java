package com.reliablewebhooks.event.presentation;

import com.reliablewebhooks.delivery.application.ListDeliveriesForEventUseCase;
import com.reliablewebhooks.delivery.presentation.dto.DeliveryResponse;
import com.reliablewebhooks.event.application.EventView;
import com.reliablewebhooks.event.application.GetEventUseCase;
import com.reliablewebhooks.event.application.IngestEventCommand;
import com.reliablewebhooks.event.application.IngestEventUseCase;
import com.reliablewebhooks.event.application.ListEventsUseCase;
import com.reliablewebhooks.event.presentation.dto.EventIngestRequest;
import com.reliablewebhooks.event.presentation.dto.EventResponse;
import com.reliablewebhooks.shared.presentation.PagedResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final IngestEventUseCase ingestEventUseCase;
    private final GetEventUseCase getEventUseCase;
    private final ListEventsUseCase listEventsUseCase;
    private final ListDeliveriesForEventUseCase listDeliveriesForEventUseCase;
    private final IdempotentReplay idempotentReplay;

    /**
     * A retried request with the same Idempotency-Key (scoped to the
     * calling producer) replays the exact original response rather than
     * creating a duplicate event (docs/adr/0002-idempotency-and-delivery-guarantees).
     * The replay sequencing itself lives in IdempotentReplay — this method
     * only supplies what to compute on a cache miss.
     */
    @PostMapping
    public ResponseEntity<String> ingest(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Producer-Id") String producerId,
            @Valid @RequestBody EventIngestRequest request) {

        return idempotentReplay.respond(producerId, idempotencyKey, HttpStatus.ACCEPTED, () -> EventResponse.from(
                ingestEventUseCase.execute(new IngestEventCommand(producerId, idempotencyKey, request.eventType(), request.payload()))));
    }

    @GetMapping
    public PagedResponse<EventResponse> list(Pageable pageable) {
        Page<EventView> page = listEventsUseCase.execute(pageable);
        return PagedResponse.from(page, EventResponse::from);
    }

    @GetMapping("/{eventId}")
    public EventResponse getById(@PathVariable UUID eventId) {
        return EventResponse.from(getEventUseCase.execute(eventId));
    }

    @GetMapping("/{eventId}/deliveries")
    public PagedResponse<DeliveryResponse> listDeliveries(@PathVariable UUID eventId, Pageable pageable) {
        return PagedResponse.from(listDeliveriesForEventUseCase.execute(eventId, pageable), DeliveryResponse::from);
    }
}
