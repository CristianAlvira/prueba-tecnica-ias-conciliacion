package com.pagoclaro.reconciliation.domain.port;

import com.pagoclaro.reconciliation.domain.model.ProviderEvent;
import reactor.core.publisher.Flux;

public interface ProviderEventRepositoryPort {

    Flux<ProviderEvent> findByPaymentId(Long paymentId);
}
