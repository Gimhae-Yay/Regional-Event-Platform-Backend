package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.platformadmin.service.DeactivateAdminAccountResult;

public record DeactivateAdminAccountResponse(
    String userId,
    String platformAdminAssignmentId,
    String grade,
    String status,
    Instant inactivatedAt
) {

    public static DeactivateAdminAccountResponse from(DeactivateAdminAccountResult result) {
        return new DeactivateAdminAccountResponse(
            result.userId().toString(),
            result.platformAdminAssignmentId().toString(),
            result.grade(),
            result.status(),
            result.inactivatedAt()
        );
    }
}
