package com.pagoclaro.reconciliation.infrastructure.mapper;

import com.pagoclaro.reconciliation.domain.model.CorrectiveAction;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ConfirmPayment;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ReverseDebit;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Consistent;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReconciliationResult;
import com.pagoclaro.reconciliation.infrastructure.dto.ReconciliationOutcome;
import com.pagoclaro.reconciliation.infrastructure.dto.ReconciliationResponseDTO;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResponseMapper {

    private static final String CONFIRMATION_ACTION = "Pago confirmado";
    private static final String REVERSAL_ACTION = "Reverso del débito %d";

    public ReconciliationResponseDTO toResponse(ReconciliationResult result) {
        String externalRef = result.externalRef();
        return switch (result.decision()) {
            case Consistent consistent ->
                    new ReconciliationResponseDTO(externalRef, ReconciliationOutcome.CONSISTENT, List.of(), null);
            case Corrected corrected -> new ReconciliationResponseDTO(externalRef, ReconciliationOutcome.CORRECTED,
                    corrected.actions().stream().map(this::describe).toList(), null);
            case NeedsReview review -> new ReconciliationResponseDTO(externalRef, ReconciliationOutcome.NEEDS_REVIEW,
                    List.of(), review.reason().description());
        };
    }

    private String describe(CorrectiveAction action) {
        return switch (action) {
            case ConfirmPayment confirm -> CONFIRMATION_ACTION;
            case ReverseDebit reverse -> REVERSAL_ACTION.formatted(reverse.debit().id());
        };
    }
}
