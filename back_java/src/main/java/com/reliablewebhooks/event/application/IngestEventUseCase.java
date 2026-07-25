package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.Event;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.outbox.domain.OutboxEntry;
import com.reliablewebhooks.outbox.domain.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IngestEventUseCase {

    private final EventRepository eventRepository;
    private final OutboxRepository outboxRepository;

    /**
     * Writes the Event and its Outbox row in a single transaction
     * (docs/adr/0003-transactional-outbox). No Kafka publication happens
     * here — that is the outbox poller's job, a later implementation slice.
     * The Event row must be inserted before the Outbox row: outbox.event_id
     * has a foreign key onto events.id, and Postgres checks that constraint
     * per-statement, not just at commit.
     */
    @Transactional
    public EventView execute(IngestEventCommand command) {
        Event event = Event.receive(command.eventType(), command.producerId(), command.idempotencyKey(), command.payload());
        event = eventRepository.save(event);

        outboxRepository.save(OutboxEntry.forEvent(event.getId(), event.getPayload()));
        event.markOutboxed();

        return EventView.from(eventRepository.save(event));
    }
}
