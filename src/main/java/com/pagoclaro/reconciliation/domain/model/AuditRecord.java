package com.pagoclaro.reconciliation.domain.model;

public record AuditRecord(Long paymentId, AuditAction action, String reason) {
}
