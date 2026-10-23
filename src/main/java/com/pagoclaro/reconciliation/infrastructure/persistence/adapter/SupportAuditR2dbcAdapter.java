package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import com.pagoclaro.reconciliation.domain.model.AuditRecord;
import com.pagoclaro.reconciliation.domain.port.SupportAuditRepositoryPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class SupportAuditR2dbcAdapter implements SupportAuditRepositoryPort {

    private static final String COUNT_MATCHING = """
            SELECT COUNT(*) AS total
            FROM support_audit
            WHERE payment_id = :paymentId AND action = :action AND reason = :reason
            """;

    private static final String INSERT_AUDIT = """
            INSERT INTO support_audit (id, payment_id, action, reason, created_at)
            VALUES (support_audit_seq.NEXTVAL, :paymentId, :action, :reason, SYSTIMESTAMP)
            """;

    private static final String TOTAL = "total";

    private final DatabaseClient databaseClient;

    public SupportAuditR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Boolean> exists(AuditRecord audit) {
        return bindAudit(databaseClient.sql(COUNT_MATCHING), audit)
                .map(row -> row.get(TOTAL, Long.class) > 0)
                .one();
    }

    @Override
    public Mono<Void> save(AuditRecord audit) {
        return bindAudit(databaseClient.sql(INSERT_AUDIT), audit)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private DatabaseClient.GenericExecuteSpec bindAudit(DatabaseClient.GenericExecuteSpec spec, AuditRecord audit) {
        return spec.bind("paymentId", audit.paymentId())
                .bind("action", audit.action().name())
                .bind("reason", audit.reason());
    }
}
