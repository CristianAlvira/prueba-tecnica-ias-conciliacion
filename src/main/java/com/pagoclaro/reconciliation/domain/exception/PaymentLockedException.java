package com.pagoclaro.reconciliation.domain.exception;

public class PaymentLockedException extends RuntimeException {

    public PaymentLockedException(String externalRef, Throwable cause) {
        super("El pago " + externalRef + " está siendo conciliado por otra solicitud", cause);
    }
}
