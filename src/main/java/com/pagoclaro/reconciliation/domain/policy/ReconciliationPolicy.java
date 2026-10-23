package com.pagoclaro.reconciliation.domain.policy;

import com.pagoclaro.reconciliation.domain.model.CorrectiveAction;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ConfirmPayment;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ReverseDebit;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentEvidence;
import com.pagoclaro.reconciliation.domain.model.PaymentStatus;
import com.pagoclaro.reconciliation.domain.model.ProviderStatus;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Consistent;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReviewReason;

import java.util.List;
import java.util.stream.Stream;

public class ReconciliationPolicy {

    private static final int DEBITS_TO_KEEP = 1;

    public ReconciliationDecision decide(PaymentEvidence evidence) {
        boolean approved = evidence.hasProviderStatus(ProviderStatus.APPROVED);
        boolean declined = evidence.hasProviderStatus(ProviderStatus.DECLINED);

        if (approved && declined) {
            return new NeedsReview(ReviewReason.CONTRADICTORY_EVIDENCE);
        }
        if (approved) {
            return decideApproved(evidence);
        }
        if (declined) {
            return decideDeclined(evidence);
        }
        return new NeedsReview(ReviewReason.NO_PROVIDER_EVIDENCE);
    }

    private ReconciliationDecision decideApproved(PaymentEvidence evidence) {
        if (evidence.payment().status() == PaymentStatus.REJECTED) {
            return new NeedsReview(ReviewReason.STATUS_CONTRADICTS_PROVIDER);
        }
        if (!evidence.isLedgerWellFormed()) {
            return new NeedsReview(ReviewReason.UNEXPECTED_LEDGER);
        }

        List<LedgerEntry> activeDebits = evidence.activeDebits();
        if (activeDebits.isEmpty()) {
            return new NeedsReview(ReviewReason.MISSING_DEBIT);
        }

        List<CorrectiveAction> actions = Stream.concat(
                        confirmationIfPending(evidence.payment()),
                        reversalsOfDuplicates(activeDebits))
                .toList();
        return actions.isEmpty() ? new Consistent() : new Corrected(actions);
    }

    private ReconciliationDecision decideDeclined(PaymentEvidence evidence) {
        boolean rejectedWithoutDebit = evidence.payment().status() == PaymentStatus.REJECTED
                && evidence.activeDebits().isEmpty();
        return rejectedWithoutDebit ? new Consistent() : new NeedsReview(ReviewReason.PROVIDER_DECLINED);
    }

    private Stream<CorrectiveAction> confirmationIfPending(Payment payment) {
        return payment.status() == PaymentStatus.PENDING ? Stream.of(new ConfirmPayment()) : Stream.empty();
    }

    private Stream<CorrectiveAction> reversalsOfDuplicates(List<LedgerEntry> activeDebits) {
        return activeDebits.stream().skip(DEBITS_TO_KEEP).map(ReverseDebit::new);
    }
}
