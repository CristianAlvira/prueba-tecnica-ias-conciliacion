package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import io.r2dbc.spi.R2dbcException;

import java.util.Objects;
import java.util.stream.Stream;

public final class OracleErrors {

    private static final int RESOURCE_BUSY = 54;

    private OracleErrors() {
    }

    public static boolean isResourceBusy(Throwable error) {
        return Stream.iterate(error, Objects::nonNull, Throwable::getCause)
                .anyMatch(cause -> cause instanceof R2dbcException r2dbcError
                        && r2dbcError.getErrorCode() == RESOURCE_BUSY);
    }
}
