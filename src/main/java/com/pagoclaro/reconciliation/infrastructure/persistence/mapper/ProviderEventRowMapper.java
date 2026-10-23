package com.pagoclaro.reconciliation.infrastructure.persistence.mapper;

import com.pagoclaro.reconciliation.domain.model.ProviderEvent;
import com.pagoclaro.reconciliation.domain.model.ProviderStatus;
import io.r2dbc.spi.Readable;

public final class ProviderEventRowMapper {

    private static final String PROVIDER_EVENT_ID = "provider_event_id";
    private static final String PROVIDER_STATUS = "provider_status";

    private ProviderEventRowMapper() {
    }

    public static ProviderEvent toProviderEvent(Readable row) {
        return new ProviderEvent(
                row.get(PROVIDER_EVENT_ID, String.class),
                ProviderStatus.valueOf(row.get(PROVIDER_STATUS, String.class)));
    }
}
