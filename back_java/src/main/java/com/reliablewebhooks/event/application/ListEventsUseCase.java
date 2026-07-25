package com.reliablewebhooks.event.application;

import com.reliablewebhooks.event.domain.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListEventsUseCase {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public Page<EventView> execute(Pageable pageable) {
        return eventRepository.findAllOrderByReceivedAtDesc(pageable).map(EventView::from);
    }
}
