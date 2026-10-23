package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import com.pagoclaro.reconciliation.domain.model.ProviderEvent;
import com.pagoclaro.reconciliation.domain.port.ProviderEventRepositoryPort;
import com.pagoclaro.reconciliation.infrastructure.persistence.mapper.ProviderEventRowMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public class ProviderEventR2dbcAdapter implements ProviderEventRepositoryPort {

    private static final String FIND_BY_PAYMENT_ID = """
            SELECT provider_event_id, provider_status
            FROM provider_event
            WHERE payment_id = :paymentId
            ORDER BY received_at, id
            """;

    private final DatabaseClient databaseClient;

    public ProviderEventR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<ProviderEvent> findByPaymentId(Long paymentId) {
        return databaseClient.sql(FIND_BY_PAYMENT_ID)
                .bind("paymentId", paymentId)
                .map(ProviderEventRowMapper::toProviderEvent)
                .all();
    }
}
