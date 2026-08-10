package io.regionevent.regioneventbackend.domain.platformadmin.dto;

import java.time.Instant;

import io.regionevent.regioneventbackend.domain.platformadmin.service.CreateAdminAccountResult;

public record CreateAdminAccountResponse(
    String userId,
    String platformAdminAssignmentId,
    String grade,
    String status,
    Instant createdAt
) {

    public static CreateAdminAccountResponse from(CreateAdminAccountResult result) {
        return new CreateAdminAccountResponse(
            result.userId().toString(),
            result.platformAdminAssignmentId().toString(),
            result.grade(),
            result.status(),
            result.createdAt()
        );
    }
}
