package com.reliablewebhooks.delivery.application;

import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.shared.domain.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetDeliveryUseCase {

    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public DeliveryView execute(UUID deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .map(DeliveryView::from)
                .orElseThrow(() -> new NotFoundException("No delivery with id " + deliveryId));
    }
}
