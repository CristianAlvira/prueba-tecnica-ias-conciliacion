package com.pagoclaro.reconciliation.domain.model;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record PaymentEvidence(Payment payment, List<ProviderEvent> events, List<LedgerEntry> ledger) {

    public PaymentEvidence {
        events = List.copyOf(events);
        ledger = List.copyOf(ledger);
    }

    public boolean hasProviderStatus(ProviderStatus status) {
        return events.stream().anyMatch(event -> event.status() == status);
    }

    public boolean isLedgerWellFormed() {
        return allEntriesMatchPaymentAmount() && eachReversalPointsToADistinctDebit();
    }

    public List<LedgerEntry> activeDebits() {
        Set<Long> reversedDebitIds = reversedDebitIds().collect(Collectors.toSet());
        return debits()
                .filter(debit -> !reversedDebitIds.contains(debit.id()))
                .sorted(Comparator.comparing(LedgerEntry::createdAt).thenComparing(LedgerEntry::id))
                .toList();
    }

    private boolean allEntriesMatchPaymentAmount() {
        return ledger.stream().allMatch(entry -> entry.amount().compareTo(payment.amount()) == 0);
    }

    private boolean eachReversalPointsToADistinctDebit() {
        Set<Long> debitIds = debits().map(LedgerEntry::id).collect(Collectors.toSet());
        List<Long> reversedIds = reversedDebitIds().toList();
        return reversedIds.size() == reversals().count()
                && reversedIds.stream().allMatch(debitIds::contains)
                && reversedIds.stream().distinct().count() == reversedIds.size();
    }

    private Stream<Long> reversedDebitIds() {
        return reversals().flatMap(reversal -> reversal.relatedEntryId().stream());
    }

    private Stream<LedgerEntry> debits() {
        return ledger.stream().filter(LedgerEntry::isDebit);
    }

    private Stream<LedgerEntry> reversals() {
        return ledger.stream().filter(LedgerEntry::isReversal);
    }
}
