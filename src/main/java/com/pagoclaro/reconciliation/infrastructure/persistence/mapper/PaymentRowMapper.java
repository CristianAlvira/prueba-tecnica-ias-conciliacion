package com.pagoclaro.reconciliation.infrastructure.persistence.mapper;

import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentStatus;
import io.r2dbc.spi.Readable;

import java.math.BigDecimal;

public final class PaymentRowMapper {

    private static final String ID = "id";
    private static final String EXTERNAL_REF = "external_ref";
    private static final String STATUS = "status";
    private static final String AMOUNT = "amount";

    private PaymentRowMapper() {
    }

    public static Payment toPayment(Readable row) {
        return new Payment(
                row.get(ID, Long.class),
                row.get(EXTERNAL_REF, String.class),
                PaymentStatus.valueOf(row.get(STATUS, String.class)),
                row.get(AMOUNT, BigDecimal.class));
    }
}
