package com.reliablewebhooks.attempt.application;

import com.reliablewebhooks.attempt.domain.AttemptRepository;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates across the delivery and attempt bounded modules' domain ports. */
@Service
@RequiredArgsConstructor
public class ListAttemptsForDeliveryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final AttemptRepository attemptRepository;

    @Transactional(readOnly = true)
    public Page<AttemptView> execute(UUID deliveryId, Pageable pageable) {
        if (!deliveryRepository.existsById(deliveryId)) {
            throw new NotFoundException("No delivery with id " + deliveryId);
        }
        return attemptRepository.findByDeliveryId(deliveryId, pageable).map(AttemptView::from);
    }
}
