package com.pagoclaro.reconciliation.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public record LedgerEntry(
        Long id,
        EntryType type,
        BigDecimal amount,
        Optional<Long> relatedEntryId,
        LocalDateTime createdAt) {

    public boolean isDebit() {
        return type == EntryType.DEBIT;
    }

    public boolean isReversal() {
        return type == EntryType.REVERSAL;
    }
}
