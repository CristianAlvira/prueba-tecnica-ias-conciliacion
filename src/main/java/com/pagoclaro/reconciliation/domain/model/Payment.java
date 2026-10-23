package com.pagoclaro.reconciliation.domain.model;

import java.math.BigDecimal;

public record Payment(Long id, String externalRef, PaymentStatus status, BigDecimal amount) {
}
