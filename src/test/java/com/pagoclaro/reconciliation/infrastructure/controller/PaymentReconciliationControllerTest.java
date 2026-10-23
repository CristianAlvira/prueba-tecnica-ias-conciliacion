package com.pagoclaro.reconciliation.infrastructure.controller;

import com.pagoclaro.reconciliation.domain.exception.PaymentLockedException;
import com.pagoclaro.reconciliation.domain.exception.PaymentNotFoundException;
import com.pagoclaro.reconciliation.domain.model.CorrectiveAction.ReverseDebit;
import com.pagoclaro.reconciliation.domain.model.EntryType;
import com.pagoclaro.reconciliation.domain.model.LedgerEntry;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.Corrected;
import com.pagoclaro.reconciliation.domain.model.ReconciliationDecision.NeedsReview;
import com.pagoclaro.reconciliation.domain.model.ReconciliationResult;
import com.pagoclaro.reconciliation.domain.model.ReviewReason;
import com.pagoclaro.reconciliation.domain.usecase.ReconcilePaymentUseCase;
import com.pagoclaro.reconciliation.infrastructure.exception.GlobalExceptionHandler;
import com.pagoclaro.reconciliation.infrastructure.mapper.ResponseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = PaymentReconciliationController.class)
@Import({ResponseMapper.class, GlobalExceptionHandler.class})
@DisplayName("API de conciliación")
class PaymentReconciliationControllerTest {

    private static final String RECONCILE_URI = "/api/support/payments/{externalRef}/reconcile";
    private static final String BX17_REF = "PAY-BX17";
    private static final String CQ22_REF = "PAY-CQ22";
    private static final String UNKNOWN_REF = "PAY-NO-EXISTE";
    private static final String TOO_LONG_REF = "PAY-" + "X".repeat(40);
    private static final Long DUPLICATE_DEBIT_ID = 5003L;
    private static final BigDecimal BX17_AMOUNT = new BigDecimal("89000");
    private static final LocalDateTime DUPLICATE_DEBIT_TIME = LocalDateTime.of(2026, 10, 13, 9, 41, 25);

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ReconcilePaymentUseCase reconcilePayment;

    @Test
    @DisplayName("Responde 200 con las acciones aplicadas")
    void debeResponderLasAccionesAplicadas() {
        LedgerEntry duplicate = new LedgerEntry(
                DUPLICATE_DEBIT_ID, EntryType.DEBIT, BX17_AMOUNT, Optional.empty(), DUPLICATE_DEBIT_TIME);
        when(reconcilePayment.execute(BX17_REF)).thenReturn(Mono.just(
                new ReconciliationResult(BX17_REF, new Corrected(List.of(new ReverseDebit(duplicate))))));

        webTestClient.post().uri(RECONCILE_URI, BX17_REF).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.externalRef").isEqualTo(BX17_REF)
                .jsonPath("$.outcome").isEqualTo("CORRECTED")
                .jsonPath("$.actions[0]").isEqualTo("Reverso del débito 5003")
                .jsonPath("$.reason").doesNotExist();
    }

    @Test
    @DisplayName("Responde 200 con el motivo cuando requiere revisión manual")
    void debeResponderElMotivoDeRevision() {
        when(reconcilePayment.execute(CQ22_REF)).thenReturn(Mono.just(
                new ReconciliationResult(CQ22_REF, new NeedsReview(ReviewReason.CONTRADICTORY_EVIDENCE))));

        webTestClient.post().uri(RECONCILE_URI, CQ22_REF).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.outcome").isEqualTo("NEEDS_REVIEW")
                .jsonPath("$.actions").isEmpty()
                .jsonPath("$.reason").isEqualTo(ReviewReason.CONTRADICTORY_EVIDENCE.description());
    }

    @Test
    @DisplayName("Responde 404 si la referencia no existe")
    void debeResponder404SiNoExiste() {
        when(reconcilePayment.execute(UNKNOWN_REF)).thenReturn(Mono.error(new PaymentNotFoundException(UNKNOWN_REF)));

        webTestClient.post().uri(RECONCILE_URI, UNKNOWN_REF).exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").isEqualTo("No existe un pago con la referencia " + UNKNOWN_REF);
    }

    @Test
    @DisplayName("Responde 409 si el pago está bloqueado por otra conciliación")
    void debeResponder409SiElPagoEstaBloqueado() {
        when(reconcilePayment.execute(BX17_REF))
                .thenReturn(Mono.error(new PaymentLockedException(BX17_REF, new IllegalStateException())));

        webTestClient.post().uri(RECONCILE_URI, BX17_REF).exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    @DisplayName("Responde 400 si la referencia supera el largo permitido, sin llamar al caso de uso")
    void debeResponder400SiLaReferenciaEsMuyLarga() {
        webTestClient.post().uri(RECONCILE_URI, TOO_LONG_REF).exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("La referencia externa no es válida");

        verifyNoInteractions(reconcilePayment);
    }

    @Test
    @DisplayName("Responde 500 sin detalles internos ante un error inesperado")
    void debeOcultarLosDetallesDeUnErrorInesperado() {
        when(reconcilePayment.execute(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("ORA-12345 detalle interno")));

        webTestClient.post().uri(RECONCILE_URI, BX17_REF).exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.message").isEqualTo("No fue posible conciliar el pago; revise los logs del servicio");
    }
}
