package com.pagoclaro.reconciliation.domain.model;

import java.util.List;

public sealed interface ReconciliationDecision {

    record Consistent() implements ReconciliationDecision {
    }

    record Corrected(List<CorrectiveAction> actions) implements ReconciliationDecision {

        public Corrected {
            actions = List.copyOf(actions);
        }
    }

    record NeedsReview(ReviewReason reason) implements ReconciliationDecision {
    }
}
