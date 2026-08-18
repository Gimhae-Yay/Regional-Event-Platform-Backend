package io.regionevent.regioneventbackend.global.performance;

import java.time.Instant;

public record PerformanceFixtureResetResult(
    String fixtureVersion,
    Instant completedAt
) {
}
