package com.reliablewebhooks.delivery.application;

import com.reliablewebhooks.delivery.domain.DeliveryRepository;
import com.reliablewebhooks.delivery.domain.DeliveryState;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** state=DEAD is the DLQ view — no separate DLQ resource (docs/adr/0005-state-machine). */
@Service
@RequiredArgsConstructor
public class ListDeliveriesUseCase {

    private final DeliveryRepository deliveryRepository;

    @Transactional(readOnly = true)
    public Page<DeliveryView> execute(DeliveryState state, UUID endpointId, Pageable pageable) {
        return deliveryRepository.search(state, endpointId, pageable).map(DeliveryView::from);
    }
}
