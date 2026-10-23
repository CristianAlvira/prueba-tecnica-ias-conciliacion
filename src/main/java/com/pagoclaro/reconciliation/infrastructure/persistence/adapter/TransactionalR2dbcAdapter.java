package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import com.pagoclaro.reconciliation.domain.port.TransactionalPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

@Component
public class TransactionalR2dbcAdapter implements TransactionalPort {

    private final TransactionalOperator transactionalOperator;

    public TransactionalR2dbcAdapter(ReactiveTransactionManager transactionManager) {
        this.transactionalOperator = TransactionalOperator.create(transactionManager);
    }

    @Override
    public <T> Mono<T> execute(Mono<T> operation) {
        return transactionalOperator.transactional(operation);
    }
}
