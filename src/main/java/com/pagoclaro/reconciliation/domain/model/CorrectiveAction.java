package com.pagoclaro.reconciliation.domain.model;

public sealed interface CorrectiveAction {

    record ConfirmPayment() implements CorrectiveAction {
    }

    record ReverseDebit(LedgerEntry debit) implements CorrectiveAction {
    }
}
