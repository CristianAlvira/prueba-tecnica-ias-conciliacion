package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import com.pagoclaro.reconciliation.domain.exception.PaymentLockedException;
import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentStatus;
import com.pagoclaro.reconciliation.domain.port.PaymentRepositoryPort;
import com.pagoclaro.reconciliation.infrastructure.config.ReconciliationProperties;
import com.pagoclaro.reconciliation.infrastructure.persistence.mapper.PaymentRowMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public class PaymentR2dbcAdapter implements PaymentRepositoryPort {

    private static final String FIND_BY_EXTERNAL_REF_FOR_UPDATE = """
            SELECT id, external_ref, status, amount
            FROM payment
            WHERE external_ref = :externalRef
            FOR UPDATE WAIT %d
            """;

    private static final String MARK_CONFIRMED = """
            UPDATE payment
            SET status = :status, updated_at = SYSTIMESTAMP, row_version = row_version + 1
            WHERE id = :id
            """;

    private final DatabaseClient databaseClient;
    private final String findForUpdateSql;

    public PaymentR2dbcAdapter(DatabaseClient databaseClient, ReconciliationProperties properties) {
        this.databaseClient = databaseClient;
        this.findForUpdateSql = FIND_BY_EXTERNAL_REF_FOR_UPDATE.formatted(properties.lockWaitSeconds());
    }

    @Override
    public Mono<Payment> findByExternalRefForUpdate(String externalRef) {
        return databaseClient.sql(findForUpdateSql)
                .bind("externalRef", externalRef)
                .map(PaymentRowMapper::toPayment)
                .one()
                .onErrorMap(OracleErrors::isResourceBusy, error -> new PaymentLockedException(externalRef, error));
    }

    @Override
    public Mono<Void> markConfirmed(Long paymentId) {
        return databaseClient.sql(MARK_CONFIRMED)
                .bind("status", PaymentStatus.CONFIRMED.name())
                .bind("id", paymentId)
                .fetch()
                .rowsUpdated()
                .then();
    }
}
