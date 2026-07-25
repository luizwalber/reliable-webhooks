package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetEventUseCase {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public EventView execute(UUID eventId) {
        return eventRepository.findById(eventId)
                .map(EventView::from)
                .orElseThrow(() -> new NotFoundException("No event with id " + eventId));
    }
}
