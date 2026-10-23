package com.pagoclaro.reconciliation.infrastructure.persistence.adapter;

import io.r2dbc.spi.R2dbcDataIntegrityViolationException;
import io.r2dbc.spi.R2dbcTransientResourceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.UncategorizedR2dbcException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Errores de Oracle")
class OracleErrorsTest {

    private static final int RESOURCE_BUSY = 54;
    private static final int UNIQUE_VIOLATION = 1;
    private static final String LOCK_SQL_STATE = "61000";
    private static final String INTEGRITY_SQL_STATE = "23000";

    @Test
    @DisplayName("Reconoce el recurso ocupado (ORA-00054) aunque Spring lo envuelva")
    void debeReconocerElBloqueoOcupadoEnvuelto() {
        R2dbcTransientResourceException busy =
                new R2dbcTransientResourceException("ORA-00054", LOCK_SQL_STATE, RESOURCE_BUSY);

        assertThat(OracleErrors.isResourceBusy(new UncategorizedR2dbcException("lock", "SELECT", busy))).isTrue();
    }

    @Test
    @DisplayName("No confunde otros errores con el bloqueo ocupado")
    void noDebeConfundirOtrosErroresConElBloqueo() {
        R2dbcDataIntegrityViolationException duplicate =
                new R2dbcDataIntegrityViolationException("ORA-00001", INTEGRITY_SQL_STATE, UNIQUE_VIOLATION);

        assertThat(OracleErrors.isResourceBusy(duplicate)).isFalse();
        assertThat(OracleErrors.isResourceBusy(new IllegalStateException())).isFalse();
    }
}
