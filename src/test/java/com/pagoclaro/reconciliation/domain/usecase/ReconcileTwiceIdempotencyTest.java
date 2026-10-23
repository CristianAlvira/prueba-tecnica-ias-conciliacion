package com.pagoclaro.reconciliation.domain.usecase;

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
import com.pagoclaro.reconciliation.domain.model.ReconciliationResult;
import com.pagoclaro.reconciliation.domain.model.ReviewReason;
import com.pagoclaro.reconciliation.domain.policy.ReconciliationPolicy;
import com.pagoclaro.reconciliation.domain.port.LedgerEntryRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.PaymentRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.SupportAuditRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.TransactionalPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Repetir la conciliación no duplica efectos")
class ReconcileTwiceIdempotencyTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_REF = "PAY-TEST";
    private static final BigDecimal AMOUNT = new BigDecimal("89000");
    private static final Long ORIGINAL_DEBIT_ID = 5002L;
    private static final Long DUPLICATE_DEBIT_ID = 5003L;
    private static final Long FIRST_NEW_ENTRY_ID = 9001L;
    private static final LocalDateTime ORIGINAL_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 23);
    private static final LocalDateTime DUPLICATE_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 25);
    private static final int EXPECTED_AUDITS = 1;

    @Test
    @DisplayName("BX17 dos veces: un solo reverso y una sola auditoría")
    void debeCrearUnSoloReversoAlRepetir() {
        InMemoryDatabase database = new InMemoryDatabase(
                payment(PaymentStatus.CONFIRMED), List.of(approved(), approved()),
                List.of(debit(ORIGINAL_DEBIT_ID, ORIGINAL_DEBIT_TIME), debit(DUPLICATE_DEBIT_ID, DUPLICATE_DEBIT_TIME)));

        ReconciliationResult first = database.reconcile();
        ReconciliationResult second = database.reconcile();

        assertThat(first.decision()).isInstanceOf(Corrected.class);
        assertThat(second.decision()).isEqualTo(new Consistent());
        assertThat(database.ledger).filteredOn(LedgerEntry::isReversal)
                .singleElement()
                .satisfies(reversal -> assertThat(reversal.relatedEntryId()).contains(DUPLICATE_DEBIT_ID));
        assertThat(database.audits).hasSize(EXPECTED_AUDITS).allMatch(audit -> audit.action() == AuditAction.DEBIT_REVERSED);
    }

    @Test
    @DisplayName("AX91 dos veces: confirma una sola vez y audita una sola vez")
    void debeConfirmarUnaSolaVezAlRepetir() {
        InMemoryDatabase database = new InMemoryDatabase(
                payment(PaymentStatus.PENDING), List.of(approved()), List.of(debit(ORIGINAL_DEBIT_ID, ORIGINAL_DEBIT_TIME)));

        ReconciliationResult first = database.reconcile();
        ReconciliationResult second = database.reconcile();

        assertThat(first.decision()).isInstanceOf(Corrected.class);
        assertThat(second.decision()).isEqualTo(new Consistent());
        assertThat(database.payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(database.audits).hasSize(EXPECTED_AUDITS).allMatch(audit -> audit.action() == AuditAction.PAYMENT_CONFIRMED);
    }

    @Test
    @DisplayName("CQ22 dos veces: una sola auditoría de revisión y sin cambios en el ledger")
    void debeAuditarLaRevisionUnaSolaVezAlRepetir() {
        InMemoryDatabase database = new InMemoryDatabase(
                payment(PaymentStatus.PENDING), List.of(approved(), declined()), List.of());

        ReconciliationResult first = database.reconcile();
        ReconciliationResult second = database.reconcile();

        NeedsReview expected = new NeedsReview(ReviewReason.CONTRADICTORY_EVIDENCE);
        assertThat(first.decision()).isEqualTo(expected);
        assertThat(second.decision()).isEqualTo(expected);
        assertThat(database.ledger).isEmpty();
        assertThat(database.payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(database.audits).hasSize(EXPECTED_AUDITS).allMatch(audit -> audit.action() == AuditAction.MANUAL_REVIEW);
    }

    private static Payment payment(PaymentStatus status) {
        return new Payment(PAYMENT_ID, PAYMENT_REF, status, AMOUNT);
    }

    private static ProviderEvent approved() {
        return new ProviderEvent("EVT-APPROVED", ProviderStatus.APPROVED);
    }

    private static ProviderEvent declined() {
        return new ProviderEvent("EVT-DECLINED", ProviderStatus.DECLINED);
    }

    private static LedgerEntry debit(Long id, LocalDateTime createdAt) {
        return new LedgerEntry(id, EntryType.DEBIT, AMOUNT, Optional.empty(), createdAt);
    }

    private static final class InMemoryDatabase
            implements PaymentRepositoryPort, LedgerEntryRepositoryPort, SupportAuditRepositoryPort, TransactionalPort {

        private final List<ProviderEvent> events;
        private final List<LedgerEntry> ledger;
        private final List<AuditRecord> audits = new ArrayList<>();
        private final ReconcilePaymentUseCase useCase;
        private Payment payment;
        private long nextEntryId = FIRST_NEW_ENTRY_ID;

        private InMemoryDatabase(Payment payment, List<ProviderEvent> events, List<LedgerEntry> ledger) {
            this.payment = payment;
            this.events = List.copyOf(events);
            this.ledger = new ArrayList<>(ledger);
            this.useCase = new ReconcilePaymentUseCase(
                    this, paymentId -> Flux.fromIterable(this.events), this, this, this, new ReconciliationPolicy());
        }

        private ReconciliationResult reconcile() {
            return useCase.execute(PAYMENT_REF).block();
        }

        @Override
        public Mono<Payment> findByExternalRefForUpdate(String externalRef) {
            return Mono.fromSupplier(() -> payment).filter(found -> found.externalRef().equals(externalRef));
        }

        @Override
        public Mono<Void> markConfirmed(Long paymentId) {
            return Mono.fromRunnable(() -> payment =
                    new Payment(payment.id(), payment.externalRef(), PaymentStatus.CONFIRMED, payment.amount()));
        }

        @Override
        public Flux<LedgerEntry> findByPaymentId(Long paymentId) {
            return Flux.defer(() -> Flux.fromIterable(List.copyOf(ledger)));
        }

        @Override
        public Mono<Void> createReversal(Long paymentId, LedgerEntry debit) {
            return Mono.fromRunnable(() -> ledger.add(new LedgerEntry(
                    nextEntryId++, EntryType.REVERSAL, debit.amount(), Optional.of(debit.id()), LocalDateTime.now())));
        }

        @Override
        public Mono<Boolean> exists(AuditRecord audit) {
            return Mono.fromSupplier(() -> audits.contains(audit));
        }

        @Override
        public Mono<Void> save(AuditRecord audit) {
            return Mono.fromRunnable(() -> audits.add(audit));
        }

        @Override
        public <T> Mono<T> execute(Mono<T> operation) {
            return operation;
        }
    }
}
