package com.pagoclaro.reconciliation.domain.usecase;

import com.pagoclaro.reconciliation.domain.exception.PaymentNotFoundException;
import com.pagoclaro.reconciliation.domain.model.AuditAction;
import com.pagoclaro.reconciliation.domain.model.AuditRecord;
import com.pagoclaro.reconciliation.domain.model.EntryType;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentStatus;
import com.pagoclaro.reconciliation.domain.model.ProviderEvent;
import com.pagoclaro.reconciliation.domain.model.ProviderStatus;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Consistent;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReviewReason;
import com.pagoclaro.reconciliation.domain.policy.ReconciliationPolicy;
import com.pagoclaro.reconciliation.domain.port.LedgerEntryRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.PaymentRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.ProviderEventRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.SupportAuditRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.TransactionalPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Caso de uso de conciliación")
class ReconcilePaymentUseCaseTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_REF = "PAY-TEST";
    private static final String UNKNOWN_REF = "PAY-NO-EXISTE";
    private static final BigDecimal AMOUNT = new BigDecimal("89000");
    private static final Long ORIGINAL_DEBIT_ID = 5002L;
    private static final Long DUPLICATE_DEBIT_ID = 5003L;
    private static final LocalDateTime ORIGINAL_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 23);
    private static final LocalDateTime DUPLICATE_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 25);

    @Mock
    private PaymentRepositoryPort payments;
    @Mock
    private ProviderEventRepositoryPort providerEvents;
    @Mock
    private LedgerEntryRepositoryPort ledgerEntries;
    @Mock
    private SupportAuditRepositoryPort audits;
    @Mock
    private TransactionalPort transactional;

    private ReconcilePaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        when(transactional.execute(any())).thenAnswer(invocation -> invocation.getArgument(0));
        useCase = new ReconcilePaymentUseCase(
                payments, providerEvents, ledgerEntries, audits, transactional, new ReconciliationPolicy());
    }

    @Test
    @DisplayName("Si la referencia no existe, responde con error y no toca nada")
    void debeFallarCuandoLaReferenciaNoExiste() {
        when(payments.findByExternalRefForUpdate(UNKNOWN_REF)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(UNKNOWN_REF))
                .expectError(PaymentNotFoundException.class)
                .verify();

        verifyNoInteractions(providerEvents, ledgerEntries, audits);
    }

    @Test
    @DisplayName("AX91: confirma el pago y audita la confirmación, sin tocar el ledger")
    void debeConfirmarElPagoYAuditarlo() {
        givenPayment(PaymentStatus.PENDING, List.of(approved()), List.of(originalDebit()));
        when(payments.markConfirmed(PAYMENT_ID)).thenReturn(Mono.empty());
        when(audits.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(PAYMENT_REF))
                .expectNextMatches(result -> result.decision() instanceof Corrected)
                .verifyComplete();

        verify(payments).markConfirmed(PAYMENT_ID);
        verify(audits).save(audit(AuditAction.PAYMENT_CONFIRMED, AuditAction.PAYMENT_CONFIRMED.description()));
        verify(ledgerEntries, never()).createReversal(any(), any());
    }

    @Test
    @DisplayName("BX17: crea el reverso del débito duplicado y lo audita, sin cambiar el estado")
    void debeReversarElDuplicadoYAuditarlo() {
        LedgerEntry duplicate = duplicateDebit();
        givenPayment(PaymentStatus.CONFIRMED, List.of(approved(), approved()), List.of(originalDebit(), duplicate));
        when(ledgerEntries.createReversal(PAYMENT_ID, duplicate)).thenReturn(Mono.empty());
        when(audits.save(any())).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(PAYMENT_REF))
                .expectNextMatches(result -> result.decision() instanceof Corrected)
                .verifyComplete();

        verify(ledgerEntries).createReversal(PAYMENT_ID, duplicate);
        verify(audits).save(audit(AuditAction.DEBIT_REVERSED, AuditAction.DEBIT_REVERSED.description()));
        verify(payments, never()).markConfirmed(any());
    }

    @Test
    @DisplayName("CQ22: registra la revisión manual una vez y no cambia el pago ni el ledger")
    void debeAuditarLaRevisionManualSinCambiarDatos() {
        AuditRecord review = audit(AuditAction.MANUAL_REVIEW, ReviewReason.CONTRADICTORY_EVIDENCE.description());
        givenPayment(PaymentStatus.PENDING, List.of(approved(), declined()), List.of());
        when(audits.exists(review)).thenReturn(Mono.just(false));
        when(audits.save(review)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(PAYMENT_REF))
                .expectNextMatches(result -> result.decision().equals(new NeedsReview(ReviewReason.CONTRADICTORY_EVIDENCE)))
                .verifyComplete();

        verify(audits).save(review);
        verify(payments, never()).markConfirmed(any());
        verify(ledgerEntries, never()).createReversal(any(), any());
    }

    @Test
    @DisplayName("CQ22 repetido: si la revisión ya está auditada, no la vuelve a registrar")
    void noDebeDuplicarLaAuditoriaDeRevision() {
        AuditRecord review = audit(AuditAction.MANUAL_REVIEW, ReviewReason.CONTRADICTORY_EVIDENCE.description());
        givenPayment(PaymentStatus.PENDING, List.of(approved(), declined()), List.of());
        when(audits.exists(review)).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(PAYMENT_REF))
                .expectNextMatches(result -> result.decision() instanceof NeedsReview)
                .verifyComplete();

        verify(audits, never()).save(any());
    }

    @Test
    @DisplayName("DK44: el pago sano no genera escrituras")
    void noDebeEscribirNadaParaElPagoSano() {
        givenPayment(PaymentStatus.CONFIRMED, List.of(approved()), List.of(originalDebit()));

        StepVerifier.create(useCase.execute(PAYMENT_REF))
                .expectNextMatches(result -> result.decision() instanceof Consistent)
                .verifyComplete();

        verify(payments, never()).markConfirmed(any());
        verify(ledgerEntries, never()).createReversal(any(), any());
        verifyNoInteractions(audits);
    }

    private void givenPayment(PaymentStatus status, List<ProviderEvent> events, List<LedgerEntry> ledger) {
        when(payments.findByExternalRefForUpdate(PAYMENT_REF))
                .thenReturn(Mono.just(new Payment(PAYMENT_ID, PAYMENT_REF, status, AMOUNT)));
        when(providerEvents.findByPaymentId(PAYMENT_ID)).thenReturn(Flux.fromIterable(events));
        when(ledgerEntries.findByPaymentId(PAYMENT_ID)).thenReturn(Flux.fromIterable(ledger));
    }

    private static AuditRecord audit(AuditAction action, String reason) {
        return new AuditRecord(PAYMENT_ID, action, reason);
    }

    private static ProviderEvent approved() {
        return new ProviderEvent("EVT-APPROVED", ProviderStatus.APPROVED);
    }

    private static ProviderEvent declined() {
        return new ProviderEvent("EVT-DECLINED", ProviderStatus.DECLINED);
    }

    private static LedgerEntry originalDebit() {
        return new LedgerEntry(ORIGINAL_DEBIT_ID, EntryType.DEBIT, AMOUNT, Optional.empty(), ORIGINAL_DEBIT_TIME);
    }

    private static LedgerEntry duplicateDebit() {
        return new LedgerEntry(DUPLICATE_DEBIT_ID, EntryType.DEBIT, AMOUNT, Optional.empty(), DUPLICATE_DEBIT_TIME);
    }
}
