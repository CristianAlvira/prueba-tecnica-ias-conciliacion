package com.pagoclaro.reconciliation.domain.usecase;

import com.pagoclaro.reconciliation.domain.exception.PaymentNotFoundException;
import com.pagoclaro.reconciliation.domain.model.AuditAction;
import com.pagoclaro.reconciliation.domain.model.AuditRecord;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ConfirmPayment;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ReverseDebit;
import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentEvidence;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Consistent;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReconciliationResult;
import com.pagoclaro.reconciliation.domain.policy.ReconciliationPolicy;
import com.pagoclaro.reconciliation.domain.port.LedgerEntryRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.PaymentRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.ProviderEventRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.SupportAuditRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.TransactionalPort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReconcilePaymentUseCase {

    private final PaymentRepositoryPort payments;
    private final ProviderEventRepositoryPort providerEvents;
    private final LedgerEntryRepositoryPort ledgerEntries;
    private final SupportAuditRepositoryPort audits;
    private final TransactionalPort transactional;
    private final ReconciliationPolicy policy;

    public ReconcilePaymentUseCase(PaymentRepositoryPort payments,
                                   ProviderEventRepositoryPort providerEvents,
                                   LedgerEntryRepositoryPort ledgerEntries,
                                   SupportAuditRepositoryPort audits,
                                   TransactionalPort transactional,
                                   ReconciliationPolicy policy) {
        this.payments = payments;
        this.providerEvents = providerEvents;
        this.ledgerEntries = ledgerEntries;
        this.audits = audits;
        this.transactional = transactional;
        this.policy = policy;
    }

    public Mono<ReconciliationResult> execute(String externalRef) {
        return transactional.execute(
                payments.findByExternalRefForUpdate(externalRef)
                        .switchIfEmpty(Mono.error(() -> new PaymentNotFoundException(externalRef)))
                        .flatMap(this::loadEvidence)
                        .flatMap(this::reconcile));
    }

    private Mono<PaymentEvidence> loadEvidence(Payment payment) {
        return providerEvents.findByPaymentId(payment.id()).collectList()
                .zipWhen(events -> ledgerEntries.findByPaymentId(payment.id()).collectList())
                .map(evidence -> new PaymentEvidence(payment, evidence.getT1(), evidence.getT2()));
    }

    private Mono<ReconciliationResult> reconcile(PaymentEvidence evidence) {
        Payment payment = evidence.payment();
        ReconciliationDecision decision = policy.decide(evidence);
        return apply(payment, decision)
                .thenReturn(new ReconciliationResult(payment.externalRef(), decision));
    }

    private Mono<Void> apply(Payment payment, ReconciliationDecision decision) {
        return switch (decision) {
            case Consistent consistent -> Mono.empty();
            case Corrected corrected -> Flux.fromIterable(corrected.actions())
                    .concatMap(action -> applyAction(payment, action))
                    .then();
            case NeedsReview review -> auditOnce(
                    new AuditRecord(payment.id(), AuditAction.MANUAL_REVIEW, review.reason().description()));
        };
    }

    private Mono<Void> applyAction(Payment payment, CorrectiveAction action) {
        return switch (action) {
            case ConfirmPayment confirm -> payments.markConfirmed(payment.id())
                    .then(audits.save(audit(payment, AuditAction.PAYMENT_CONFIRMED)));
            case ReverseDebit reverse -> ledgerEntries.createReversal(payment.id(), reverse.debit())
                    .then(audits.save(audit(payment, AuditAction.DEBIT_REVERSED)));
        };
    }

    private Mono<Void> auditOnce(AuditRecord audit) {
        return audits.exists(audit)
                .flatMap(alreadyAudited -> alreadyAudited ? Mono.empty() : audits.save(audit));
    }

    private AuditRecord audit(Payment payment, AuditAction action) {
        return new AuditRecord(payment.id(), action, action.description());
    }
}
