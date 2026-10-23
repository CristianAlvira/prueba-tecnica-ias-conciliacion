package com.pagoclaro.reconciliation.domain.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String externalRef) {
        super("No existe un pago con la referencia " + externalRef);
    }
}
