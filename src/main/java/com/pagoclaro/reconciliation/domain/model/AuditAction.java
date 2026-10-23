package com.pagoclaro.reconciliation.domain.model;

public enum AuditAction {
    PAYMENT_CONFIRMED("Pago confirmado: el proveedor lo aprobó y tiene su débito"),
    DEBIT_REVERSED("Reverso de un débito duplicado del pago"),
    MANUAL_REVIEW("Requiere revisión manual");

    private final String description;

    AuditAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
