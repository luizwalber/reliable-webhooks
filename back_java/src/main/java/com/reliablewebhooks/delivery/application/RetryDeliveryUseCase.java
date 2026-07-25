package com.reliablewebhooks.delivery.application;

import com.reliablewebhooks.delivery.domain.Delivery;
import com.reliablewebhooks.delivery.domain.DeliveryPublisher;
import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import com.reliablewebhooks.shared.domain.ConflictException;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manual retry re-enters the exact same path a brand-new Delivery takes —
 * no special-cased redelivery logic (docs/adr/0005-state-machine). Only
 * legal from DEAD; the circuit breaker isn't checked here, so a retry
 * against a still-open circuit surfaces a real CIRCUIT_OPEN attempt on its
 * next try, same as any other attempt.
 */
@Service
@RequiredArgsConstructor
public class RetryDeliveryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPublisher deliveryPublisher;

    @Transactional
    public DeliveryView execute(UUID deliveryId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotFoundException("No delivery with id " + deliveryId));

        if (delivery.getState() != DeliveryState.DEAD) {
            throw new ConflictException("Delivery " + deliveryId + " is not DEAD — cannot be manually retried");
        }

        delivery.retryManually();
        Delivery saved = deliveryRepository.save(delivery);
        deliveryPublisher.publish(saved, 1);
        return DeliveryView.from(saved);
    }
}
