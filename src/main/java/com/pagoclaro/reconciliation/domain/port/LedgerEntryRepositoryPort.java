package com.pagoclaro.reconciliation.domain.port;

import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LedgerEntryRepositoryPort {

    Flux<LedgerEntry> findByPaymentId(Long paymentId);

    Mono<Void> createReversal(Long paymentId, LedgerEntry debit);
}
