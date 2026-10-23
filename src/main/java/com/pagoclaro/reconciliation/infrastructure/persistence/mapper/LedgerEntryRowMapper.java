package com.pagoclaro.reconciliation.infrastructure.persistence.mapper;

import com.pagoclaro.reconciliation.domain.model.EntryType;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import io.r2dbc.spi.Readable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public final class LedgerEntryRowMapper {

    private static final String ID = "id";
    private static final String ENTRY_TYPE = "entry_type";
    private static final String AMOUNT = "amount";
    private static final String RELATED_ENTRY_ID = "related_entry_id";
    private static final String CREATED_AT = "created_at";

    private LedgerEntryRowMapper() {
    }

    public static LedgerEntry toLedgerEntry(Readable row) {
        return new LedgerEntry(
                row.get(ID, Long.class),
                EntryType.valueOf(row.get(ENTRY_TYPE, String.class)),
                row.get(AMOUNT, BigDecimal.class),
                Optional.ofNullable(row.get(RELATED_ENTRY_ID, Long.class)),
                row.get(CREATED_AT, LocalDateTime.class));
    }
}
