package com.pagoclaro.reconciliation.domain.policy;

import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ConfirmPayment;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ReverseDebit;
import com.pagoclaro.reconciliation.domain.model.EntryType;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import com.pagoclaro.reconciliation.domain.model.Payment;
import com.pagoclaro.reconciliation.domain.model.PaymentEvidence;
import com.pagoclaro.reconciliation.domain.model.PaymentStatus;
import com.pagoclaro.reconciliation.domain.model.ProviderEvent;
import com.pagoclaro.reconciliation.domain.model.ProviderStatus;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Consistent;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReviewReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Política de conciliación")
class ReconciliationPolicyTest {

    private static final Long PAYMENT_ID = 1L;
    private static final String PAYMENT_REF = "PAY-TEST";
    private static final BigDecimal AX91_AMOUNT = new BigDecimal("125000");
    private static final BigDecimal BX17_AMOUNT = new BigDecimal("89000");
    private static final BigDecimal CQ22_AMOUNT = new BigDecimal("54000");
    private static final BigDecimal DK44_AMOUNT = new BigDecimal("31000");
    private static final BigDecimal OTHER_AMOUNT = new BigDecimal("1000");
    private static final Long ORIGINAL_DEBIT_ID = 5002L;
    private static final Long DUPLICATE_DEBIT_ID = 5003L;
    private static final Long REVERSAL_ID = 5004L;
    private static final LocalDateTime ORIGINAL_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 23);
    private static final LocalDateTime DUPLICATE_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 25);
    private static final LocalDateTime REVERSAL_TIME = LocalDateTime.of(2026, 10, 13, 10, 0, 0);

    private final ReconciliationPolicy policy = new ReconciliationPolicy();

    @Test
    @DisplayName("AX91: confirma el pago pendiente que el proveedor aprobó y ya tiene su débito")
    void debeConfirmarPagoPendienteAprobadoConDebito() {
        PaymentEvidence evidence = evidence(PaymentStatus.PENDING, AX91_AMOUNT,
                List.of(approved()), List.of(debit(ORIGINAL_DEBIT_ID, AX91_AMOUNT, ORIGINAL_DEBIT_TIME)));

        assertThat(policy.decide(evidence)).isEqualTo(new Corrected(List.of(new ConfirmPayment())));
    }

    @Test
    @DisplayName("BX17: reversa el débito duplicado más reciente y conserva el original")
    void debeReversarElDebitoDuplicadoMasReciente() {
        LedgerEntry duplicate = debit(DUPLICATE_DEBIT_ID, BX17_AMOUNT, DUPLICATE_DEBIT_TIME);
        PaymentEvidence evidence = evidence(PaymentStatus.CONFIRMED, BX17_AMOUNT,
                List.of(approved(), approved()),
                List.of(debit(ORIGINAL_DEBIT_ID, BX17_AMOUNT, ORIGINAL_DEBIT_TIME), duplicate));

        assertThat(policy.decide(evidence)).isEqualTo(new Corrected(List.of(new ReverseDebit(duplicate))));
    }

    @Test
    @DisplayName("BX17 ya corregido: no propone otro reverso")
    void debeQuedarConsistenteCuandoElDuplicadoYaFueReversado() {
        PaymentEvidence evidence = evidence(PaymentStatus.CONFIRMED, BX17_AMOUNT,
                List.of(approved(), approved()),
                List.of(debit(ORIGINAL_DEBIT_ID, BX17_AMOUNT, ORIGINAL_DEBIT_TIME),
                        debit(DUPLICATE_DEBIT_ID, BX17_AMOUNT, DUPLICATE_DEBIT_TIME),
                        reversal(REVERSAL_ID, BX17_AMOUNT, DUPLICATE_DEBIT_ID)));

        assertThat(policy.decide(evidence)).isEqualTo(new Consistent());
    }

    @Test
    @DisplayName("CQ22: con aprobación y rechazo a la vez, pide revisión manual")
    void debePedirRevisionCuandoLaEvidenciaEsContradictoria() {
        PaymentEvidence evidence = evidence(PaymentStatus.PENDING, CQ22_AMOUNT,
                List.of(approved(), declined()), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.CONTRADICTORY_EVIDENCE));
    }

    @Test
    @DisplayName("DK44: el pago sano no recibe cambios")
    void debeDejarSinCambiosElPagoSano() {
        PaymentEvidence evidence = evidence(PaymentStatus.CONFIRMED, DK44_AMOUNT,
                List.of(approved()), List.of(debit(ORIGINAL_DEBIT_ID, DK44_AMOUNT, ORIGINAL_DEBIT_TIME)));

        assertThat(policy.decide(evidence)).isEqualTo(new Consistent());
    }

    @Test
    @DisplayName("Sin eventos del proveedor, pide revisión manual")
    void debePedirRevisionSinEventosDelProveedor() {
        PaymentEvidence evidence = evidence(PaymentStatus.PENDING, DK44_AMOUNT, List.of(), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.NO_PROVIDER_EVIDENCE));
    }

    @Test
    @DisplayName("Con un débito por un monto distinto al del pago, pide revisión manual")
    void debePedirRevisionCuandoUnDebitoTieneOtroMonto() {
        PaymentEvidence evidence = evidence(PaymentStatus.CONFIRMED, DK44_AMOUNT,
                List.of(approved()), List.of(debit(ORIGINAL_DEBIT_ID, OTHER_AMOUNT, ORIGINAL_DEBIT_TIME)));

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.UNEXPECTED_LEDGER));
    }

    @Test
    @DisplayName("Con un reverso que no apunta a un débito del pago, pide revisión manual")
    void debePedirRevisionCuandoElReversoNoApuntaAUnDebitoDelPago() {
        PaymentEvidence evidence = evidence(PaymentStatus.CONFIRMED, DK44_AMOUNT,
                List.of(approved()),
                List.of(debit(ORIGINAL_DEBIT_ID, DK44_AMOUNT, ORIGINAL_DEBIT_TIME),
                        reversal(REVERSAL_ID, DK44_AMOUNT, DUPLICATE_DEBIT_ID)));

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.UNEXPECTED_LEDGER));
    }

    @Test
    @DisplayName("Pago rechazado que el proveedor aprobó: pide revisión manual")
    void debePedirRevisionCuandoUnPagoRechazadoFueAprobado() {
        PaymentEvidence evidence = evidence(PaymentStatus.REJECTED, DK44_AMOUNT, List.of(approved()), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.STATUS_CONTRADICTS_PROVIDER));
    }

    @Test
    @DisplayName("Aprobado pero sin débito: no crea débitos, pide revisión manual")
    void debePedirRevisionCuandoFaltaElDebito() {
        PaymentEvidence evidence = evidence(PaymentStatus.PENDING, DK44_AMOUNT, List.of(approved()), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.MISSING_DEBIT));
    }

    @Test
    @DisplayName("Pago pendiente que el proveedor rechazó: no lo cambia, pide revisión manual")
    void debePedirRevisionCuandoUnPagoPendienteFueRechazado() {
        PaymentEvidence evidence = evidence(PaymentStatus.PENDING, DK44_AMOUNT, List.of(declined()), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new NeedsReview(ReviewReason.PROVIDER_DECLINED));
    }

    @Test
    @DisplayName("Pago rechazado, sin débito y rechazado por el proveedor: es consistente")
    void debeDejarConsistenteUnPagoRechazadoSinDebito() {
        PaymentEvidence evidence = evidence(PaymentStatus.REJECTED, DK44_AMOUNT, List.of(declined()), List.of());

        assertThat(policy.decide(evidence)).isEqualTo(new Consistent());
    }

    private static PaymentEvidence evidence(PaymentStatus status, BigDecimal amount,
                                            List<ProviderEvent> events, List<LedgerEntry> ledger) {
        return new PaymentEvidence(new Payment(PAYMENT_ID, PAYMENT_REF, status, amount), events, ledger);
    }

    private static ProviderEvent approved() {
        return new ProviderEvent("EVT-APPROVED", ProviderStatus.APPROVED);
    }

    private static ProviderEvent declined() {
        return new ProviderEvent("EVT-DECLINED", ProviderStatus.DECLINED);
    }

    private static LedgerEntry debit(Long id, BigDecimal amount, LocalDateTime createdAt) {
        return new LedgerEntry(id, EntryType.DEBIT, amount, Optional.empty(), createdAt);
    }

    private static LedgerEntry reversal(Long id, BigDecimal amount, Long relatedEntryId) {
        return new LedgerEntry(id, EntryType.REVERSAL, amount, Optional.of(relatedEntryId), REVERSAL_TIME);
    }
}
