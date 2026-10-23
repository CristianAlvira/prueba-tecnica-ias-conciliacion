package com.pagoclaro.reconciliation.domain.port;

import reactor.core.publisher.Mono;

public interface TransactionalPort {

    <T> Mono<T> execute(Mono<T> operation);
}
