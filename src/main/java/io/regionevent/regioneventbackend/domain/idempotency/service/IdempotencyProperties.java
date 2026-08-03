package io.regionevent.regioneventbackend.domain.idempotency.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "idempotency")
public record IdempotencyProperties(
    @DefaultValue("PT24H") Duration retention,
    @DefaultValue("PT1H") Duration cleanupFixedDelay,
    @DefaultValue("PT1H") Duration cleanupInitialDelay,
    @DefaultValue("3") int lockWaitTimeoutSeconds
) {

    public IdempotencyProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("retention must be positive");
        }
        if (cleanupFixedDelay == null || cleanupFixedDelay.isNegative() || cleanupFixedDelay.isZero()) {
            throw new IllegalArgumentException("cleanupFixedDelay must be positive");
        }
        if (cleanupInitialDelay == null || cleanupInitialDelay.isNegative()) {
            throw new IllegalArgumentException("cleanupInitialDelay must not be negative");
        }
        if (lockWaitTimeoutSeconds < 1) {
            throw new IllegalArgumentException("lockWaitTimeoutSeconds must be positive");
        }
    }
}
