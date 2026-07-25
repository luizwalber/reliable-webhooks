package com.reliablewebhooks.delivery.application;

import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.event.domain.EventRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates across the event and delivery bounded modules' domain ports. */
@Service
@RequiredArgsConstructor
public class ListDeliveriesForEventUseCase {

    private final EventRepository eventRepository;
    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public Page<DeliveryView> execute(UUID eventId, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw new NotFoundException("No event with id " + eventId);
        }
        return deliveryRepository.findByEventId(eventId, pageable).map(DeliveryView::from);
    }
}
