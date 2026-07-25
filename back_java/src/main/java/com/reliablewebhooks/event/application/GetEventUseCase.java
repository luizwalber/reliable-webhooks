package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetEventUseCase {

    private final EventRepository eventRepository;

    public GetEventUseCase(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public EventView execute(UUID eventId) {
        return eventRepository.findById(eventId)
                .map(EventView::from)
                .orElseThrow(() -> new NotFoundException("No event with id " + eventId));
    }
}
