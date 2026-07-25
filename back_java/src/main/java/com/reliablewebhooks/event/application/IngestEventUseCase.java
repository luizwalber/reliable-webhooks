package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestEventUseCase {

    private final EventRepository eventRepository;
    private final OutboxRepository outboxRepository;

    public IngestEventUseCase(EventRepository eventRepository, OutboxRepository outboxRepository) {
        this.eventRepository = eventRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Writes the Event and its Outbox row in a single transaction
     * (docs/adr/0003-transactional-outbox). No Kafka publication happens
     * here — that is the outbox poller's job, a later implementation slice.
     */
    @Transactional
    public EventView execute(IngestEventCommand command) {
        Event event = Event.receive(command.eventType(), command.producerId(), command.idempotencyKey(), command.payload());

        outboxRepository.save(OutboxEntry.forEvent(event.getId(), event.getPayload()));
        event.markOutboxed();

        return EventView.from(eventRepository.save(event));
    }
}
