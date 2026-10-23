package com.pagoclaro.reconciliation.infrastructure.config;

import com.pagoclaro.reconciliation.domain.policy.ReconciliationPolicy;
import com.pagoclaro.reconciliation.domain.port.LedgerEntryRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.PaymentRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.ProviderEventRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.SupportAuditRepositoryPort;
import com.pagoclaro.reconciliation.domain.port.TransactionalPort;
import com.pagoclaro.reconciliation.domain.usecase.ReconcilePaymentUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public ReconciliationPolicy reconciliationPolicy() {
        return new ReconciliationPolicy();
    }

    @Bean
    public ReconcilePaymentUseCase reconcilePaymentUseCase(PaymentRepositoryPort payments,
                                                           ProviderEventRepositoryPort providerEvents,
                                                           LedgerEntryRepositoryPort ledgerEntries,
                                                           SupportAuditRepositoryPort audits,
                                                           TransactionalPort transactional,
                                                           ReconciliationPolicy reconciliationPolicy) {
        return new ReconcilePaymentUseCase(
                payments, providerEvents, ledgerEntries, audits, transactional, reconciliationPolicy);
    }
}
