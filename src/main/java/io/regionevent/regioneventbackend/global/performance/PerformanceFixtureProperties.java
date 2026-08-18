package io.regionevent.regioneventbackend.global.performance;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "performance.fixture")
public record PerformanceFixtureProperties(
    boolean enabled,
    String resetToken
) {
}
