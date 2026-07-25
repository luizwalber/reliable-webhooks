package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.EventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListEventsUseCase {

    private final EventRepository eventRepository;

    public ListEventsUseCase(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional(readOnly = true)
    public Page<EventView> execute(Pageable pageable) {
        return eventRepository.findAllOrderByReceivedAtDesc(pageable).map(EventView::from);
    }
}
