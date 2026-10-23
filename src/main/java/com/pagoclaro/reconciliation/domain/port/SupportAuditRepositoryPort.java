package com.pagoclaro.reconciliation.domain.port;

import com.pagoclaro.reconciliation.domain.model.AuditRecord;
import reactor.core.publisher.Mono;

public interface SupportAuditRepositoryPort {

    Mono<Boolean> exists(AuditRecord audit);

    Mono<Void> save(AuditRecord audit);
}
