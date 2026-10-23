package com.pagoclaro.reconciliation.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "reconciliation")
public record ReconciliationProperties(int lockWaitSeconds) {
}
