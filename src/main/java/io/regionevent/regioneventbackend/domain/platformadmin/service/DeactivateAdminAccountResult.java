package io.regionevent.regioneventbackend.domain.platformadmin.service;

import java.time.Instant;

public record DeactivateAdminAccountResult(
    Long userId,
    Long platformAdminAssignmentId,
    String grade,
    String status,
    Instant inactivatedAt
) {
}
