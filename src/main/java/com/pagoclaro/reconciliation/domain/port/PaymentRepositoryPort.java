package com.pagoclaro.reconciliation.domain.port;

import com.pagoclaro.reconciliation.domain.model.Payment;
import reactor.core.publisher.Mono;

public interface PaymentRepositoryPort {

    Mono<Payment> findByExternalRefForUpdate(String externalRef);

    Mono<Void> markConfirmed(Long paymentId);
}
