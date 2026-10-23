package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import com.pagoclaro.reconciliation.domain.model.EntryType;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import com.pagoclaro.reconciliation.domain.port.LedgerEntryRepositoryPort;
import com.pagoclaro.reconciliation.infrastructure.persistence.mapper.LedgerEntryRowMapper;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class LedgerEntryR2dbcAdapter implements LedgerEntryRepositoryPort {

    private static final String FIND_BY_PAYMENT_ID = """
            SELECT id, entry_type, amount, related_entry_id, created_at
            FROM ledger_entry
            WHERE payment_id = :paymentId
            ORDER BY created_at, id
            """;

    private static final String INSERT_REVERSAL = """
            INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at)
            VALUES (ledger_entry_seq.NEXTVAL, :paymentId, :entryType, :amount, :relatedEntryId, SYSTIMESTAMP)
            """;

    private final DatabaseClient databaseClient;

    public LedgerEntryR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<LedgerEntry> findByPaymentId(Long paymentId) {
        return databaseClient.sql(FIND_BY_PAYMENT_ID)
                .bind("paymentId", paymentId)
                .map(LedgerEntryRowMapper::toLedgerEntry)
                .all();
    }

    @Override
    public Mono<Void> createReversal(Long paymentId, LedgerEntry debit) {
        return databaseClient.sql(INSERT_REVERSAL)
                .bind("paymentId", paymentId)
                .bind("entryType", EntryType.REVERSAL.name())
                .bind("amount", debit.amount())
                .bind("relatedEntryId", debit.id())
                .fetch()
                .rowsUpdated()
                .then();
    }
}
