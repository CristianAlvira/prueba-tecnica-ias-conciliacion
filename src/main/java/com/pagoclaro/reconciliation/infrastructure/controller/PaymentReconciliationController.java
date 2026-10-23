package com.pagoclaro.reconciliation.infrastructure.controller;

import com.pagoclaro.reconciliation.domain.usecase.ReconcilePaymentUseCase;
import com.pagoclaro.reconciliation.infrastructure.dto.ReconciliationResponseDTO;
import com.pagoclaro.reconciliation.infrastructure.mapper.ResponseMapper;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/support/payments")
public class PaymentReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationController.class);
    private static final int MAX_EXTERNAL_REF_LENGTH = 40;

    private final ReconcilePaymentUseCase reconcilePayment;
    private final ResponseMapper responseMapper;

    public PaymentReconciliationController(ReconcilePaymentUseCase reconcilePayment, ResponseMapper responseMapper) {
        this.reconcilePayment = reconcilePayment;
        this.responseMapper = responseMapper;
    }

    @PostMapping("/{externalRef}/reconcile")
    public Mono<ReconciliationResponseDTO> reconcile(
            @PathVariable @Size(max = MAX_EXTERNAL_REF_LENGTH) String externalRef) {
        return reconcilePayment.execute(externalRef)
                .map(responseMapper::toResponse)
                .doOnNext(response -> log.info("paymentRef={} outcome={} actions={} reason={}",
                        response.externalRef(), response.outcome(), response.actions(), response.reason()));
    }
}
