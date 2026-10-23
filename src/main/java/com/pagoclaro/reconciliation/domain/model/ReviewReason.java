package com.pagoclaro.reconciliation.domain.model;

public enum ReviewReason {
    CONTRADICTORY_EVIDENCE("El proveedor reportó aprobación y rechazo para el mismo pago"),
    NO_PROVIDER_EVIDENCE("No hay eventos del proveedor para decidir"),
    PROVIDER_DECLINED("El proveedor rechazó el pago y el estado o el ledger no coinciden con ese rechazo"),
    STATUS_CONTRADICTS_PROVIDER("El pago está rechazado, pero el proveedor lo aprobó"),
    UNEXPECTED_LEDGER("Los movimientos contables no coinciden con el monto del pago o tienen reversos inválidos"),
    MISSING_DEBIT("El proveedor aprobó el pago, pero no existe un débito vigente");

    private final String description;

    ReviewReason(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
