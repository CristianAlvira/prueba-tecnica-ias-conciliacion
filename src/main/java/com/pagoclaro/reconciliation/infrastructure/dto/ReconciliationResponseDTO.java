package com.pagoclaro.reconciliation.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReconciliationResponseDTO(
        String externalRef,
        ReconciliationOutcome outcome,
        List<String> actions,
        String reason) {
}
